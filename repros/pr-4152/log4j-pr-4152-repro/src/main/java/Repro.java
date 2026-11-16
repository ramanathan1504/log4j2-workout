import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.filter.RegexFilter;
import org.apache.logging.log4j.message.SimpleMessage;

public class Repro {

    public static void main(String[] args) throws Exception {
        String outdir = System.getProperty("outdir");
        String programmatic;
        try {
            RegexFilter filter = RegexFilter.createFilter(".*secret.*", null, null, Filter.Result.DENY, Filter.Result.NEUTRAL);
            Filter.Result result = filter.filter(null, Level.INFO, null, new SimpleMessage("secret-message"), null);
            programmatic = "created, filter(secret-message)=" + result;
        } catch (Throwable error) {
            programmatic = "threw " + error;
        }
        System.out.println((programmatic.equals("created, filter(secret-message)=DENY") ? "PASS " : "FAIL ")
                + "createFilter-with-null-useRawMsg observed=" + programmatic);

        Logger logger = LogManager.getLogger(Repro.class);
        logger.info("public-message");
        logger.info("secret-message");
        LogManager.shutdown();
        List<String> lines = Files.readAllLines(Paths.get(outdir, "app.log"), StandardCharsets.UTF_8);
        System.out.println((lines.equals(List.of("public-message")) ? "PASS " : "FAIL ")
                + "xml-RegexFilter-without-useRawMsg-control lines=" + lines);
    }
}
