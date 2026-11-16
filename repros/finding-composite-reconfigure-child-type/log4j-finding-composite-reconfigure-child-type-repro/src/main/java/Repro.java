import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    private static File dir;

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        dir = new File(args[1], mode);
        dir.mkdirs();
        for (String name : new String[] {"base.xml", "overlay.xml"}) {
            try (InputStream in = Repro.class.getClassLoader().getResourceAsStream(name)) {
                Files.copy(in, new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Class<?> expected = CustomXmlConfiguration.class;
        Configuration configuration;
        switch (mode) {
            case "single":
                configuration = load("base.xml", false);
                break;
            case "single-no-override":
                expected = NoReconfigureXmlConfiguration.class;
                configuration = load("base.xml", true);
                break;
            case "composite-factory":
                ConfigurationFactory.setConfigurationFactory(new CustomConfigurationFactory());
                configuration = composite();
                break;
            default:
                configuration = composite();
        }
        LoggerContext context = Configurator.initialize(Repro.class.getClassLoader(), configuration);
        report(mode, "initial", context, expected);
        Configuration before = context.getConfiguration();
        if ("composite-monitor".equals(mode)) {
            Thread.sleep(1500);
            File overlay = new File(dir, "overlay.xml");
            String text = new String(Files.readAllBytes(overlay.toPath()), StandardCharsets.UTF_8);
            Files.write(overlay.toPath(), text.replace("level=\"debug\"", "level=\"warn\"").getBytes(StandardCharsets.UTF_8));
            long deadline = System.currentTimeMillis() + 15000;
            while (context.getConfiguration() == before && System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
            }
            report(mode, "after file touch", context, expected);
        } else if ("composite-ctx".equals(mode)) {
            context.reconfigure();
            report(mode, "after LoggerContext.reconfigure()", context, expected);
        } else {
            context.onChange((Reconfigurable) before);
            report(mode, "after onChange(config)", context, expected);
        }
        Configurator.shutdown(context);
    }

    private static Configuration composite() throws Exception {
        return new CompositeConfiguration(Arrays.asList(load("base.xml", false), load("overlay.xml", false)));
    }

    private static AbstractConfiguration load(String name, boolean noOverride) throws Exception {
        File file = new File(dir, name);
        ConfigurationSource source = new ConfigurationSource(new FileInputStream(file), file);
        return noOverride ? new NoReconfigureXmlConfiguration(null, source) : new CustomXmlConfiguration(null, source);
    }

    private static List<String> children(Configuration active) throws Exception {
        List<String> names = new ArrayList<>();
        if (active instanceof CompositeConfiguration) {
            Field field = CompositeConfiguration.class.getDeclaredField("configurations");
            field.setAccessible(true);
            for (Object child : (List<?>) field.get(active)) {
                names.add(child.getClass().getSimpleName());
            }
        } else {
            names.add(active.getClass().getSimpleName());
        }
        return names;
    }

    private static void report(String mode, String checkpoint, LoggerContext context, Class<?> expected) throws Exception {
        Configuration active = context.getConfiguration();
        List<String> kids = children(active);
        boolean typesKept = !kids.isEmpty();
        for (String kid : kids) {
            typesKept &= kid.equals(expected.getSimpleName());
        }
        boolean setupTrace = context.getLogger(Marks.SETUP_LOGGER).isTraceEnabled();
        boolean doConfigurePresent = active.getLoggers().containsKey(Marks.DO_CONFIGURE_LOGGER);
        String common = " config=" + active.getClass().getSimpleName() + " children=" + kids
                + " loggers=" + new ArrayList<>(active.getLoggers().keySet())
                + " from.xml.level=" + context.getLogger("from.xml").getLevel();
        System.out.println((typesKept && setupTrace ? "PASS " : "FAIL ") + mode + " " + checkpoint
                + " [type+setup]: expected children=" + expected.getSimpleName() + " from.setup.trace=true; observed"
                + " from.setup.trace=" + setupTrace + common);
        System.out.println((doConfigurePresent ? "PASS " : "FAIL ") + mode + " " + checkpoint
                + " [doConfigure]: expected 'programmatic' logger; observed present=" + doConfigurePresent + common);
    }
}
