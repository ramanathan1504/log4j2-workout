import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Probe {

    public static void main(final String[] args) throws InterruptedException {
        final ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(new NamedTask("first-job", 3));
        executorService.submit(new NamedTask("second-job", 8));
        executorService.shutdown();
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Executor did not terminate in time");
        }
        LogManager.shutdown();
    }

    private static final class NamedTask implements Runnable {
        private static final Logger LOGGER = LogManager.getLogger(NamedTask.class);
        private final String taskName;
        private final int priority;

        private NamedTask(final String taskName, final int priority) {
            this.taskName = taskName;
            this.priority = priority;
        }

        @Override
        public void run() {
            final Thread thread = Thread.currentThread();
            final String originalName = thread.getName();
            final int originalPriority = thread.getPriority();
            try {
                thread.setName(taskName);
                thread.setPriority(priority);
                LOGGER.info("actual name={} priority={}", thread.getName(), thread.getPriority());
            } finally {
                thread.setName(originalName);
                thread.setPriority(originalPriority);
            }
        }
    }
}
