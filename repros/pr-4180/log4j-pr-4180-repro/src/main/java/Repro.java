import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.message.StructuredDataMessage;

public class Repro {
    public static void main(String[] args) throws Exception {
        StructuredDataMessage withText = new StructuredDataMessage("login@32473", "user <alice> logged in", "audit");
        withText.put("ip", "10.0.0.1");
        String xml = withText.asString("XML");
        report("api-free-form-text", xml.contains("<message>user &lt;alice&gt; logged in</message>"), xml);

        StructuredDataMessage mapKeyMessage = new StructuredDataMessage("login@32473", "text", "audit");
        mapKeyMessage.put("message", "from-map");
        String xml2 = mapKeyMessage.asString("XML");
        report("api-text-and-map-key-message-distinct",
                xml2.contains("<message>text</message>") && xml2.contains("<Entry key=\"message\">from-map</Entry>"), xml2);

        Logger logger = LogManager.getLogger("audit");
        logger.info(withText);
        ((LoggerContext) LogManager.getContext(false)).stop();
        String file = new String(Files.readAllBytes(Paths.get(System.getProperty("logfile"))), StandardCharsets.UTF_8);
        report("layout-%m{XML}-free-form-text", file.contains("<message>user &lt;alice&gt; logged in</message>"), file);
    }

    static void report(String name, boolean ok, String observed) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + " observed=" + observed.replace("\n", "\\n"));
    }
}
