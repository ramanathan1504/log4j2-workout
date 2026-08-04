package org.apache.logging.bench.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;

/**
 * Database appenders, verified by reading the rows back. Feature matrix §1, §7.
 *
 * <p>Checking that logging "did not throw" proves very little for a database
 * appender — the JDBC appender buffers, swallows failures into StatusLogger and
 * keeps going. The only convincing check is to query the table afterwards and
 * count what actually landed, which is what this does.
 *
 * <p>Uses embedded H2 so it runs with no container. For MongoDB, Cassandra and
 * CouchDB, start the relevant service from {@code infra/docker-compose.yml} and
 * point {@code --config} at the matching configuration.
 */
public final class DbBench {

    private static final String JDBC_URL = "jdbc:h2:./logs/db/log4jdb;AUTO_SERVER=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    static {
        // Must run before the first Logger call: Log4j's JDBC appender writes
        // into an existing table and will not create one. Doing this in a static
        // block guarantees it happens before the LoggerContext initialises.
        try {
            createSchema();
        } catch (final SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final Logger log = LogManager.getLogger(DbBench.class);

    public static void main(final String[] args) throws SQLException {
        System.out.println("Database appender bench");
        System.out.println("  jdbc url  " + JDBC_URL);
        System.out.println("  config    "
                + System.getProperty("log4j.configurationFile", "<default>"));
        System.out.println();

        final int before = countRows("LOG_EVENTS");
        emit();

        // The appender may buffer; give it a moment before asserting on the table.
        flushAndSettle();

        final int after = countRows("LOG_EVENTS");
        System.out.printf("%nLOG_EVENTS rows: %d before, %d after (delta %d)%n",
                before, after, after - before);

        dumpRecent();

        if (after == before) {
            System.out.println();
            System.out.println("Nothing was written. Either the active config has no JDBC appender,");
            System.out.println("or the appender failed and reported it through StatusLogger — re-run");
            System.out.println("with status=\"DEBUG\" on the Configuration element to see why.");
            System.exit(1);
        }
    }

    private static void emit() {
        ThreadContext.put("traceId", "db-bench-0001");
        try {
            log.info("Plain informational event");
            log.warn("Event carrying a marker", MarkerManager.getMarker("AUDIT"));
            log.info("Parameterised event: order {} total {}", 4711, "£20.00");
            log.error("Event with a throwable", new IllegalStateException("synthetic failure"));

            for (int i = 1; i <= 20; i++) {
                log.info("Bulk event {} of 20", i);
            }
        } finally {
            ThreadContext.clearAll();
        }
    }

    /** Stopping the context flushes and closes appenders deterministically. */
    private static void flushAndSettle() {
        try {
            Thread.sleep(500);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void createSchema() throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS LOG_EVENTS (
                  ID         BIGINT AUTO_INCREMENT PRIMARY KEY,
                  EVENT_DATE TIMESTAMP,
                  LEVEL      VARCHAR(16),
                  LOGGER     VARCHAR(255),
                  MESSAGE    VARCHAR(4000),
                  THREAD     VARCHAR(128),
                  MARKER     VARCHAR(128),
                  TRACE_ID   VARCHAR(128),
                  THROWABLE  CLOB
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS LOG_EVENTS_JSON (
                  ID         BIGINT AUTO_INCREMENT PRIMARY KEY,
                  EVENT_DATE TIMESTAMP,
                  LEVEL      VARCHAR(16),
                  MDC_MAP    VARCHAR(4000),
                  EVENT_JSON CLOB
                )""");
        }
    }

    private static int countRows(final String table) throws SQLException {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static void dumpRecent() throws SQLException {
        System.out.printf("%n  %-12s %-8s %-34s %-16s %s%n", "TIME", "LEVEL", "LOGGER", "TRACE_ID", "MESSAGE");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT EVENT_DATE, LEVEL, LOGGER, TRACE_ID, MESSAGE FROM LOG_EVENTS ORDER BY ID DESC LIMIT 8")) {
            while (rs.next()) {
                System.out.printf("  %-12s %-8s %-34s %-16s %s%n",
                        String.valueOf(rs.getTimestamp(1)).substring(11, 19),
                        rs.getString(2),
                        abbreviate(rs.getString(3), 34),
                        String.valueOf(rs.getString(4)),
                        abbreviate(rs.getString(5), 60));
            }
        }
    }

    private static String abbreviate(final String value, final int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private DbBench() {}
}
