import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

public class Repro {
    public static void main(final String[] args) {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration configuration = context.getConfiguration();
        System.out.println("CONFIG_NAME=" + configuration.getName());
        System.out.println("CONFIG_CLASS=" + configuration.getClass().getSimpleName());
        LogManager.getLogger("repro").info("info-line-from-repro");
        context.stop();
    }
}
