import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class Repro {
    public static void main(String[] args) throws Exception {
        LoggerContext context = (LoggerContext) LogManager.getContext(Repro.class.getClassLoader(), false);
        LogManager.getLogger("com.example").debug("before");
        String constructorState;
        try {
            Constructor<LoggerConfig> springBootConstructor =
                    LoggerConfig.class.getConstructor(String.class, Level.class, boolean.class);
            LoggerConfig levelSet = springBootConstructor.newInstance("com.example", Level.DEBUG, true);
            context.getConfiguration().addLogger("com.example", levelSet);
            context.updateLoggers();
            constructorState = "present";
        } catch (NoSuchMethodException e) {
            constructorState = "NoSuchMethodException";
        } catch (ReflectiveOperationException | RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            constructorState = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        LogManager.getLogger("com.example").debug("after");
        LogManager.getLogger("other").info("control");
        LogManager.shutdown();
        List<String> lines = Files.readAllLines(Paths.get(System.getProperty("outdir"), "app.log"));
        System.out.println((constructorState.equals("present") ? "PASS " : "FAIL ")
                + "LoggerConfig(String, Level, boolean) expected=present observed=" + constructorState);
        boolean debugAfter = lines.contains("DEBUG com.example after");
        System.out.println((debugAfter ? "PASS " : "FAIL ")
                + "DEBUG com.example after addLogger expected=written observed=" + (debugAfter ? "written" : "absent"));
        boolean debugBefore = lines.contains("DEBUG com.example before");
        boolean control = lines.contains("INFO other control");
        System.out.println((!debugBefore && control ? "PASS " : "FAIL ") + "control before=" + debugBefore
                + " other-info=" + control);
    }
}
