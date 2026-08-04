package org.apache.logging.bench.jms;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * The JMS appender, verified by consuming the messages it publishes.
 * Feature matrix §1, §12.
 *
 * <p>Two things are specific to this appender:
 *
 * <ul>
 *   <li><strong>The layout decides the JMS message TYPE</strong>, and a layout
 *       is mandatory — without one the appender refuses to build. With
 *       {@code MessageLayout} the event's own Message object is handed to JMS,
 *       so a {@code StringMapMessage} arrives as a JMS {@code MapMessage} and
 *       anything else as an {@code ObjectMessage}; with {@code PatternLayout} it
 *       arrives as a {@code TextMessage} with every field boundary gone. All
 *       three are produced below, because the difference is invisible from the
 *       Log4j side and total from the consumer's.</li>
 *   <li><strong>It resolves its destination through JNDI</strong>, like the
 *       JDBC DataSource source, so the same ordering constraint applies: the
 *       bindings must exist before the first logger is acquired. Hence the lazy
 *       logger below.</li>
 * </ul>
 *
 * <p>Artemis runs in-VM over the {@code vm://0} transport — no port, nothing to
 * start first.
 *
 * <pre>
 *   ./bench run jms --config xml/appender-jms
 * </pre>
 */
public final class JmsBench {

    private static final String QUEUE = "log4jBenchQueue";

    /** Lazy for the same reason as apps/jdbc-jndi: a static Logger field would
     *  configure Log4j at class load, before the broker and its JNDI bindings
     *  exist, and the appender would fail to resolve its destination. */
    private static Logger logger() {
        return LogManager.getLogger(JmsBench.class);
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("JMS appender bench");
        System.out.println("  broker       ActiveMQ Artemis, embedded (vm://0)");
        System.out.println("  queue        " + QUEUE);
        System.out.println("  factory      "
                + System.getProperty("java.naming.factory.initial", "<unset>"));
        System.out.println("  enableJndiJms   "
                + System.getProperty("log4j2.enableJndiJms", "<unset — the appender will refuse>"));
        System.out.println("  config       "
                + System.getProperty("log4j.configurationFile", "<default>"));

        final EmbeddedActiveMQ broker = startBroker();
        try {
            // Everything below happens before the first logger is acquired.
            final Context jndi = jndiContext();
            System.out.println();

            emit();
            LogManager.shutdown();

            consumeAndReport(jndi);
        } finally {
            broker.stop();
        }
    }

    /**
     * An in-VM broker with security and persistence off.
     *
     * <p>Persistence matters here: with the journal enabled Artemis writes a
     * data directory beside the bench and replays it on the next run, so a
     * second run would consume the first run's messages and the count would
     * lie.
     */
    private static EmbeddedActiveMQ startBroker() throws Exception {
        final ConfigurationImpl config = new ConfigurationImpl();
        config.setPersistenceEnabled(false);
        config.setSecurityEnabled(false);
        config.setJournalDirectory("target/artemis-journal");
        config.addAcceptorConfiguration("in-vm", "vm://0");

        final EmbeddedActiveMQ broker = new EmbeddedActiveMQ();
        broker.setConfiguration(config);
        broker.start();
        return broker;
    }

    /**
     * The JNDI context, built from {@code jndi.properties} on the classpath.
     *
     * <p>No environment map is passed deliberately: the appender constructs its
     * own {@code InitialContext} and cannot be handed one, so the configuration
     * has to come from somewhere both of us see. {@code jndi.properties} is that
     * place — system properties are not, because InitialContext copies only the
     * standard {@code java.naming.*} keys and ignores the
     * {@code connectionFactory.*} and {@code queue.*} entries Artemis needs.
     */
    private static Context jndiContext() throws Exception {
        return new InitialContext();
    }

    private static void emit() {
        final Logger log = logger();
        ThreadContext.put("traceId", "jms-bench-0001");
        try {
            log.info("A plain event");
            log.info("A parameterised event: user {} order {}", "alice", Integer.valueOf(4711));
            log.warn("A warning");
            log.error("An event with a throwable",
                    new IllegalStateException("synthetic JMS failure"));

            // A StringMapMessage, which is what makes MessageLayout produce a
            // JMS MapMessage rather than an ObjectMessage: the layout hands over
            // the Message object, so the event's own type decides what JMS sees.
            final org.apache.logging.log4j.message.StringMapMessage order =
                    new org.apache.logging.log4j.message.StringMapMessage();
            order.put("event", "checkout");
            order.put("orderId", "4711");
            order.put("customer", "alice");
            log.info(order);
        } finally {
            ThreadContext.clearAll();
        }
    }

    /**
     * Drains the queue and reports what actually arrived — the assertion, since
     * a JMS appender that cannot publish reports through StatusLogger and lets
     * the JVM exit 0.
     */
    private static void consumeAndReport(final Context jndi) throws Exception {
        final ConnectionFactory factory = (ConnectionFactory) jndi.lookup("ConnectionFactory");
        final Queue queue = (Queue) jndi.lookup("queue/" + QUEUE);

        int count = 0;
        try (Connection connection = factory.createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    MessageConsumer consumer = session.createConsumer(queue)) {

                System.out.println();
                System.out.println("Messages on the queue:");
                Message message;
                // A short timeout, not zero: the appender publishes on the
                // logging thread but the broker delivers asynchronously.
                while ((message = consumer.receive(2000)) != null) {
                    count++;
                    describe(message, count);
                }
            }
        }

        System.out.printf("%nTotal received: %d%n", count);
        if (count == 0) {
            System.out.println();
            System.out.println("Nothing arrived. Check log4j2.enableJndiJms, that the config's");
            System.out.println("factoryBindingName and destinationBindingName match the bindings");
            System.out.println("above, and the status logger — all three fail silently from the");
            System.out.println("application's point of view.");
        }
    }

    private static void describe(final Message message, final int index) throws Exception {
        if (message instanceof jakarta.jms.MapMessage map) {
            // The entries are the LOG EVENT'S map, not event metadata: with
            // MessageLayout the appender hands JMS the Message object, so a
            // StringMapMessage's own keys are what a consumer reads. There is no
            // Level or LoggerName here — that information is not carried at all,
            // which is the trade for getting structured data with no parsing.
            final StringBuilder entries = new StringBuilder();
            final java.util.Enumeration<?> names = map.getMapNames();
            while (names.hasMoreElements()) {
                final String key = String.valueOf(names.nextElement());
                entries.append(key).append('=').append(map.getString(key)).append(' ');
            }
            System.out.printf("  %d. MapMessage   %s%n", index, entries.toString().strip());
        } else if (message instanceof TextMessage text) {
            // What a configured layout produces instead — one formatted string,
            // structure discarded.
            System.out.printf("  %d. TextMessage  %s%n", index, text.getText().strip());
        } else {
            System.out.printf("  %d. %s%n", index, message.getClass().getName());
        }
    }

    private JmsBench() {}
}
