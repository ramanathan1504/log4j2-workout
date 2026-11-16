import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.StructuredDataCollectionMessage;
import org.apache.logging.log4j.message.StructuredDataId;
import org.apache.logging.log4j.message.StructuredDataMessage;

public class Repro {
    public static void main(String[] args) throws Exception {
        Logger logger = LogManager.getLogger("repro");

        logger.info(new StructuredDataMessage("control", "plain", "Audit"));

        ThreadContext.put("user", "alice");
        StructuredDataMessage mdcNamed = new StructuredDataMessage("mdc", "mdc-merge", "Audit");
        mdcNamed.put("request", "r1");
        logger.info(mdcNamed);
        ThreadContext.clearMap();

        StructuredDataId transfer = new StructuredDataId("Transfer", 18060, null, null);
        StructuredDataMessage first = new StructuredDataMessage(transfer, "first", "Audit");
        first.put("from", "A");
        StructuredDataMessage second = new StructuredDataMessage(transfer, "second", "Audit");
        second.put("to", "B");
        logger.info(new StructuredDataCollectionMessage(Arrays.asList(first, second)));

        LogManager.shutdown();
        List<String> lines = Files.readAllLines(Paths.get(System.getProperty("outdir"), "syslog.log"));
        report("control single SDM", lines, "[control@32473]", "plain");
        report("SDM id=mdc merged with ThreadContext", lines, "[mdc@32473 ", "user=\"alice\"", "request=\"r1\"");
        report("two SDMs id=Transfer@18060 in a collection", lines, "[Transfer@18060 ", "from=\"A\"", "to=\"B\"");
    }

    private static void report(String name, List<String> lines, String... needles) {
        String found = null;
        for (String line : lines) {
            if (line.contains(needles[0])) {
                found = line;
            }
        }
        boolean ok = found != null;
        for (String needle : needles) {
            ok &= found != null && found.contains(needle);
        }
        String observed = found == null ? "no line written" : found.substring(found.indexOf('['));
        System.out.println((ok ? "PASS " : "FAIL ") + name + " observed=" + observed.trim());
    }
}
