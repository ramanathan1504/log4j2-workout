import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;

public class CustomConfigurationFactory extends ConfigurationFactory {
    @Override
    protected String[] getSupportedTypes() {
        return new String[] {".xml"};
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
        return new CustomXmlConfiguration(loggerContext, source);
    }
}
