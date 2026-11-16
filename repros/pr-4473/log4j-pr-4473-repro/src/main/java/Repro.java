import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Repro {

    private static final String[] APPENDERS = {"raf", "mmf", "rraf", "file"};

    public static void main(final String[] args) throws Exception {
        final Path logs = Paths.get(System.getProperty("outdir"), "logs");
        final Logger used = LogManager.getLogger("used");
        LogManager.getLogger("never");

        for (final String appender : APPENDERS) {
            expectAbsent("after-startup", logs.resolve(appender + "-never"), appender + (appender.equals("file") ? " (control)" : ""));
            expectAbsent("after-startup", logs.resolve(appender + "-used"), appender + (appender.equals("file") ? " (control)" : ""));
        }

        used.info("first-event");
        LogManager.shutdown();

        for (final String appender : APPENDERS) {
            expectAbsent("after-shutdown", logs.resolve(appender + "-never"), appender + (appender.equals("file") ? " (control)" : ""));
            expectContent(logs.resolve(appender + "-used").resolve("app.log"), appender);
        }
    }

    private static void expectAbsent(final String phase, final Path dir, final String label) {
        final boolean fileExists = Files.exists(dir.resolve("app.log"));
        final String observed = fileExists ? "app.log exists" : Files.exists(dir) ? "no app.log (dir exists)" : "no app.log (no dir)";
        System.out.println((fileExists ? "FAIL " : "PASS ") + phase + " " + label + " " + dir.getFileName()
                + " expected=no app.log observed=" + observed);
    }

    private static void expectContent(final Path file, final String label) throws Exception {
        final String observed = Files.exists(file)
                ? new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\0", "").trim()
                : "<no file>";
        System.out.println((observed.equals("first-event") ? "PASS " : "FAIL ") + "after-event " + label + " "
                + file.getParent().getFileName() + " expected=first-event observed=" + observed);
    }
}
