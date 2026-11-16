import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.spring.cloud.config.client.Log4j2EventListener;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

public class Repro {

    private static void writeConfig(final File file, final Level level, final long modifiedAt) throws Exception {
        final String xml = "<Configuration status=\"WARN\" monitorInterval=\"3600\">"
                + "<Appenders><Console name=\"OUT\"/></Appenders>"
                + "<Loggers><Root level=\"" + level + "\"><AppenderRef ref=\"OUT\"/></Root></Loggers>"
                + "</Configuration>";
        Files.write(file.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        file.setLastModified(modifiedAt);
    }

    private static Level waitForRootLevel(final Level wanted) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 3000;
        Level level = LogManager.getRootLogger().getLevel();
        while (!level.equals(wanted) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            level = LogManager.getRootLogger().getLevel();
        }
        return level;
    }

    private static void refresh(final String watchEnabled) {
        final StandardEnvironment environment = new StandardEnvironment();
        final Map<String, Object> properties =
                Collections.singletonMap("spring.cloud.config.watch.enabled", watchEnabled);
        environment.getPropertySources().addFirst(new MapPropertySource("repro", properties));
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            context.addApplicationListener(new Log4j2EventListener());
            context.refresh();
            context.publishEvent(new EnvironmentChangeEvent(context, Collections.singleton("logging.level.root")));
        }
    }

    public static void main(final String[] args) throws Exception {
        final File config = new File(args[0]);
        final long start = System.currentTimeMillis();
        System.out.println("initial root level=" + waitForRootLevel(Level.INFO));

        writeConfig(config, Level.DEBUG, start + 10_000);
        refresh("false");
        final Level afterDisabled = waitForRootLevel(Level.DEBUG);
        final boolean ignored = afterDisabled.equals(Level.INFO);
        System.out.println((ignored ? "PASS" : "FAIL")
                + " watch-disabled-refresh-does-not-reload expected=INFO observed=" + afterDisabled);

        writeConfig(config, Level.TRACE, start + 20_000);
        refresh("true");
        final Level afterEnabled = waitForRootLevel(Level.TRACE);
        System.out.println((afterEnabled.equals(Level.TRACE) ? "PASS" : "FAIL")
                + " control-watch-enabled-refresh-reloads expected=TRACE observed=" + afterEnabled);
    }
}
