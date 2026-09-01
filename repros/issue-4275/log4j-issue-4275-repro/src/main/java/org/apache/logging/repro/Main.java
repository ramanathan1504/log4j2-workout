package org.apache.logging.repro;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Main {

    public static void main(final String[] args) throws InterruptedException {
        final ExecutorService executorService = Executors.newFixedThreadPool(1);

        executorService.submit(new NamedTask("first-job"));
        executorService.submit(new NamedTask("second-job"));

        executorService.shutdown();

        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Executor did not terminate in time");
        }

        LogManager.shutdown();
    }

    private static final class NamedTask implements Runnable {

        private static final Logger LOGGER = LogManager.getLogger(NamedTask.class);

        private final String taskName;

        private NamedTask(final String taskName) {
            this.taskName = taskName;
        }

        @Override
        public void run() {
            final Thread thread = Thread.currentThread();
            final String originalThreadName = thread.getName();

            try {
                thread.setName(taskName);
                LOGGER.info("Executing {}", taskName);
            } finally {
                thread.setName(originalThreadName);
            }
        }
    }
}
