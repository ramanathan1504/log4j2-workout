package org.apache.logging.bench.jndi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.naming.InitialContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * The JDBC appender's {@code DataSource} connection source, resolved through
 * JNDI. Feature matrix §1, §7.
 *
 * <p>The other two connection sources name their target directly:
 * {@code DriverManager} takes a URL, {@code PoolingDriver} takes a pool name.
 * This one takes only a JNDI name, so the database is chosen entirely outside
 * Log4j — by whatever bound that name. In a container that is the point; in a
 * standalone application it means a config that cannot work without something
 * else having run first.
 *
 * <p>Where the module lives differs by line, and this app runs on both: 2.x has
 * {@code DataSourceConnectionSource} inside log4j-core, 3.x split it into
 * log4j-jdbc-jndi. The configuration is identical either way.
 *
 * <p>Verified by reading rows back, like the db and jpa benches — a JDBC
 * appender that silently fails leaves the JVM exiting 0.
 *
 * <pre>
 *   ./bench run jdbc-jndi --config xml/appender-jdbc-jndi
 * </pre>
 */
public final class JdbcJndiBench {

    private static final String URL = "jdbc:h2:./logs/jndi/log4jndi;AUTO_SERVER=TRUE";

    /** The name the configuration looks up. Nothing parses it — it is a map key. */
    private static final String JNDI_NAME = "java:comp/env/jdbc/benchDataSource";

    /**
     * Deliberately NOT a static field.
     *
     * <p>The conventional {@code private static final Logger log =
     * LogManager.getLogger(...)} initialises when the class loads — before
     * main() runs — which configures Log4j, which builds the appender, which
     * performs the JNDI lookup. The binding this app creates in main() would
     * then be too late, and the symptom is
     *
     *   ERROR Nothing bound at java:comp/env/jdbc/benchDataSource (bound: [])
     *
     * pointing at the name rather than at the ordering. With a JNDI-sourced
     * appender the binding has to exist before the FIRST logger acquired
     * anywhere in the JVM, which in a container the container guarantees and in
     * a standalone application nothing does.
     */
    private static Logger logger() {
        return LogManager.getLogger(JdbcJndiBench.class);
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("JDBC-via-JNDI appender bench");
        System.out.println("  jdbc url     " + URL);
        System.out.println("  jndi name    " + JNDI_NAME);
        System.out.println("  factory      "
                + System.getProperty("java.naming.factory.initial", "<unset — lookups will fail>"));
        System.out.println("  enableJndiJdbc  "
                + System.getProperty("log4j2.enableJndiJdbc", "<unset — the appender will refuse>"));
        System.out.println("  config       "
                + System.getProperty("log4j.configurationFile", "<default>"));

        // Bind BEFORE anything acquires a logger — see logger() below.
        bindDataSource();
        createSchema();

        System.out.println("  bindings     " + BenchInitialContextFactory.bindings().keySet());
        System.out.println();

        final long before = countRows();
        emit();
        LogManager.shutdown();
        final long after = countRows();

        System.out.printf("%nLOG_EVENTS rows: %d before, %d after (delta %d)%n",
                before, after, after - before);

        if (after > before) {
            dump();
        } else {
            System.out.println();
            System.out.println("Nothing was written. Check, in this order: that");
            System.out.println("log4j2.enableJndiJdbc is set, that the config's jndiName matches");
            System.out.println("the binding above, and the status logger for an appender error —");
            System.out.println("all three fail silently as far as the application is concerned.");
        }
    }

    /**
     * Binds an H2 DataSource under the name the configuration references.
     *
     * <p>{@code JdbcDataSource} is H2's own implementation, so nothing here
     * needs a pool or a container. The binding lives in this JVM's map and is
     * unreachable from anywhere else.
     */
    private static void bindDataSource() throws Exception {
        final org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL(URL);
        dataSource.setUser("sa");
        dataSource.setPassword("");

        // Through InitialContext rather than the static bind(), so the provider
        // is exercised the way Log4j will exercise it.
        new InitialContext().bind(JNDI_NAME, dataSource);
    }

    /** The JDBC appender does not create tables — same as the db bench. */
    private static void createSchema() throws Exception {
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("logs", "jndi"));
        try (Connection c = DriverManager.getConnection(URL, "sa", "");
                Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS LOG_EVENTS ("
                    + "ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                    + "EVENT_DATE TIMESTAMP, "
                    + "LEVEL VARCHAR(10), "
                    + "LOGGER VARCHAR(255), "
                    + "MESSAGE VARCHAR(4000), "
                    + "THREAD VARCHAR(100), "
                    + "TRACE_ID VARCHAR(64), "
                    + "THROWABLE VARCHAR(8000))");
        }
    }

    private static void emit() {
        final Logger log = logger();
        ThreadContext.put("traceId", "jndi-bench-0001");
        try {
            log.info("A plain event");
            log.info("A parameterised event: user {} order {}", "alice", Integer.valueOf(4711));
            log.warn("A warning");
            log.error("An event with a throwable",
                    new IllegalStateException("synthetic JNDI-sourced failure"));
            for (int i = 1; i <= 10; i++) {
                log.info("Bulk event {} of 10", Integer.valueOf(i));
            }
        } finally {
            ThreadContext.clearAll();
        }
    }

    private static long countRows() {
        try (Connection c = DriverManager.getConnection(URL, "sa", "");
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM LOG_EVENTS")) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (final Exception e) {
            return 0L;
        }
    }

    private static void dump() {
        System.out.println();
        System.out.printf("  %-10s %-7s %-32s %-40s %s%n",
                "TIME", "LEVEL", "LOGGER", "MESSAGE", "TRACE_ID");
        final String sql = "SELECT EVENT_DATE, LEVEL, LOGGER, MESSAGE, TRACE_ID "
                + "FROM LOG_EVENTS ORDER BY ID DESC LIMIT 8";
        try (Connection c = DriverManager.getConnection(URL, "sa", "");
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                System.out.printf("  %-10s %-7s %-32s %-40s %s%n",
                        String.valueOf(rs.getObject(1)).substring(11, 19),
                        rs.getString(2),
                        truncate(rs.getString(3), 31),
                        truncate(rs.getString(4), 39),
                        rs.getString(5));
            }
        } catch (final Exception e) {
            System.out.println("  <could not read rows back: " + e + ">");
        }
    }

    private static String truncate(final String s, final int max) {
        if (s == null) {
            return "<null>";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private JdbcJndiBench() {}
}
