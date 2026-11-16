import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

public class Repro {
    public static void main(final String[] args) {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration configuration = context.getConfiguration();
        final Appender stdout = configuration.getAppender("StdOut");
        System.out.println("CONFIG_CLASS=" + configuration.getClass().getSimpleName());
        System.out.println("CONFIG_STATE=" + configuration.getState());
        System.out.println("DEFAULT_CONFIG=" + (configuration instanceof DefaultConfiguration));
        System.out.println("STDOUT_APPENDER=" + (stdout == null ? "absent" : stdout.getLayout().getClass().getSimpleName()));
        LogManager.getLogger("repro").info("marker-line-from-repro");
    }
}
