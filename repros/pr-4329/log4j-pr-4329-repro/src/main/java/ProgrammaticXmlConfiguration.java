import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;

public class ProgrammaticXmlConfiguration extends XmlConfiguration {
    public static final String PROGRAMMATIC_LOGGER = "programmatic";

    public ProgrammaticXmlConfiguration(LoggerContext context, ConfigurationSource source) {
        super(context, source);
    }

    @Override
    protected void doConfigure() {
        super.doConfigure();
        addProgrammaticLogger(this);
    }

    public void postConfigure(Configuration target) {
        addProgrammaticLogger(target);
    }

    @Override
    public Configuration reconfigure() {
        try {
            return new ProgrammaticXmlConfiguration(getLoggerContext(), getConfigurationSource().resetInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void addProgrammaticLogger(Configuration target) {
        if (target.getLoggers().containsKey(PROGRAMMATIC_LOGGER)) {
            return;
        }
        LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                .withLoggerName(PROGRAMMATIC_LOGGER)
                .withLevel(Level.TRACE)
                .withAdditivity(true)
                .withConfig(target)
                .build();
        target.addLogger(PROGRAMMATIC_LOGGER, loggerConfig);
    }
}
