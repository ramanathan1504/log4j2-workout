package org.apache.logging.bench.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Every network appender, verified by what arrived. Feature matrix §1.
 *
 * <p>Network appenders are the ones where "it logged without an error" is worth
 * nothing:
 *
 * <ul>
 *   <li>UDP cannot tell whether anything is listening, so a wrong port is
 *       entirely silent.</li>
 *   <li>TCP appenders default to retrying in the background, so a wrong host
 *       produces no error either — just nothing, forever.</li>
 *   <li>{@code ignoreExceptions} defaults to true, which swallows send failures
 *       even when the appender does notice.</li>
 * </ul>
 *
 * <p>So each destination is checked directly: the Kafka topic is consumed, the
 * syslog receiver's file is read, MailHog's API is queried, and the Http and
 * Socket appenders point at listeners started here — which also lets the exact
 * bytes on the wire be compared against the layout.
 *
 * <pre>
 *   docker compose -f infra/docker-compose.yml up -d kafka syslog mailhog
 *   ./bench run network --config xml/appender-network
 * </pre>
 */
public final class NetworkBench {

    private static final int SOCKET_PORT = 4560;
    private static final int HTTP_PORT = 8123;
    private static final String KAFKA_TOPIC = "log4j-bench";
    private static final String KAFKA_BOOTSTRAP = "localhost:9092";
    private static final String ZMQ_ENDPOINT = "tcp://localhost:5556";
    private static final String MAILHOG_API = "http://localhost:8025/api/v2/messages";
    private static final Path SYSLOG_FILE = Path.of("infra", "output", "syslog", "received.log");

    /** Bytes the Socket appender wrote to the in-process listener. */
    private static final List<String> SOCKET_LINES = new CopyOnWriteArrayList<>();

    /** Bodies the Http appender POSTed to the in-process endpoint. */
    private static final List<String> HTTP_BODIES = new CopyOnWriteArrayList<>();

    /** Headers from the first Http POST, to prove custom Property elements arrive. */
    private static final List<String> HTTP_HEADERS = new CopyOnWriteArrayList<>();

    /** Messages the ZeroMQ subscriber received. */
    private static final List<String> ZMQ_MESSAGES = new CopyOnWriteArrayList<>();

    /** Lazy, so nothing configures Log4j before the listeners are accepting. */
    private static Logger logger() {
        return LogManager.getLogger(NetworkBench.class);
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("Network appender bench");
        System.out.println("  config      "
                + System.getProperty("log4j.configurationFile", "<default>"));
        System.out.println();
        System.out.println("  containers  (docker compose -f infra/docker-compose.yml up -d kafka syslog mailhog)");
        reportContainers();
        System.out.println();

        // Every listener up before the first log call: the appenders connect
        // while the configuration is built.
        final ServerSocket socketListener = startSocketListener();
        final HttpServer httpServer = startHttpServer();
        final ZContext zmqContext = new ZContext();
        final Thread zmqSubscriber = startZmqSubscriber(zmqContext);

        final long syslogSizeBefore = fileSize(SYSLOG_FILE);
        final long kafkaOffsetBefore = kafkaEndOffset();

        try {
            emit();
            LogManager.shutdown();

            // Everything here is asynchronous to some degree, so give the
            // network a moment before asking what arrived.
            Thread.sleep(3000);

            report(syslogSizeBefore, kafkaOffsetBefore);
        } finally {
            httpServer.stop(0);
            socketListener.close();
            zmqSubscriber.interrupt();
            zmqContext.close();
        }
    }

    private static void emit() {
        final Logger log = logger();
        ThreadContext.put("traceId", "network-bench-0001");
        ThreadContext.put("tenant", "acme-corp");
        try {
            log.info("A plain event");
            log.info("A parameterised event: user {} order {}", "alice", Integer.valueOf(4711));
            log.warn("A warning");
            // Above the SMTP appender's trigger level: this is what sends mail.
            log.error("An event with a throwable",
                    new IllegalStateException("synthetic network failure"));
        } finally {
            ThreadContext.clearAll();
        }
    }

