import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ClockCase {

    public static String run(final String logdir) throws Exception {
        final Logger logger = LogManager.getLogger("clock");
        final long[] script = {-1L, -1L, 1L, -1L};
        for (final long now : script) {
            ScriptedClock.now = now;
            logger.info("m");
        }
        final List<String> lines = Files.readAllLines(Paths.get(logdir, "clock.log"), StandardCharsets.UTF_8);
        final List<String> stamps = new ArrayList<>();
        for (final String line : lines) {
            if (!line.isEmpty()) {
                stamps.add(line.split(" ")[1]);
            }
        }
        return String.join(",", stamps);
    }
}
