import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;

public final class Repro {

    private static final String[] CASES = {"gz", "zst", "zstLevel", "bz2", "deflate", "xz"};
    private static final int SEGMENT = 50;
    private static final int ROLLOVERS = 2;
    private static final int LINES = SEGMENT * (ROLLOVERS + 1);

    public static void main(final String[] args) throws Exception {
        final File out = new File(args[0]);
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final java.util.List<String> present = new java.util.ArrayList<>();
        for (final String name : CASES) {
            final RollingFileAppender appender = ctx.getConfiguration().getAppender(name);
            if (appender == null) {
                continue;
            }
            present.add(name);
            final Logger logger = LogManager.getLogger(name);
            int n = 0;
            for (int r = 0; r <= ROLLOVERS; r++) {
                if (r > 0) {
                    appender.getManager().rollover();
                    Thread.sleep(500);
                }
                for (int i = 0; i < SEGMENT; i++) {
                    logger.info(String.format("%s-line-%04d", name, ++n));
                }
            }
        }
        ctx.stop(30, TimeUnit.SECONDS);
        System.out.println("commons-compress " + commonsCompressVersion());
        for (final String name : present) {
            report(name, new File(out, name));
        }
    }

    private static String commonsCompressVersion() {
        final Package p = CompressorStreamFactory.class.getPackage();
        final String v = p == null ? null : p.getImplementationVersion();
        return v == null ? "unknown" : v;
    }

    private static void report(final String name, final File dir) {
        final Set<String> seen = new HashSet<>();
        final StringBuilder files = new StringBuilder();
        String archiveError = null;
        int archives = 0;
        int leftovers = 0;
        final File[] list = dir.listFiles();
        if (list != null) {
            Arrays.sort(list);
            for (final File f : list) {
                files.append(' ').append(f.getName()).append('=').append(f.length()).append('B');
                if (!f.getName().equals("app.log")) {
                    if (f.getName().endsWith(".log")) {
                        leftovers++;
                    } else if (f.length() > 0) {
                        archives++;
                    }
                }
                try {
                    collect(f, seen);
                } catch (final Exception e) {
                    archiveError = f.getName() + ": " + e.getClass().getSimpleName();
                }
            }
        }
        int recovered = 0;
        for (int i = 1; i <= LINES; i++) {
            if (seen.contains(String.format("%s-line-%04d", name, i))) {
                recovered++;
            }
        }
        final int lost = LINES - recovered;
        final boolean ok = lost == 0 && archiveError == null && archives == ROLLOVERS && leftovers == 0;
        System.out.printf(
                "%s %-8s recovered=%d/%d lost=%d nonEmptyArchives=%d/%d uncompressedLeftovers=%d%s files:%s%n",
                ok ? "PASS" : "FAIL",
                name,
                recovered,
                LINES,
                lost,
                archives,
                ROLLOVERS,
                leftovers,
                archiveError == null ? "" : " unreadable=" + archiveError,
                files);
    }

    private static void collect(final File f, final Set<String> seen) throws Exception {
        try (InputStream raw = new FileInputStream(f);
                InputStream in = open(f.getName(), raw);
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                seen.add(line);
            }
        }
    }

    private static InputStream open(final String fileName, final InputStream raw) throws Exception {
        if (fileName.endsWith(".log")) {
            return raw;
        }
        if (fileName.endsWith(".gz")) {
            return new GZIPInputStream(raw);
        }
        if (fileName.endsWith(".zst") || fileName.endsWith(".zstd")) {
            return new com.github.luben.zstd.ZstdInputStream(raw);
        }
        if (fileName.endsWith(".bz2") || fileName.endsWith(".bzip2")) {
            return new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.BZIP2, raw);
        }
        if (fileName.endsWith(".deflate")) {
            return new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.DEFLATE, raw);
        }
        if (fileName.endsWith(".xz")) {
            return new org.tukaani.xz.XZInputStream(raw);
        }
        throw new IOException("unknown extension");
    }
}
