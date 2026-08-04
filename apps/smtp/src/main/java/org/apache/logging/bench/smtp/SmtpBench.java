package org.apache.logging.bench.smtp;

import java.util.ServiceLoader;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.internet.MimeMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.net.MailManagerFactory;

/**
 * The SMTP appender, verified by the mail that arrives. Feature matrix §1, §12.
 *
 * <p>Two things make this appender unlike the others, and both are the reason
 * it needs a real server rather than a mock:
 *
 * <ul>
 *   <li><strong>It buffers.</strong> Nothing is sent until an event at or above
 *       the trigger level arrives; the preceding {@code bufferSize} events are
 *       then attached as context. So a run that logs only INFO produces no mail
 *       and no error, and looks identical to a broken configuration.</li>
 *   <li><strong>The implementation is chosen by ServiceLoader.</strong> The
 *       appender lives in log4j-core and is written against javax.mail;
 *       log4j-jakarta-smtp supplies a jakarta.mail {@code MailManagerFactory}
 *       and a services file, so simply being on the classpath replaces the mail
 *       stack. No configuration mentions it, and nothing logs which one won —
 *       hence the report below.</li>
 * </ul>
 *
 * <p>GreenMail runs the SMTP server in this JVM, so the whole path is exercised
 * without anything to start first.
 *
 * <pre>
 *   ./bench run smtp --config xml/appender-smtp
 * </pre>
 */
public final class SmtpBench {

    /** Port 3025 is GreenMail's own default for SMTP — unprivileged, and its
     *  convention rather than an arbitrary choice. */
    private static final int PORT = Integer.getInteger("bench.smtp.port", 3025);

    private static final Logger log = LogManager.getLogger(SmtpBench.class);

    public static void main(final String[] args) throws Exception {
        final GreenMail server = new GreenMail(new ServerSetup(PORT, "127.0.0.1", "smtp"));
        server.setUser("bench@example.com", "bench", "benchpw");
        server.start();

        try {
            banner();
            emit();

            // Flush the appender: the manager sends on the triggering event, but
            // the send itself is what shutdown waits for.
            LogManager.shutdown();

            // GreenMail delivers asynchronously; wait rather than sleep-and-hope.
            final boolean arrived = server.waitForIncomingEmail(10_000, 1);
            report(server, arrived);
        } finally {
            server.stop();
        }
    }

    private static void banner() {
        System.out.println("SMTP appender bench");
        System.out.println("  smtp server  127.0.0.1:" + PORT + "  (GreenMail, in-process)");
        System.out.println("  config       "
                + System.getProperty("log4j.configurationFile", "<default>"));

        // Which mail implementation the ServiceLoader picked. This is the only
        // place it is visible: no configuration names it, and a run with the
        // javax stack looks exactly the same until the message headers differ.
        final ServiceLoader<MailManagerFactory> loader = ServiceLoader.load(MailManagerFactory.class);
        boolean any = false;
        for (final MailManagerFactory factory : loader) {
            System.out.println("  MailManagerFactory  " + factory.getClass().getName());
            any = true;
        }
        if (!any) {
            System.out.println("  MailManagerFactory  <none registered — falling back to");
            System.out.println("                       log4j-core's javax.mail SmtpManager>");
        }
        System.out.println();
    }

    private static void emit() {
        ThreadContext.put("traceId", "smtp-bench-0001");
        try {
            // Below the trigger level: buffered, not sent. These become the
            // context attached to the mail when the ERROR below fires — which is
            // the entire point of this appender and the thing a mock cannot show.
            log.info("Context event 1 — buffered, not sent");
            log.info("Context event 2 — buffered, not sent");
            log.warn("Context event 3 — still below the trigger level");

            // At or above the trigger level: this is what sends.
            log.error("The triggering event", new IllegalStateException("synthetic SMTP failure"));
        } finally {
            ThreadContext.clearAll();
        }
    }

    private static void report(final GreenMail server, final boolean arrived) throws Exception {
        final MimeMessage[] received = server.getReceivedMessages();
        // One per RECIPIENT, not per send: the config has a `to` and a `cc`, so
        // a single triggering event yields two here. GreenMail counts
        // deliveries, and mistaking that for two sends is an easy way to go
        // looking for a duplicate-send bug that does not exist.
        System.out.printf("%nMessages received: %d  (one per recipient — to + cc)%n",
                received.length);

        if (!arrived || received.length == 0) {
            System.out.println();
            System.out.println("Nothing arrived. Either the active config has no SMTP appender,");
            System.out.println("or no event reached its trigger level, or the send failed and");
            System.out.println("was reported through StatusLogger — an SMTP appender that never");
            System.out.println("sends looks exactly like one that is working but idle.");
            return;
        }

        final MimeMessage mail = received[0];
        System.out.println();
        System.out.println("  from      " + java.util.Arrays.toString(mail.getFrom()));
        System.out.println("  to        " + java.util.Arrays.toString(
                mail.getRecipients(MimeMessage.RecipientType.TO)));
        System.out.println("  subject   " + mail.getSubject());
        System.out.println("  type      " + mail.getContentType());

        final String body = bodyOf(mail);
        final long lines = body.lines().count();
        System.out.printf("  body      %d line(s)%n", lines);

        // The buffered events must be in there: their presence is what proves
        // the appender is doing more than sending one message per error.
        System.out.println();
        for (final String needle : new String[] {
                "Context event 1", "Context event 2", "Context event 3", "The triggering event"}) {
            System.out.printf("  %-22s %s%n", needle,
                    body.contains(needle) ? "present" : "MISSING");
        }
    }

    private static String bodyOf(final MimeMessage mail) throws Exception {
        final Object content = mail.getContent();
        if (content instanceof jakarta.mail.Multipart multipart) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                sb.append(multipart.getBodyPart(i).getContent()).append('\n');
            }
            return sb.toString();
        }
        return String.valueOf(content);
    }

    private SmtpBench() {}
}
