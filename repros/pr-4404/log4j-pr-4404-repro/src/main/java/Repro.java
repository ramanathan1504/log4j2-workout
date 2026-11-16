import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

public class Repro {
    public static void main(String[] args) throws Exception {
        Logger logger = LoggerFactory.getLogger("repro");
        logger.info("info %s", "formatted");
        Wrapper.info(logger, "location-aware %s", "formatted");
        LogManager.shutdown();
        List<String> lines = Files.readAllLines(Paths.get(System.getProperty("outdir"), "app.log"));
        report("control Logger.info(\"info %s\")", lines, "info ", "info formatted | Repro");
        report("LocationAwareLogger.log(\"location-aware %s\")", lines, "location-aware ",
                "location-aware formatted | Repro");
    }

    private static void report(String name, List<String> lines, String prefix, String expected) {
        String observed = "MISSING";
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                observed = line;
            }
        }
        System.out.println((observed.equals(expected) ? "PASS " : "FAIL ") + name + " expected=\"" + expected
                + "\" observed=\"" + observed + "\"");
    }

    static final class Wrapper {
        static void info(Logger logger, String format, Object argument) {
            ((LocationAwareLogger) logger)
                    .log(null, Wrapper.class.getName(), LocationAwareLogger.INFO_INT, format,
                            new Object[] {argument}, null);
        }
    }
}
