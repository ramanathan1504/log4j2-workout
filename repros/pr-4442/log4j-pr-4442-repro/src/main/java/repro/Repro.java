package repro;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Repro {
    public static void main(final String[] args) throws Exception {
        final Logger logger = LogManager.getLogger(Repro.class);
        logger.info("keep this event");
        logger.info("drop this event");
        LogManager.shutdown();
        final String log = new String(Files.readAllBytes(Paths.get(System.getProperty("out"), "app.log")), StandardCharsets.UTF_8);
        System.out.println("RESULT keep=" + log.contains("keep this event") + " drop=" + log.contains("drop this event"));
    }
}
