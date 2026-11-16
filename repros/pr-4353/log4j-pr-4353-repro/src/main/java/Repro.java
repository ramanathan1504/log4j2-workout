import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(Repro.class);
        logger.info("written-by-converted-config");
        ((LoggerContext) LogManager.getContext(false)).stop();
    }
}
