import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;

public class NoReconfigureXmlConfiguration extends XmlConfiguration {
    public NoReconfigureXmlConfiguration(LoggerContext context, ConfigurationSource source) {
        super(context, source);
    }

    @Override
    public void setup() {
        super.setup();
        Marks.addSetupNode(getRootNode());
    }

    @Override
    protected void doConfigure() {
        super.doConfigure();
        Marks.addLogger(this);
    }
}
