import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ClockCase {

    public static String run(final String logdir) throws Exception {
        final Logger logger = LogManager.getLogger("clock");
        final long[] script = {-1L, -1L, 0L, -1L};
        final String[] names = {"a", "b", "c", "d"};
        final Thread t = new Thread(() -> {
            for (int i = 0; i < script.length; i++) {
                ScriptedClock.now = script[i];
                logger.info(names[i]);
            }
        });
        t.start();
        t.join();
        final List<String> lines = Files.readAllLines(Paths.get(logdir, "clock.log"), StandardCharsets.UTF_8);
        return String.join(",", lines);
    }
}
