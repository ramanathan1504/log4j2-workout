import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Repro {
    public static void main(final String[] args) throws InterruptedException {
        final Logger logger = LogManager.getLogger(Repro.class);
        for (int i = 0; i < 40; i++) {
            logger.info("line {} padding padding padding padding padding padding", i);
        }
        Thread.sleep(3000);
        final File[] archives = new File(System.getProperty("logdir")).listFiles((dir, name) -> name.endsWith(".gz"));
        System.out.println("archives=" + (archives == null ? 0 : archives.length));
        final List<String> lingering = new ArrayList<>();
        for (final Thread thread : Thread.getAllStackTraces().keySet()) {
            if (!thread.isDaemon() && thread.isAlive() && thread != Thread.currentThread()
                    && !"DestroyJavaVM".equals(thread.getName())) {
                lingering.add(thread.getName());
            }
        }
        System.out.println("non-daemon threads 3s after the rollover=" + lingering);
        System.out.println("main returning");
    }
}
