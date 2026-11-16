import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.StructuredDataMessage;

public class Repro {

    public static void main(final String[] args) throws Exception {
        final Logger logger = LogManager.getLogger(Repro.class);
        ThreadContext.put("mdcKey", "A\nB");
        logger.info("mdc-event");
        ThreadContext.clearMap();
        final StructuredDataMessage sd = new StructuredDataMessage("sd", "sd-event", "audit");
        sd.put("sdKey", "C\r\nD");
        logger.info(sd);
        LogManager.shutdown();

        final Path dir = Paths.get(System.getProperty("outdir"));
        check(dir, "backslash-n.log", "mdcKey", "A\\\\nB", "A\\nB");
        check(dir, "backslash-n.log", "sdKey", "C\\\\nD", "C\\nD");
        check(dir, "bracket.log", "mdcKey", "A\\]B", "A]B");
        check(dir, "octal.log", "mdcKey", "A#012B", "A#012B");
    }

    private static void check(final Path dir, final String file, final String key, final String expected, final String old)
            throws Exception {
        final String text = new String(Files.readAllBytes(dir.resolve(file)), StandardCharsets.UTF_8);
        final Matcher m = Pattern.compile(Pattern.quote(key) + "=\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(text);
        final String observed = m.find() ? m.group(1) : "<missing: " + firstSdElement(text) + ">";
        final String verdict = observed.equals(expected) ? "PASS" : "FAIL";
        System.out.println(verdict + " " + file + " " + key + " expected=" + expected + " observed=" + observed
                + (expected.equals(old) ? " (control)" : ""));
    }

    private static String firstSdElement(final String text) {
        final int start = text.indexOf('[');
        return start < 0 ? text.trim() : text.substring(start, Math.min(text.length(), start + 120)).trim();
    }
}
