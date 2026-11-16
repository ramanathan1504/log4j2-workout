import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Repro {
    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(Repro.class);
        logger.error("application message");
        System.out.println("logger class: " + logger.getClass().getName());
    }
}
