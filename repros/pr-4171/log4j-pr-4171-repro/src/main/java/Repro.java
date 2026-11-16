import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    static final ThreadLocal<String[]> CURRENT_SPAN = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        boolean spiPresent = installProvider();
        System.out.println("info TraceContextProvider-SPI-present=" + spiPresent);

        CURRENT_SPAN.set(new String[] {"4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", "01"});
        LogManager.getLogger("sync").info("sync-event");
        LogManager.getLogger("async").info("async-event");
        CURRENT_SPAN.set(new String[] {"ffffffffffffffffffffffffffffffff", "ffffffffffffffff", "00"});
        LogManager.getLogger("async").info("async-event-2");
        CURRENT_SPAN.remove();
        LogManager.getLogger("sync").info("no-span-event");
        ((LoggerContext) LogManager.getContext(false)).stop();

        List<String> lines = Files.readAllLines(Paths.get(System.getProperty("logfile")), StandardCharsets.UTF_8);
        check(lines, "sync-event", "sync-event trace=4bf92f3577b34da6a3ce929d0e0e4736 span=00f067aa0ba902b7 flags=01");
        check(lines, "async-event", "async-event trace=4bf92f3577b34da6a3ce929d0e0e4736 span=00f067aa0ba902b7 flags=01");
        check(lines, "async-event-2", "async-event-2 trace=ffffffffffffffffffffffffffffffff span=ffffffffffffffff flags=00");
        check(lines, "no-span-event", "no-span-event trace= span= flags=");
    }

    static boolean installProvider() {
        try {
            Class<?> spi = Class.forName("org.apache.logging.log4j.spi.TraceContextProvider");
            Object provider = Proxy.newProxyInstance(Repro.class.getClassLoader(), new Class<?>[] {spi}, (proxy, method, methodArgs) -> {
                String[] span = CURRENT_SPAN.get();
                switch (method.getName()) {
                    case "getTraceId":
                        return span == null ? null : span[0];
                    case "getSpanId":
                        return span == null ? null : span[1];
                    case "getTraceFlags":
                        return span == null ? null : span[2];
                    case "toString":
                        return "ThreadLocalTraceContextProvider";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == methodArgs[0];
                    default:
                        return null;
                }
            });
            System.getProperties().put("log4j2.activeTraceContextProvider", provider);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static void check(List<String> lines, String message, String expected) {
        String observed = lines.stream().filter(l -> l.startsWith(message + " ")).findFirst().orElse("<missing>");
        System.out.println((observed.equals(expected) ? "PASS " : "FAIL ") + message + " expected=[" + expected + "] observed=[" + observed + "]");
    }
}
