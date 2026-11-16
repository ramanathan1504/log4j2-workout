package repro;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.plugins.Namespace;
import org.apache.logging.log4j.plugins.Ordered;
import org.apache.logging.log4j.plugins.Plugin;

@Namespace(ConfigurationFactory.NAMESPACE)
@Plugin("ReproXmlConfigurationFactory")
@Ordered(10)
public class ReproXmlConfigurationFactory extends ConfigurationFactory {

    public static volatile boolean used;

    @Override
    protected String[] getSupportedTypes() {
        return new String[] {".xml"};
    }

    @Override
    public Configuration getConfiguration(final LoggerContext loggerContext, final ConfigurationSource source) {
        used = true;
        return new XmlConfiguration(loggerContext, source);
    }
}
