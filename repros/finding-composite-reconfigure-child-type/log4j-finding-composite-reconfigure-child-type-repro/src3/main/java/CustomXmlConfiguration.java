import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;

public class CustomXmlConfiguration extends NoReconfigureXmlConfiguration {
    public CustomXmlConfiguration(LoggerContext context, ConfigurationSource source) {
        super(context, source);
    }

    @Override
    public Configuration reconfigure() {
        try {
            return new CustomXmlConfiguration(getLoggerContext(), getConfigurationSource().resetInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
