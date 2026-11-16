import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class Repro {

    public static void main(final String[] args) throws Exception {
        final Logger logger = LogManager.getLogger("com.example.rules");
        logger.info(MarkerManager.getMarker("FULL_PLAN"), "marked-event");
        logger.info("unmarked-event");
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final LoggerConfig loggerConfig = context.getConfiguration().getLoggers().get("com.example.rules");
        final Filter filter = loggerConfig == null ? null : loggerConfig.getFilter();
        final String type = filter == null ? "null" : filter.getClass().getSimpleName();
        final String onMatch = filter == null ? "null" : String.valueOf(filter.getOnMatch());
        final String onMismatch = filter == null ? "null" : String.valueOf(filter.getOnMismatch());
        LogManager.shutdown();
        verdict(type.equals("MarkerFilter"), "filter-type", type);
        verdict("DENY".equals(onMatch), "onMatch=DENY", onMatch);
        verdict("ACCEPT".equals(onMismatch), "onMisMatch=ACCEPT", onMismatch);
        final List<String> lines = Files.readAllLines(Paths.get(System.getProperty("out"), "app.log"), StandardCharsets.UTF_8);
        verdict(lines.equals(java.util.Collections.singletonList("unmarked-event")), "file-content (marked denied, unmarked kept)", lines.toString());
    }

    private static void verdict(final boolean ok, final String name, final String observed) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + " observed=" + observed);
    }
}
