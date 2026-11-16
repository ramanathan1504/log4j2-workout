import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(String[] args) throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        Map<String, String> tenantByBody = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ingest", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                bodies.add(body);
                String tenant = exchange.getRequestHeaders().getFirst("X-Tenant");
                tenantByBody.put(body, tenant == null ? "<absent>" : tenant);
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        System.setProperty("repro.port", Integer.toString(server.getAddress().getPort()));

        Logger logger = LogManager.getLogger(Repro.class);
        ThreadContext.put("tenant", "acme");
        logger.info("event-1-valid-tenant");
        ThreadContext.put("tenant", "evil\r\nX-Injected: 1");
        logger.info("event-2-line-break-tenant");
        ThreadContext.put("tenant", "acme");
        logger.info("event-3-valid-tenant");
        ThreadContext.clearMap();
        ((LoggerContext) LogManager.getContext(false)).stop();
        server.stop(0);

        report("control event-1 delivered", tenantByBody.get("event-1-valid-tenant"));
        report("event-2 with CRLF in X-Tenant delivered", tenantByBody.get("event-2-line-break-tenant"));
        report("control event-3 delivered", tenantByBody.get("event-3-valid-tenant"));
        System.out.println("requests received=" + bodies.size() + " of 3");
    }

    private static void report(String name, String tenantHeader) {
        if (tenantHeader == null) {
            System.out.println("FAIL " + name + ": not received");
        } else {
            System.out.println("PASS " + name + ": received, X-Tenant=" + tenantHeader);
        }
    }
}
