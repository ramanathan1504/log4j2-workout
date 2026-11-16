import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import org.apache.logging.log4j.LogManager;

public final class Repro {

    private static final int EVENTS = 3;

    private static final class TlsSink {
        final SSLServerSocket server;
        final List<String> lines = new CopyOnWriteArrayList<>();
        final List<String> errors = new CopyOnWriteArrayList<>();

        TlsSink(final SSLContext context) throws Exception {
            server = (SSLServerSocket)
                    context.getServerSocketFactory().createServerSocket(0, 5, InetAddress.getLoopbackAddress());
            final Thread thread = new Thread(this::serve, "tls-sink-" + server.getLocalPort());
            thread.setDaemon(true);
            thread.start();
        }

        void serve() {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    final BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = in.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (final Exception error) {
                    if (!server.isClosed()) {
                        errors.add(error.getClass().getSimpleName() + ": " + error.getMessage());
                    }
                }
            }
        }
    }

    public static void main(final String[] args) throws Exception {
        final String tlsDir = System.getProperty("tlsdir");
        final SSLContext serverContext = serverContext(tlsDir + "/server.p12");
        final Map<String, TlsSink> sinks = new LinkedHashMap<>();
        for (final String name : new String[] {"missingTrustStore", "missingKeyStore", "validTrustStore"}) {
            final TlsSink sink = new TlsSink(serverContext);
            System.setProperty("port." + name, Integer.toString(sink.server.getLocalPort()));
            sinks.put(name, sink);
        }
        for (final String name : sinks.keySet()) {
            for (int i = 1; i <= EVENTS; i++) {
                LogManager.getLogger(name).info("{} secret event {}", name, i);
            }
        }
        LogManager.shutdown();
        TimeUnit.MILLISECONDS.sleep(500);
        for (final Map.Entry<String, TlsSink> entry : sinks.entrySet()) {
            final String name = entry.getKey();
            final TlsSink sink = entry.getValue();
            sink.server.close();
            final int received = sink.lines.size();
            final boolean valid = name.equals("validTrustStore");
            final boolean ok = valid ? received == EVENTS : received == 0;
            System.out.println((ok ? "PASS " : "FAIL ") + name + " expected-received=" + (valid ? EVENTS : 0)
                    + " received=" + received + " serverErrors=" + summarize(sink.errors));
        }
    }

    private static String summarize(final List<String> errors) {
        final List<String> distinct = new ArrayList<>();
        for (final String error : errors) {
            if (!distinct.contains(error)) {
                distinct.add(error);
            }
        }
        return distinct.toString();
    }

    private static SSLContext serverContext(final String keyStorePath) throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(keyStorePath)) {
            keyStore.load(in, "changeit".toCharArray());
        }
        final KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "changeit".toCharArray());
        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagerFactory.getKeyManagers(), null, null);
        return context;
    }
}
