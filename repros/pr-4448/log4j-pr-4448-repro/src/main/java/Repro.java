import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Repro {

    private static final String PADDING = "the quick brown fox jumps over the lazy dog ";

    public static void main(final String[] args) throws Exception {
        final Logger logger = LogManager.getLogger(Repro.class);
        for (int i = 0; i < 600; i++) {
            logger.info(String.format("line %05d %s%s", i, PADDING, PADDING));
        }
        LogManager.shutdown();

        final Path logs = Paths.get(System.getProperty("outdir"), "logs");
        report(logs, "default-gz-level0", true);
        report(logs, "default-gz-level0-temp", true);
        report(logs, "default-zip-level0", true);
        report(logs, "direct-gz-level0", true);
        report(logs, "direct-gz-level0-temp", true);
        report(logs, "default-gz-level9", false);
        report(logs, "default-gz-unset", false);
        report(logs, "default-gz-level42", false);
    }

    private static void report(final Path logs, final String name, final boolean expectStored) throws Exception {
        final Path archive = waitForArchive(logs.resolve(name));
        if (archive == null) {
            System.out.println("FAIL " + name + " observed=<no archive>");
            return;
        }
        final long compressed = Files.size(archive);
        final long raw = uncompressedSize(archive);
        final double ratio = raw == 0 ? 0 : (double) compressed / raw;
        final boolean pass = expectStored ? ratio >= 0.9 : ratio < 0.5;
        System.out.printf("%s %s archive=%s expected=%s observed=compressed:%d raw:%d ratio:%.3f%s%n",
                pass ? "PASS" : "FAIL", name, archive.getFileName(), expectStored ? "ratio>=0.9 (level 0, stored)" : "ratio<0.5",
                compressed, raw, ratio, expectStored ? "" : " (control)");
    }

    private static Path waitForArchive(final Path dir) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "app-*.log.{gz,zip}")) {
                for (final Path file : files) {
                    return file;
                }
            } catch (final IOException ignored) {
            }
            Thread.sleep(200);
        }
        return null;
    }

    private static long uncompressedSize(final Path archive) throws IOException {
        try (InputStream in = Files.newInputStream(archive)) {
            if (archive.toString().endsWith(".zip")) {
                try (ZipInputStream zip = new ZipInputStream(in)) {
                    long total = 0;
                    while (zip.getNextEntry() != null) {
                        total += drain(zip);
                    }
                    return total;
                }
            }
            try (GZIPInputStream gzip = new GZIPInputStream(in)) {
                return drain(gzip);
            }
        }
    }

    private static long drain(final InputStream in) throws IOException {
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            sink.write(buffer, 0, read);
        }
        return sink.size();
    }
}
