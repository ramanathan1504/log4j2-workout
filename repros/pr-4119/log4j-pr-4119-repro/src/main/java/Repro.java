import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.filter.RegexFilter;

public class Repro {

    private static final String MULTI_LINE = "first\nline two";

    public static void main(final String[] args) throws Exception {
        if ("api".equals(args[0])) {
            apiCase();
            return;
        }
        final Logger logger = LogManager.getLogger("app");
        logger.info(MULTI_LINE);
        LogManager.shutdown();
        final Path log = Paths.get(System.getProperty("out"), "app.log");
        final List<String> lines = Files.exists(log)
                ? Files.readAllLines(log, StandardCharsets.UTF_8)
                : Collections.singletonList("<no file>");
        final boolean kept = lines.contains("first\\nline two");
        System.out.println("RESULT case=" + args[0] + " multiLineEventKept=" + kept + " file=" + lines);
    }

    private static void apiCase() throws Exception {
        final RegexFilter filter = RegexFilter.createFilter(
                ".*line.*", new String[] {"DOTALL"}, false, Filter.Result.ACCEPT, Filter.Result.DENY);
        final Filter.Result result = filter.filter(null, null, null, (Object) MULTI_LINE, null);
        System.out.println("RESULT case=api createFilter(\".*line.*\", {\"DOTALL\"}) on multi-line message -> " + result);
    }
}
