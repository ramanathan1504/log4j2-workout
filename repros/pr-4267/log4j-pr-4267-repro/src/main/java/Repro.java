import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;

public final class Repro {

    private static final List<String> MESSAGES = new CopyOnWriteArrayList<>();

    public static void main(final String[] args) throws Exception {
        final ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        System.setProperty("smtpPort", Integer.toString(server.getLocalPort()));
        final Thread serverThread = new Thread(() -> serve(server), "smtp-server");
        serverThread.setDaemon(true);
        serverThread.start();

        ThreadContext.put("traceId", "TraceValue1");
        LogManager.getLogger(Repro.class).error("safe\r\nX-Evil: injected");
        ThreadContext.clearAll();
        final String managerClass = ((org.apache.logging.log4j.core.appender.SmtpAppender)
                        ((org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false))
                                .getConfiguration()
                                .getAppender("mail"))
                .getManager()
                .getClass()
                .getName();
        LogManager.shutdown();
        server.close();

        System.out.println("manager=" + managerClass);
        System.out.println("messages-received=" + MESSAGES.size());
        if (MESSAGES.isEmpty()) {
            System.out.println("FAIL delivery observed=no message received");
            return;
        }
        final String raw = MESSAGES.get(0);
        Files.write(Paths.get(System.getProperty("outdir"), "message.eml"), raw.getBytes(StandardCharsets.UTF_8));
        final List<String> headers = headerLines(raw);
        check("X-Static", headers, "[fixed-value]");
        check("X-Trace-Id", headers, "[TraceValue1]");
        check("X-Tag", headers, "[first, second]");
        check("X-Message", headers, "[safe  X-Evil: injected]");
        check("X-Evil", headers, "[]");
        check("X", headers, "[]");
    }

    private static void check(final String name, final List<String> headers, final String expected) {
        final List<String> values = new ArrayList<>();
        for (final String header : headers) {
            final int colon = header.indexOf(':');
            if (colon > 0 && header.substring(0, colon).equalsIgnoreCase(name)) {
                values.add(header.substring(colon + 1).trim());
            }
        }
        final String observed = values.toString();
        System.out.println((observed.equals(expected) ? "PASS " : "FAIL ") + "header " + name + " expected=" + expected
                + " observed=" + observed);
    }

    private static List<String> headerLines(final String raw) {
        final List<String> headers = new ArrayList<>();
        for (final String line : raw.split("\r\n")) {
            if (line.isEmpty()) {
                break;
            }
            if ((line.startsWith(" ") || line.startsWith("\t")) && !headers.isEmpty()) {
                headers.set(headers.size() - 1, headers.get(headers.size() - 1) + line);
            } else {
                headers.add(line);
            }
        }
        return headers;
    }

    private static void serve(final ServerSocket server) {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                final BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                final OutputStream out = socket.getOutputStream();
                reply(out, "220 repro ESMTP");
                String line;
                while ((line = in.readLine()) != null) {
                    final String command = line.toUpperCase();
                    if (command.startsWith("EHLO") || command.startsWith("HELO")) {
                        reply(out, "250 repro");
                    } else if (command.startsWith("DATA")) {
                        reply(out, "354 go ahead");
                        final StringBuilder data = new StringBuilder();
                        while ((line = in.readLine()) != null && !line.equals(".")) {
                            data.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                        }
                        MESSAGES.add(data.toString());
                        reply(out, "250 queued");
                    } else if (command.startsWith("QUIT")) {
                        reply(out, "221 bye");
                        break;
                    } else {
                        reply(out, "250 ok");
                    }
                }
            } catch (final Exception closed) {
                return;
            }
        }
    }

    private static void reply(final OutputStream out, final String text) throws Exception {
        out.write((text + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }
}
