import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Repro {
    public static void main(final String[] args) throws Exception {
        final String run = args[0];
        final long end = System.currentTimeMillis() + Long.parseLong(args[1]) * 1000L;
        final Logger logger = LogManager.getLogger(Repro.class);
        int seq = 0;
        while (System.currentTimeMillis() < end) {
            logger.info("run={} seq={}", run, seq++);
            Thread.sleep(250);
        }
        LogManager.shutdown();
        System.out.println("logged run=" + run + " lines=" + seq);
    }
}
