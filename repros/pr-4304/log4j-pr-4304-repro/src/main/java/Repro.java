import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.SmtpAppender;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;

public final class Repro {

    static final StringBuilder TRANSCRIPT = new StringBuilder();
    static final CountDownLatch MESSAGE_RECEIVED = new CountDownLatch(1);

    static void serve(final ServerSocket server) {
        try (Socket client = server.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
            out.print("220 repro\r\n");
            out.flush();
            boolean inData = false;
            String line;
            while ((line = in.readLine()) != null) {
                TRANSCRIPT.append(line).append('\n');
                if (inData) {
                    if (".".equals(line)) {
                        inData = false;
                        out.print("250 queued\r\n");
                        out.flush();
                        MESSAGE_RECEIVED.countDown();
                    }
                    continue;
                }
                final String command = line.toUpperCase();
                if (command.startsWith("DATA")) {
                    inData = true;
                    out.print("354 go\r\n");
                } else if (command.startsWith("QUIT")) {
                    out.print("221 bye\r\n");
                    out.flush();
                    return;
                } else {
                    out.print("250 ok\r\n");
                }
                out.flush();
            }
        } catch (final Exception ignored) {
            MESSAGE_RECEIVED.countDown();
        }
    }

    static void report(final String name, final boolean pass, final String observed) {
        System.out.println((pass ? "PASS " : "FAIL ") + name + " observed=" + observed);
    }

    static String grep(final String prefix) {
        for (final String line : TRANSCRIPT.toString().split("\n")) {
            if (line.toUpperCase().startsWith(prefix.toUpperCase())) {
                return line;
            }
        }
        return "<none>";
    }

    @SuppressWarnings("deprecation")
    public static void main(final String[] args) throws Exception {
        final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        final Thread smtp = new Thread(() -> serve(server), "embedded-smtp");
        smtp.setDaemon(true);
        smtp.start();
        final SmtpAppender appender;
        try {
            appender = SmtpAppender.createAppender(null, "Mail", "to@example.org", null, null, "from@example.org",
                    null, "Subject 4304", "smtp", "127.0.0.1", String.valueOf(server.getLocalPort()), null, null,
                    "false", "10", PatternLayout.createDefaultLayout(), null, "false");
        } catch (final Throwable error) {
            report("createAppender", false, error.getClass().getSimpleName() + ": " + error.getMessage());
            report("mail-delivered", false, "no appender");
            return;
        }
        report("createAppender", appender != null && "Mail".equals(appender.getName()),
                "name=" + (appender == null ? "<null appender>" : appender.getName()));
        appender.start();
        appender.append(Log4jLogEvent.newBuilder()
                .setLoggerName("repro")
                .setLevel(Level.ERROR)
                .setMessage(new SimpleMessage("boom"))
                .build());
        final boolean received = MESSAGE_RECEIVED.await(10, TimeUnit.SECONDS);
        appender.stop();
        final String rcpt = grep("RCPT TO");
        final String subject = grep("Subject:");
        report("mail-delivered", received && rcpt.contains("to@example.org") && subject.contains("Subject 4304"),
                "rcpt=" + rcpt + " | " + subject + " | " + grep("MAIL FROM"));
    }
}
