import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]);
        System.setProperty("repro.dir", dir.toString());
        LogManager.getLogger(Repro.class).info("<b>Tom</b> & \"Jerry\" 'x'");
        ((LoggerContext) LogManager.getContext(false)).stop();

        String plain = new String(Files.readAllBytes(dir.resolve("plain.log")), StandardCharsets.UTF_8).trim();
        String html = new String(Files.readAllBytes(dir.resolve("html.log")), StandardCharsets.UTF_8).trim();
        String expectedPlain = "[INFO] <b>Tom</b> & \"Jerry\" 'x'";
        String expectedHtml = "[INFO] &lt;b&gt;Tom&lt;/b&gt; &amp; &quot;Jerry&quot; &apos;x&apos;";
        System.out.println((plain.equals(expectedPlain) ? "PASS" : "FAIL") + " control %m: observed=" + plain);
        System.out.println((html.equals(expectedHtml) ? "PASS" : "FAIL") + " %m{html}: observed=" + html
                + " (" + html.length() + " chars)");
    }
}
