import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Repro {

    static final int EVENTS = 200;

    public static void main(String[] args) throws Exception {
        Path outdir = Paths.get(System.getProperty("outdir"));
        Path archive = Files.createDirectories(outdir.resolve("archive"));
        Files.setPosixFilePermissions(archive, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            Logger logger = LogManager.getLogger(Repro.class);
            for (int seq = 0; seq < EVENTS; seq++) {
                logger.info(String.format("event seq=%04d padding-to-forty-bytes-xxxxxxx", seq));
            }
            LogManager.shutdown();
        } finally {
            Files.setPosixFilePermissions(archive, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        List<String> active = Files.readAllLines(outdir.resolve("app.log"), StandardCharsets.UTF_8);
        long archived;
        try (Stream<Path> files = Files.list(archive)) {
            archived = files.count();
        }
        long written = active.stream().filter(line -> line.startsWith("event seq=")).count();
        System.out.println((archived == 0 ? "PASS " : "FAIL ") + "rollover-blocked-control archived-files=" + archived);
        System.out.println((written == EVENTS ? "PASS " : "FAIL ") + "events-kept-after-failed-rollover written="
                + written + "/" + EVENTS + " last=" + (active.isEmpty() ? "<empty>" : active.get(active.size() - 1)));
    }
}
