import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.util.KeyValuePair;

public class Repro {
    public static void main(final String[] args) throws Exception {
        switch (args[0]) {
            case "S1":
                System.setProperty("log4j2.configurationFile", "keyless-gelf.xml");
                LogManager.getLogger("S1").info("hello");
                LogManager.shutdown();
                break;
            case "S2":
                for (final String key : new String[] {null, ""}) {
                    try {
                        final KeyValuePair pair = new KeyValuePair(key, "v");
                        System.out.println("S2 key=" + (key == null ? "null" : "\"\"") + " -> created " + pair);
                    } catch (final RuntimeException e) {
                        System.out.println("S2 key=" + (key == null ? "null" : "\"\"") + " -> " + e);
                    }
                }
                break;
            case "S3":
                final ConfigurationBuilder<BuiltConfiguration> b = ConfigurationBuilderFactory.newConfigurationBuilder();
                try {
                    b.add(b.newAppender("Out", "Console")
                            .add(b.newLayout("GelfLayout")
                                    .addAttribute("host", "myhost")
                                    .addComponent(b.newKeyValuePair(null, "orphan"))));
                } catch (final RuntimeException e) {
                    System.out.println("S3 newKeyValuePair(null) -> " + e);
                    break;
                }
                final String xml = b.toXmlConfiguration();
                System.out.println("S3 generated XML KeyValuePair: "
                        + xml.replaceAll("(?s).*?(<KeyValuePair[^>]*>).*", "$1"));
                try (LoggerContext ctx = new LoggerContext("S3")) {
                    ctx.start(new XmlConfiguration(
                            ctx, new ConfigurationSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))));
                    System.out.println("S3 re-parse -> started=" + ctx.isStarted());
                } catch (final RuntimeException e) {
                    System.out.println("S3 re-parse -> " + e);
                }
                break;
            default:
                throw new IllegalArgumentException(args[0]);
        }
    }
}
