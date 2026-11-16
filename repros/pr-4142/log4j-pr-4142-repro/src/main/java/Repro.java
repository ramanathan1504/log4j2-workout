import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;

public class Repro {

    public static void main(String[] args) throws Exception {
        String outdir = System.getProperty("outdir");
        LogManager.getLogger("app.db").info("db-event");
        LogManager.getLogger("app.web").info("root-event");
        LogManager.shutdown();
        List<String> lines = Files.readAllLines(Paths.get(outdir, "app.log"), StandardCharsets.UTF_8);
        report("logger.db.property", lines, "db-event", "subsystem=Database");
        report("rootLogger.property", lines, "root-event", "region=eu-west");
    }

    static void report(String name, List<String> lines, String marker, String expected) {
        String observed = "<no line>";
        for (String line : lines) {
            if (line.contains("|" + marker + "|")) {
                observed = line;
            }
        }
        System.out.println((observed.contains(expected) ? "PASS " : "FAIL ") + name + " expected=" + expected + " line=" + observed);
    }
}
