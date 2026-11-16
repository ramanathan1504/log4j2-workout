import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    static final int ROUNDS = 5;

    public static void main(String[] args) throws Exception {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        File logs = new File(System.getProperty("outdir"), "logs");
        List<Long> delayedLatencies = measure(LogManager.getLogger("delayed"), new File(logs, "delayed"));
        List<Long> successorLatencies = measure(LogManager.getLogger("successor"), new File(logs, "successor"));
        List<Long> immediateLatencies = measure(LogManager.getLogger("immediate"), new File(logs, "immediate"));
        context.stop();
        long delayedMax = Collections.max(delayedLatencies);
        long successorMax = Collections.max(successorLatencies);
        long immediateMax = Collections.max(immediateLatencies);
        boolean delayApplied = delayedMax >= 900 && delayedMax <= 3500;
        System.out.println((delayApplied ? "PASS" : "FAIL") + " maxCompressionDelaySeconds=2 rollover-to-gz latencies ms=" + delayedLatencies + " max=" + delayedMax);
        System.out.println((successorMax >= 900 && successorMax <= 3500 ? "PASS" : "FAIL") + " successor #4165 RollingFile maxRandomDelay=2 latencies ms=" + successorLatencies + " max=" + successorMax);
        System.out.println((immediateMax < 900 ? "PASS" : "FAIL") + " control no delay attribute latencies ms=" + immediateLatencies + " max=" + immediateMax);
        System.out.println(verifyArchives(new File(logs, "delayed")) + " delayed archives decompress");
        System.out.println(verifyArchives(new File(logs, "successor")) + " successor archives decompress");
        System.out.println(verifyArchives(new File(logs, "immediate")) + " control archives decompress");
    }

    static List<Long> measure(Logger logger, File dir) throws InterruptedException {
        char[] filler = new char[1100];
        Arrays.fill(filler, 'x');
        String payload = new String(filler);
        List<Long> latencies = new ArrayList<>();
        for (int round = 1; round <= ROUNDS; round++) {
            logger.info("payload round={} {}", round, payload);
            long start = System.nanoTime();
            logger.info("trigger round={}", round);
            while (countArchives(dir) < round && System.nanoTime() - start < 10_000_000_000L) {
                Thread.sleep(10);
            }
            latencies.add((System.nanoTime() - start) / 1_000_000);
        }
        return latencies;
    }

    static int countArchives(File dir) {
        String[] names = dir.list((d, name) -> name.endsWith(".gz"));
        return names == null ? 0 : names.length;
    }

    static String verifyArchives(File dir) throws Exception {
        File[] archives = dir.listFiles((d, name) -> name.endsWith(".gz"));
        int valid = 0;
        for (File archive : archives) {
            try (InputStream in = new GZIPInputStream(new FileInputStream(archive))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                if (new String(out.toByteArray(), StandardCharsets.UTF_8).contains("payload round=")) {
                    valid++;
                }
            }
        }
        return (valid == ROUNDS && archives.length == ROUNDS ? "PASS" : "FAIL") + " archives=" + archives.length + " valid=" + valid;
    }
}
