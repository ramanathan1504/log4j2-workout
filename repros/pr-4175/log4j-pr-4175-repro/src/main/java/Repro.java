import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;

public final class Repro {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String TRACE_FLAGS = "01";

    public static void main(final String[] args) throws Exception {
        final Path outDir = Paths.get(System.getProperty("outdir"));
        LogManager.getLogger(Repro.class).info("traced event");
        LogManager.shutdown();
        report("native-resolvers", outDir.resolve("native.json"));
        report("pattern-resolver-control", outDir.resolve("pattern.json"));
    }

    private static void report(final String caseName, final Path file) throws Exception {
        if (!Files.exists(file) || Files.size(file) == 0) {
            System.out.println("FAIL " + caseName + " observed=no-output (appender not created)");
            return;
        }
        final String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        final String traceId = field(json, "traceId");
        final String spanId = field(json, "spanId");
        final String traceFlags = field(json, "traceFlags");
        final boolean ok = TRACE_ID.equals(traceId) && SPAN_ID.equals(spanId) && TRACE_FLAGS.equals(traceFlags);
        System.out.println((ok ? "PASS " : "FAIL ") + caseName + " observed=" + json);
    }

    private static String field(final String json, final String name) {
        final Matcher matcher = Pattern.compile("\"" + name + "\":(\"([^\"]*)\"|null)").matcher(json);
        if (!matcher.find()) {
            return "<absent>";
        }
        return matcher.group(2);
    }
}
