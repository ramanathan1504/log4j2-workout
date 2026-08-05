package org.apache.logging.bench.db;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * The NoSQL appenders — MongoDB, CouchDB and Cassandra — verified by counting
 * documents and rows. Feature matrix §1.
 *
 * <pre>
 *   docker compose -f infra/docker-compose.yml up -d mongodb couchdb cassandra
 *   ./bench run nosql --config xml/appender-nosql
 * </pre>
 *
 * <p>Each of the three needs something created before Log4j starts, and each
 * fails differently when it is missing:
 *
 * <ul>
 *   <li><strong>MongoDB</strong> creates the collection on first write, so
 *       nothing is required — the only one of the three that needs no setup.</li>
 *   <li><strong>CouchDB</strong> does not create databases. The appender gets a
 *       404 naming the database, which reads like a connection problem.</li>
 *   <li><strong>Cassandra</strong> needs a keyspace AND a table with columns
 *       matching the ColumnMappings, because CQL is typed. A missing table
 *       fails the appender at construction, so it is absent for the whole run
 *       rather than failing per event.</li>
 * </ul>
 *
 * <p>Cassandra also takes 60–90 seconds after its container starts before it
 * accepts connections — far longer than anything else here, and long enough
 * that a run started too eagerly looks like a configuration error.
 */
public final class NoSqlBench {

    private static final String MONGO_URI = "mongodb://localhost:27017";
    private static final String COUCH_BASE = "http://localhost:5984";
    private static final String COUCH_DB = "log4j";
    private static final String COUCH_AUTH = Base64.getEncoder()
            .encodeToString("log4j:log4j".getBytes(StandardCharsets.UTF_8));
    private static final String CASSANDRA_HOST = "localhost";
    private static final int CASSANDRA_PORT = 9042;

    /** Lazy: the schema below must exist before Log4j configures. */
    private static Logger logger() {
        return LogManager.getLogger(NoSqlBench.class);
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("NoSQL appender bench");
        System.out.println("  config     "
                + System.getProperty("log4j.configurationFile", "<default>"));
        System.out.println();

        final boolean couch = prepareCouchDb();
        // Opt-in, because the DataStax 3.x driver retries rather than failing and
        // its three bounded calls together still overran the harness timeout —
        // killing the run before MongoDB and CouchDB were ever written to. One
        // unusable appender was starving the two that work. Investigate it with
        // -Dbench.cassandra=true; leave it off to verify the other two reliably.
        final boolean cassandraEnabled = Boolean.getBoolean("bench.cassandra");
        // Bounded, because the driver can stall indefinitely rather than fail —
        // see prepareCassandra. Without this the whole app hangs on one appender.
        final boolean cassandra = cassandraEnabled
                && withTimeout("cassandra setup", 20, NoSqlBench::prepareCassandra, false);
        System.out.printf("  mongodb    %s  (collection created on first write)%n",
                reachable("localhost", 27017));
        System.out.printf("  couchdb    %s  database '%s' %s%n",
                reachable("localhost", 5984), COUCH_DB, couch ? "ready" : "NOT ready");
        if (cassandraEnabled) {
            System.out.printf("  cassandra  %s  keyspace/table %s%n",
                    reachable(CASSANDRA_HOST, CASSANDRA_PORT), cassandra ? "ready" : "NOT ready");
        } else {
            System.out.println("  cassandra  SKIPPED  (enable with -Dbench.cassandra=true)");
        }
        System.out.println();

        final long mongoBefore = mongoCount();
        final long couchBefore = couchCount();
        final long cassandraBefore = cassandraEnabled
                ? withTimeout("cassandra count", 15, NoSqlBench::cassandraCount, 0L)
                : 0L;

        emit();
        LogManager.shutdown();
        Thread.sleep(2000);

        System.out.println();
        System.out.println("──── what was stored");
        report("MongoDB  (log4j.events)", mongoBefore, mongoCount());
        report("CouchDB  (" + COUCH_DB + ")", couchBefore, couchCount());
        if (cassandraEnabled) {
            report("Cassandra(log4j.log_events)", cassandraBefore,
                    withTimeout("cassandra count", 15, NoSqlBench::cassandraCount, 0L));
        } else {
            System.out.println("  Cassandra(log4j.log_events)  skipped");
        }

        System.out.println();
        System.out.println("A count that did not move means the appender failed and reported it");
        System.out.println("through StatusLogger — the JVM still exits 0 either way.");
    }

