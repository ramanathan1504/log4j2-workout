import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

public class Repro {
    public static void main(String[] args) throws Exception {
        org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger("app");
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        String configType = configuration.getClass().getSimpleName();
        boolean hasGood = configuration.getAppenders().containsKey("good");
        System.out.println((hasGood ? "PASS " : "FAIL ") + "valid-appender-configured configuration=" + configType + " appenders=" + configuration.getAppenders().keySet());
        logger.info("hello-from-log4j1");
        context.stop();
        File file = new File(System.getProperty("logfile"));
        String content = file.exists() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim() : "<no file>";
        System.out.println((content.contains("hello-from-log4j1") ? "PASS " : "FAIL ") + "valid-appender-received-event file=" + content);
    }
}
