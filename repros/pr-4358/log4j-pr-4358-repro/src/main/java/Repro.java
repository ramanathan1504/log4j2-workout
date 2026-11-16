import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    private static final String USER = "user";
    private static final String PASSWORD = "päss";
    private static final String CONFIG = "<Configuration status=\"WARN\" name=\"remote\"><Appenders>"
            + "<Console name=\"Out\"/></Appenders><Loggers><Root level=\"info\"><AppenderRef ref=\"Out\"/></Root>"
            + "</Loggers></Configuration>";

    public static void main(String[] args) throws Exception {
        String caseName = args[0];
        String property = args[1];
        String charset = args[2];
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/log4j2.xml", exchange -> {
            received.compareAndSet(null, exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = CONFIG.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        System.setProperty("log4j2.configurationFile",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/log4j2.xml");
        System.setProperty("log4j2.Configuration.allowedProtocols", "http");
        System.setProperty("log4j2.configurationUserName", USER);
        System.setProperty("log4j2.configurationPassword", PASSWORD);
        if (!"none".equals(property)) {
            System.setProperty(property, charset);
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        String loadedName = context.getConfiguration().getName();
        context.stop();
        server.stop(0);
        String header = received.get();
        String decodedCharset = "none";
        if (header != null && header.startsWith("Basic ")) {
            byte[] raw = Base64.getDecoder().decode(header.substring(6));
            if (java.util.Arrays.equals(raw, (USER + ":" + PASSWORD).getBytes(StandardCharsets.ISO_8859_1))) {
                decodedCharset = "ISO-8859-1";
            } else if (java.util.Arrays.equals(raw, (USER + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8))) {
                decodedCharset = "UTF-8";
            } else {
                decodedCharset = "other(" + raw.length + " bytes)";
            }
        }
        String expected = "none".equals(property) ? "UTF-8" : charset;
        System.out.println((expected.equals(decodedCharset) ? "PASS " : "FAIL ") + caseName + ": " + property + "="
                + charset + " expected=" + expected + " observed=" + decodedCharset + " header=" + header
                + " config=" + loadedName);
    }
}