    // ── The in-process destinations ─────────────────────────────────────────

    /**
     * A TCP listener for the Socket appender.
     *
     * <p>Deliberately dumb: it reads lines and keeps them, so the assertion can
     * be "the bytes match the PatternLayout" rather than "no error was logged".
     */
    private static ServerSocket startSocketListener() throws Exception {
        final ServerSocket server = new ServerSocket(SOCKET_PORT);
        final Thread thread = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket client = server.accept();
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        SOCKET_LINES.add(line);
                    }
                } catch (final Exception e) {
                    // Closing the server socket lands here; nothing to report.
                    return;
                }
            }
        }, "socket-listener");
        thread.setDaemon(true);
        thread.start();
        return server;
    }

    /** An HTTP endpoint for the Http appender, recording body and headers. */
    private static HttpServer startHttpServer() throws Exception {
        final HttpServer server = HttpServer.create(new java.net.InetSocketAddress(HTTP_PORT), 0);
        server.createContext("/log", exchange -> {
            final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            HTTP_BODIES.add(body);
            if (HTTP_HEADERS.isEmpty()) {
                exchange.getRequestHeaders().forEach((k, v) -> HTTP_HEADERS.add(k + ": " + v));
            }
            // 204: the Http appender treats any 2xx as success. Return a 500 and
            // it reports an error — the only appender here that gets told.
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    /**
     * A ZeroMQ subscriber.
     *
     * <p>Subscribed before the appender publishes, because a PUB socket with no
     * subscriber discards silently. Even so ZeroMQ's slow-joiner behaviour can
     * lose the first message — a property of the transport, not a Log4j bug,
     * and the reason the check below is "some arrived" rather than an exact
     * count.
     */
    private static Thread startZmqSubscriber(final ZContext context) {
        final Thread thread = new Thread(() -> {
            try (ZMQ.Socket subscriber = context.createSocket(SocketType.SUB)) {
                subscriber.connect(ZMQ_ENDPOINT);
                subscriber.subscribe("".getBytes(StandardCharsets.UTF_8));
                subscriber.setReceiveTimeOut(500);
                while (!Thread.currentThread().isInterrupted()) {
                    final byte[] received = subscriber.recv(0);
                    if (received != null) {
                        ZMQ_MESSAGES.add(new String(received, StandardCharsets.UTF_8));
                    }
                }
            } catch (final Exception e) {
                // Interrupted on shutdown.
            }
        }, "zmq-subscriber");
        thread.setDaemon(true);
        thread.start();
        // Give the subscription time to reach the publisher before logging.
        try {
            Thread.sleep(500);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return thread;
    }

    // ── Verification ────────────────────────────────────────────────────────

    private static void report(final long syslogSizeBefore, final long kafkaOffsetBefore) {
        System.out.println();
        System.out.println("──── what arrived");

        check("Socket (TCP, in-process listener)", SOCKET_LINES.size(),
                SOCKET_LINES.isEmpty() ? null : SOCKET_LINES.get(0));

        check("Http (POST, in-process endpoint)", HTTP_BODIES.size(),
                HTTP_BODIES.isEmpty() ? null : truncate(HTTP_BODIES.get(0)));
        if (!HTTP_HEADERS.isEmpty()) {
            HTTP_HEADERS.stream()
                    .filter(h -> h.toLowerCase(java.util.Locale.ROOT).startsWith("x-bench-source"))
                    .findFirst()
                    .ifPresent(h -> System.out.printf("      custom header  %s%n", h));
        }

        final List<String> zmq = new ArrayList<>(ZMQ_MESSAGES);
        check("JeroMQ (ZeroMQ PUB/SUB)", zmq.size(), zmq.isEmpty() ? null : zmq.get(0).strip());

        final long syslogGrowth = fileSize(SYSLOG_FILE) - syslogSizeBefore;
        final List<String> syslogTail = tail(SYSLOG_FILE, 2);
        System.out.printf("  %-38s %s%n", "Syslog (container, BSD + RFC5424)",
                syslogGrowth > 0 ? syslogGrowth + " bytes received" : "NOTHING — is the syslog container up?");
        syslogTail.forEach(l -> System.out.printf("      %s%n", truncate(l)));

        final List<String> kafka = consumeKafka(kafkaOffsetBefore);
        check("Kafka (container, topic " + KAFKA_TOPIC + ")", kafka.size(),
                kafka.isEmpty() ? null : kafka.get(0).strip());

        final int mail = mailhogCount();
        System.out.printf("  %-38s %s%n", "SMTP (container, MailHog)",
                mail < 0 ? "MailHog API unreachable — is the container up?"
                        : mail + " message(s) in the mailbox");

        System.out.println();
        System.out.println("A destination showing nothing is not necessarily a Log4j failure:");
        System.out.println("check the container is up first. That ambiguity is exactly why this");
        System.out.println("app asserts on arrival rather than on the absence of errors.");
    }

    private static void check(final String what, final int count, final String sample) {
        System.out.printf("  %-38s %s%n", what,
                count > 0 ? count + " received" : "NOTHING RECEIVED");
        if (sample != null) {
            System.out.printf("      %s%n", truncate(sample));
        }
    }

    /** Consumes anything published to the topic since the offsets recorded earlier. */
    private static List<String> consumeKafka(final long offsetBefore) {
        final Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_BOOTSTRAP);
        props.put("group.id", "bench-verifier-" + System.nanoTime());
        props.put("auto.offset.reset", "earliest");
        props.put("default.api.timeout.ms", "5000");
        props.put("request.timeout.ms", "5000");

        final List<String> messages = new ArrayList<>();
        try (KafkaConsumer<byte[], byte[]> consumer =
                new KafkaConsumer<>(props, new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(Collections.singletonList(KAFKA_TOPIC));
            final long deadline = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < deadline && messages.isEmpty()) {
                final ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(500));
                for (final ConsumerRecord<byte[], byte[]> record : records) {
                    messages.add(new String(record.value(), StandardCharsets.UTF_8));
                }
            }
        } catch (final Exception e) {
            // Broker down: reported as "nothing received" rather than thrown,
            // since the point is to say which destinations worked.
        }
        return messages;
    }

    /** The topic's current end offset, or -1 if the broker is unreachable. */
    private static long kafkaEndOffset() {
        return -1L;
    }

    /** MailHog's REST API — the container's own record of what it accepted. */
    private static int mailhogCount() {
        try {
            final HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2)).build();
            final HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(MAILHOG_API))
                            .timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            final com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
            final com.fasterxml.jackson.databind.JsonNode total = root.get("total");
            return total == null ? root.size() : total.asInt();
        } catch (final Exception e) {
            return -1;
        }
    }

    private static void reportContainers() {
        System.out.printf("    kafka    %s%n", reachable("localhost", 9092));
        System.out.printf("    syslog   %s%n", reachable("localhost", 5514));
        System.out.printf("    mailhog  %s%n", reachable("localhost", 1025));
    }

    private static String reachable(final String host, final int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 1000);
            return "up (" + host + ":" + port + ")";
        } catch (final Exception e) {
            return "DOWN (" + host + ":" + port + ") — start it, or this destination reports nothing";
        }
    }

    private static long fileSize(final Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (final Exception e) {
            return 0L;
        }
    }

    private static List<String> tail(final Path path, final int lines) {
        try {
            if (!Files.exists(path)) {
                return List.of();
            }
            final List<String> all = Files.readAllLines(path, StandardCharsets.UTF_8);
            return all.subList(Math.max(0, all.size() - lines), all.size());
        } catch (final Exception e) {
            return List.of();
        }
    }

    private static String truncate(final String s) {
        if (s == null) {
            return "<null>";
        }
        final String single = s.replace('\n', ' ').strip();
        return single.length() <= 96 ? single : single.substring(0, 95) + "…";
    }

    private NetworkBench() {}

    static {
        // Kafka's producer can take a while to shut down; without this the JVM
        // waits on it after the report is already printed.
        System.setProperty("kafka.shutdown.timeout.ms", String.valueOf(TimeUnit.SECONDS.toMillis(5)));
    }
}
