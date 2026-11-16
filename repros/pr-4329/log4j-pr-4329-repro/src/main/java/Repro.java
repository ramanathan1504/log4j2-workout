import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Reconfigurable;
import org.apache.logging.log4j.core.config.composite.CompositeConfiguration;

public class Repro {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        boolean factory = "factory".equals(args[1]);
        if (factory) {
            ConfigurationFactory.setConfigurationFactory(new ProgrammaticConfigurationFactory());
        }
        String caseName = mode + (factory ? "+factory" : "+no-factory");
        Configuration configuration = "composite".equals(mode)
                ? new CompositeConfiguration(Arrays.asList(load("base.xml"), load("overlay.xml")))
                : load("base.xml");
        LoggerContext context = Configurator.initialize(Repro.class.getClassLoader(), configuration);
        report(caseName + " initial", context);
        Configuration rebuilt = ((Reconfigurable) context.getConfiguration()).reconfigure();
        context.setConfiguration(rebuilt);
        report(caseName + " after reconfigure", context);
        Configurator.shutdown(context);
    }

    private static AbstractConfiguration load(String resource) throws Exception {
        ConfigurationSource source = ConfigurationSource.fromResource(resource, Repro.class.getClassLoader());
        return new ProgrammaticXmlConfiguration(null, source);
    }

    private static void report(String caseName, LoggerContext context) {
        Configuration active = context.getConfiguration();
        boolean present = active.getLoggers().containsKey(ProgrammaticXmlConfiguration.PROGRAMMATIC_LOGGER);
        boolean traceEnabled = context.getLogger(ProgrammaticXmlConfiguration.PROGRAMMATIC_LOGGER).isTraceEnabled();
        boolean xmlLoggerDebug = context.getLogger("from.xml").isDebugEnabled();
        List<String> loggers = new ArrayList<>(active.getLoggers().keySet());
        System.out.println((present && traceEnabled ? "PASS " : "FAIL ") + caseName + ": config="
                + active.getClass().getSimpleName() + " loggers=" + loggers + " programmatic.trace=" + traceEnabled
                + " from.xml.debug=" + xmlLoggerDebug);
    }
}
