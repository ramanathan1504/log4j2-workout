import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(String[] args) throws Exception {
        Logger logger = LogManager.getLogger("com.example.App");
        logger.atInfo().log("builder-without-location");
        logger.info("plain-info-control");
        logger.atInfo().withLocation().log("builder-with-location-control");
        String loggerClass = logger.getClass().getName();
        ((LoggerContext) LogManager.getContext(false)).stop();
        List<String> lines =
                Files.readAllLines(Paths.get(System.getProperty("repro.out"), "app.log"), StandardCharsets.UTF_8);
        System.out.println("logger implementation: " + loggerClass);
        for (String line : lines) {
            String[] parts = line.split("\\|", -1);
            boolean located = "Repro".equals(parts[1]) && "main".equals(parts[2]);
            System.out.println((located ? "PASS " : "FAIL ") + parts[0] + ": class=" + parts[1] + " method=" + parts[2]
                    + " line=" + parts[3]);
        }
        if (lines.size() != 3) {
            System.out.println("FAIL event count: expected=3 observed=" + lines.size());
        }
    }
}
