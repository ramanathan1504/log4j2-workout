import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(final String[] args) throws Exception {
        final URI config = Repro.class.getResource("/log4j2.xml").toURI();
        final File dir = new File(System.getProperty("logdir"));
        final long jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();

        final LoggerContext first = new LoggerContext("first", null, config);
        first.start();
        first.getLogger("first").info("written by the first context");

        final Path log = new File(dir, "app.log").toPath();
        final long created = Files.readAttributes(log, BasicFileAttributes.class).creationTime().toMillis();
        final long createdRounded = Math.round(created / 1000d) * 1000;

        final LoggerContext second = new LoggerContext("second", null, config);
        second.start();
        second.getLogger("second").info("written by the second context");
        second.stop();
        first.stop();

        final File archive = new File(dir, "app-1.log");
        final boolean rolled = archive.exists();
        final String archived = rolled ? new String(Files.readAllBytes(archive.toPath())).trim() : "";
        final boolean roundedBelowStart = createdRounded < jvmStart;
        System.out.printf(
                "jvmStartMs=%d fileCreatedMs=%d fileTimeRounded=%d roundedBelowStart=%s rolled=%s archived=[%s]%n",
                jvmStart % 100000, created % 100000, createdRounded % 100000, roundedBelowStart, rolled, archived);
    }
}
