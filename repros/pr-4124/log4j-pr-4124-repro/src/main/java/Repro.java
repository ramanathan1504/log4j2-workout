import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;

public class Repro {

    public static void main(final String[] args) throws Exception {
        final String loggerClass = LogManager.getLogger("app").getClass().getName();
        LogManager.getLogger("app").info("async-event");
        LogManager.shutdown();
        final String content = new String(Files.readAllBytes(Paths.get(System.getProperty("out"), "app.log")), StandardCharsets.UTF_8).trim();
        System.out.println("RESULT logger=" + loggerClass + " file=" + content);
    }
}
