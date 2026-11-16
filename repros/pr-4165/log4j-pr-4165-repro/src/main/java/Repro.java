import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    static final int ROLLOVERS = 6;
    static final String PAYLOAD = new String(new char[2048]).replace('\0', 'x');

    public static void main(String[] args) throws Exception {
        File root = new File(System.getProperty("logdir"));
        List<Long> delayed = measure(LogManager.getLogger("delayed"), new File(root, "delayed"));
        List<Long> immediate = measure(LogManager.getLogger("immediate"), new File(root, "immediate"));
        long delayedMax = max(delayed);
        long immediateMax = max(immediate);
        System.out.println((delayedMax > 250 && delayedMax <= 2500 ? "PASS " : "FAIL ")
                + "maxRandomDelay=2 compression-delays-ms=" + delayed + " max=" + delayedMax);
        System.out.println((immediateMax < 250 ? "PASS " : "FAIL ")
                + "control-no-delay compression-delays-ms=" + immediate + " max=" + immediateMax);

        Logger delayedLogger = LogManager.getLogger("delayed");
        delayedLogger.info(PAYLOAD);
        long stopStart = System.nanoTime();
        ((LoggerContext) LogManager.getContext(false)).stop();
        long stopMillis = (System.nanoTime() - stopStart) / 1_000_000;
        File dir = new File(root, "delayed");
        int gz = count(dir, ".gz");
        int pendingRaw = count(dir, ".log") - 1;
        System.out.println((gz == ROLLOVERS + 1 && pendingRaw == 0 ? "PASS " : "FAIL ")
                + "stop-right-after-rollover gz=" + gz + " uncompressed-archives=" + pendingRaw + " stop-ms=" + stopMillis
                + " files=" + Arrays.toString(dir.list()));
    }

    static List<Long> measure(Logger logger, File dir) throws Exception {
        List<Long> delays = new ArrayList<>();
        logger.info(PAYLOAD);
        for (int i = 1; i <= ROLLOVERS; i++) {
            long start = System.nanoTime();
            logger.info(PAYLOAD);
            while (count(dir, ".gz") < i) {
                if ((System.nanoTime() - start) / 1_000_000 > 8000) {
                    delays.add(-1L);
                    return delays;
                }
                Thread.sleep(5);
            }
            delays.add((System.nanoTime() - start) / 1_000_000);
        }
        return delays;
    }

    static int count(File dir, String suffix) {
        String[] names = dir.list((d, n) -> n.endsWith(suffix));
        return names == null ? 0 : names.length;
    }

    static long max(List<Long> values) {
        long result = Long.MIN_VALUE;
        for (long value : values) {
            result = Math.max(result, value);
        }
        return result;
    }
}
