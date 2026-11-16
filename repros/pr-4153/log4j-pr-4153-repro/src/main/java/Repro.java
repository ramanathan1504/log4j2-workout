import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.filter.StringMatchFilter;

public class Repro {

    public static void main(String[] args) throws Exception {
        String outdir = System.getProperty("outdir");
        Logger logger = LogManager.getLogger(Repro.class);
        logger.info("public-message");
        logger.info("secret-message");

        StringMatchFilter built = StringMatchFilter.newBuilder()
                .setOnMatch(Filter.Result.DENY)
                .setOnMismatch(Filter.Result.NEUTRAL)
                .build();
        LogManager.shutdown();

        List<String> noText = Files.readAllLines(Paths.get(outdir, "no-text.log"), StandardCharsets.UTF_8);
        List<String> withText = Files.readAllLines(Paths.get(outdir, "with-text.log"), StandardCharsets.UTF_8);
        System.out.println((noText.equals(List.of("public-message", "secret-message")) ? "PASS " : "FAIL ")
                + "xml-StringMatchFilter-without-text-not-built lines=" + noText);
        System.out.println((built == null ? "PASS " : "FAIL ")
                + "builder-without-text-build() returned=" + (built == null ? "null" : "filter text='" + built + "'"));
        System.out.println((withText.equals(List.of("public-message")) ? "PASS " : "FAIL ")
                + "xml-StringMatchFilter-with-text-control lines=" + withText);
    }
}
