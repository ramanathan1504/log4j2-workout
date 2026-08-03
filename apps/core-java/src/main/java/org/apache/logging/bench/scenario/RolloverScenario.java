package org.apache.logging.bench.scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.bench.Scenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Drives enough volume through the configured appenders to trigger rollovers,
 * then reports what landed on disk: which files rolled, which compressed, and how
 * long compression lagged behind the roll.
 *
 * <p>Pair this with the rollover configurations under {@code configs/}, which vary
 * triggering policy (size / time / cron / onStartup), rollover strategy (default /
 * direct-write), compression format, and delete actions. Feature matrix §6.
 */
public final class RolloverScenario implements Scenario {

    private static final Logger log = LogManager.getLogger(RolloverScenario.class);

    /** Enough lines to cross the small size thresholds the rollover configs use. */
    private static final int LINES = Integer.getInteger("bench.rollover.lines", 2_000);

    private static final Path LOG_DIR = Path.of(System.getProperty("bench.log.dir", "logs"));

    @Override
    public String name() {
        return "rollover";
    }

    @Override
    public String describes() {
        return "Drives rollovers (size/time/cron/onStartup), then reports rolled vs compressed files on disk";
    }

    @Override
    public void run() throws IOException, InterruptedException {
        log.info("Writing {} lines to force rollovers", LINES);

        final String filler = "x".repeat(180);
        for (int i = 1; i <= LINES; i++) {
            log.info("rollover line {} of {} — {}", i, LINES, filler);
            if (i % 500 == 0) {
                // Give time-based policies and the compression thread a chance to run
                Thread.sleep(50);
            }
        }

        // Compression happens on a background thread, and configs using
        // maxCompressionDelaySeconds deliberately defer it. Wait before reporting,
        // otherwise the report just races the compressor and reads as a failure.
        final int settleSeconds = Integer.getInteger("bench.rollover.settleSeconds", 8);
        log.info("Waiting {}s for background compression to settle", settleSeconds);
        Thread.sleep(settleSeconds * 1000L);

        report();
    }

    private void report() throws IOException {
        if (!Files.isDirectory(LOG_DIR)) {
            System.out.printf("No log directory at %s — is the active config writing to files?%n",
                    LOG_DIR.toAbsolutePath());
            return;
        }

        try (Stream<Path> walk = Files.walk(LOG_DIR)) {
            final List<Path> files = walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();

            final long compressed = files.stream().filter(RolloverScenario::isCompressed).count();
            final long plain = files.size() - compressed;

            System.out.printf("%nRollover result under %s%n", LOG_DIR.toAbsolutePath());
            System.out.printf("  %d file(s): %d compressed, %d uncompressed%n", files.size(), compressed, plain);

            for (final Path file : files) {
                System.out.printf("    %-58s %8d bytes  %s%n",
                        LOG_DIR.relativize(file),
                        Files.size(file),
                        Files.getLastModifiedTime(file).toInstant());
            }

            if (compressed == 0 && files.size() > 1) {
                System.out.println("  Note: nothing compressed. Either the config has no compression "
                        + "suffix on filePattern, or the delay has not elapsed.");
            }
        }
    }

    private static boolean isCompressed(final Path path) {
        final String name = path.getFileName().toString();
        return name.endsWith(".gz") || name.endsWith(".zip") || name.endsWith(".bz2")
                || name.endsWith(".xz") || name.endsWith(".zst") || name.endsWith(".lz4")
                || name.endsWith(".deflate");
    }
}