    /**
     * Runs work on a daemon thread and gives up after {@code seconds}.
     *
     * <p>Needed only for Cassandra. The DataStax 3.x driver does not fail when
     * it cannot negotiate with a newer Cassandra — it retries its control
     * connection indefinitely, so a single unusable appender otherwise stalls
     * the entire run with no error at all. Bounding it turns that into a
     * reported "NOT ready" and lets the other two appenders be verified.
     */
    private static <T> T withTimeout(final String what, final int seconds,
            final java.util.concurrent.Callable<T> work, final T fallback) {
        final var executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "bounded-" + what);
            t.setDaemon(true);
            return t;
        });
        try {
            return executor.submit(work).get(seconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (final java.util.concurrent.TimeoutException e) {
            System.out.printf("  (%s gave up after %ds — the driver was still retrying)%n",
                    what, seconds);
            return fallback;
        } catch (final Exception e) {
            return fallback;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void report(final String what, final long before, final long after) {
        System.out.printf("  %-28s %d before, %d after (delta %d)%s%n",
                what, before, after, after - before,
                after > before ? "" : "   ← NOTHING STORED");
    }

    private static void emit() {
        final Logger log = logger();
        ThreadContext.put("traceId", "nosql-bench-0001");
        try {
            log.info("A plain event");
            log.info("A parameterised event: user {} order {}", "alice", Integer.valueOf(4711));
            log.warn("A warning");
            log.error("An event with a throwable",
                    new IllegalStateException("synthetic NoSQL failure"));
        } finally {
            ThreadContext.clearAll();
        }
    }

    // ── MongoDB ─────────────────────────────────────────────────────────────

    /** Counts through the driver the appender itself uses. */
    private static long mongoCount() {
        try (var client = com.mongodb.client.MongoClients.create(MONGO_URI)) {
            return client.getDatabase("log4j").getCollection("events").countDocuments();
        } catch (final Exception e) {
            return 0L;
        }
    }

    // ── CouchDB ─────────────────────────────────────────────────────────────

    /** PUTs the database; 201 creates it, 412 means it already existed. */
    private static boolean prepareCouchDb() {
        try {
            final HttpResponse<String> response = couch("PUT", "/" + COUCH_DB, "");
            return response.statusCode() == 201 || response.statusCode() == 412;
        } catch (final Exception e) {
            return false;
        }
    }

    private static long couchCount() {
        try {
            final HttpResponse<String> response = couch("GET", "/" + COUCH_DB, null);
            if (response.statusCode() != 200) {
                return 0L;
            }
            final var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(response.body()).get("doc_count");
            return node == null ? 0L : node.asLong();
        } catch (final Exception e) {
            return 0L;
        }
    }

    private static HttpResponse<String> couch(final String method, final String path, final String body)
            throws Exception {
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build();
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(COUCH_BASE + path))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Basic " + COUCH_AUTH);
        if (body == null) {
            request.GET();
        } else {
            request.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ── Cassandra ───────────────────────────────────────────────────────────

    /**
     * Creates the keyspace and table.
     *
     * <p>The columns must match the ColumnMappings in the configuration exactly,
     * including types: the appender writes a prepared INSERT built from them, so
     * a type mismatch is a CQL error per event rather than a startup failure.
     */
    private static boolean prepareCassandra() {
        try (var cluster = cassandraCluster(); var session = cluster.connect()) {
            session.execute("CREATE KEYSPACE IF NOT EXISTS log4j WITH replication = "
                    + "{'class':'SimpleStrategy','replication_factor':1}");
        } catch (final Exception e) {
            return false;
        }
        try (var cluster = cassandraCluster(); var session = cluster.connect("log4j")) {
            session.execute("CREATE TABLE IF NOT EXISTS log_events ("
                    + "id timeuuid PRIMARY KEY, "
                    + "timeid timeuuid, "
                    + "message text, "
                    + "level text, "
                    + "logger text, "
                    + "marker text, "
                    + "thread text, "
                    + "event_date timestamp, "
                    + "mdc map<text, text>)");
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    private static long cassandraCount() {
        try (var cluster = cassandraCluster(); var session = cluster.connect("log4j")) {
            final var row = session.execute("SELECT COUNT(*) FROM log_events").one();
            return row == null ? 0L : row.getLong(0);
        } catch (final Exception e) {
            return 0L;
        }
    }

    /**
     * The DataStax driver 3.x API — {@code Cluster}, not {@code CqlSession}.
     *
     * <p>log4j-cassandra is built against cassandra-driver-core 3.x, so the
     * verification here has to use the same generation. The 4.x OSS driver
     * (`com.datastax.oss.driver.api.core.CqlSession`) is a different artifact
     * with a different package, and mixing them compiles only if both are on
     * the classpath — at which point which one the appender picks depends on
     * ordering.
     */
    private static com.datastax.driver.core.Cluster cassandraCluster() {
        // Short, explicit timeouts. The driver's defaults retry a control
        // connection for minutes, so a Cassandra it cannot negotiate with
        // stalls the whole run rather than failing — which is exactly what
        // happens against Cassandra 5, whose native protocol v5 this driver
        // generation does not speak.
        final var socketOptions = new com.datastax.driver.core.SocketOptions()
                .setConnectTimeoutMillis(4000)
                .setReadTimeoutMillis(4000);
        return com.datastax.driver.core.Cluster.builder()
                .addContactPoint(CASSANDRA_HOST)
                .withPort(CASSANDRA_PORT)
                .withSocketOptions(socketOptions)
                .withNoCompact()
                .build();
    }

    private static String reachable(final String host, final int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 2000);
            return "up  ";
        } catch (final Exception e) {
            return "DOWN";
        }
    }

    private NoSqlBench() {}
}
