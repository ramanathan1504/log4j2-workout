package repro;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.apache.logging.log4j.core.lookup.Interpolator;
import org.apache.logging.log4j.core.lookup.StrLookup;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class App {

    private static final Logger LOG = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        System.out.println("RESULT boot=" + SpringBootVersion.getVersion() + " log4j=" + LoggerContext.class.getPackage().getImplementationVersion()
                + " log4j-spring-boot=" + present("org.apache.logging.log4j.spring.boot.SpringLookup"));
        ConfigurableApplicationContext ctx;
        try {
            ctx = SpringApplication.run(App.class, args);
        }
        catch (Throwable ex) {
            Throwable root = ex;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            System.out.println("RESULT startup=FAILED " + root.getClass().getName() + ": " + root.getMessage());
            System.exit(1);
            return;
        }
        System.out.println("RESULT startup=OK loggingSystem=" + ctx.getBean(LoggingSystem.class).getClass().getName());
        LOG.info("probe");
        LoggerContext lc = (LoggerContext) LogManager.getContext(false);
        StrLookup resolver = lc.getConfiguration().getStrSubstitutor().getVariableResolver();
        if (resolver instanceof Interpolator interpolator) {
            Map<String, StrLookup> map = interpolator.getStrLookupMap();
            System.out.println("RESULT lookup spring -> " + (map.get("spring") == null ? "none" : map.get("spring").getClass().getName()));
        }
        PluginManager pm = new PluginManager("Core");
        pm.collectPlugins();
        PluginType<?> arbiter = pm.getPluginType("springprofile");
        System.out.println("RESULT arbiter SpringProfile -> " + (arbiter == null ? "none" : arbiter.getPluginClass().getName()));
        System.out.println("RESULT property log4j2.reproProbe from application.properties -> " + PropertiesUtil.getProperties().getStringProperty("log4j2.reproProbe"));
        ctx.close();
    }

    private static boolean present(String name) {
        try {
            Class.forName(name);
            return true;
        }
        catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
