package com.playground;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.CronTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Standalone reproduction harness for Log4j issue #2073.
 *
 * <p>This class intentionally avoids Spring Boot and SLF4J bridge dependencies so it can be
 * compiled directly against remote Maven Central Log4j artifacts such as 2.17.2 and 2.26.0.</p>
 */
public final class Issue2073Standalone {

    private static final String SCHEDULE = "0/1 0/1 * 1/1 * ? *";
    private static final Path LOG_DIR = Paths.get("logs", "issue-2073-standalone");

    private Issue2073Standalone() {
    }

    public static void main(String[] args) throws Exception {
        int iterationsPerWorker = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int workers = args.length > 1 ? Integer.parseInt(args[1]) : 4;

        Files.createDirectories(LOG_DIR);

        LoggerContext loggerContext = new LoggerContext("issue-2073-standalone");
        loggerContext.start(new DefaultConfiguration());
        try {
            Configuration configuration = loggerContext.getConfiguration();

            ExecutorService pool = Executors.newFixedThreadPool(workers);
            try {
                CountDownLatch latch = new CountDownLatch(workers);
                AtomicInteger attempts = new AtomicInteger();
                AtomicInteger successes = new AtomicInteger();
                AtomicReference<Exception> firstFailure = new AtomicReference<>();

                long startedAt = System.nanoTime();
                for (int worker = 0; worker < workers; worker++) {
                    final int workerId = worker;
                    pool.submit(() -> {
                        try {
                            for (int i = 0; i < iterationsPerWorker && firstFailure.get() == null; i++) {
                                RollingFileAppender appender = null;
                                try {
                                    attempts.incrementAndGet();
                                    alignNearNextSecondBoundary();
                                    appender = buildAppender(configuration, workerId, i);
                                    appender.start();
                                    successes.incrementAndGet();
                                } catch (Exception e) {
                                    firstFailure.compareAndSet(null, e);
                                } finally {
                                    stopQuietly(appender);
                                }
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await();
                pool.shutdownNow();
                pool.awaitTermination(5, TimeUnit.SECONDS);

                Exception failure = firstFailure.get();
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                String version = LoggerContext.class.getPackage().getImplementationVersion();

                System.out.println("issue=2073");
                System.out.println("log4jVersion=" + version);
                System.out.println("schedule=" + SCHEDULE);
                System.out.println("workers=" + workers);
                System.out.println("iterationsPerWorker=" + iterationsPerWorker);
                System.out.println("attempts=" + attempts.get());
                System.out.println("successfulBuilds=" + successes.get());
                System.out.println("durationMs=" + durationMs);
                System.out.println("logDir=" + LOG_DIR.toAbsolutePath());

                if (failure == null) {
                    System.out.println("status=not-reproduced");
                    return;
                }

                System.out.println("status=" + (isIssue2073Failure(failure) ? "reproduced" : "failed-with-other-exception"));
                System.out.println("exceptionType=" + failure.getClass().getName());
                System.out.println("message=" + failure.getMessage());
                for (String line : stackTraceLines(failure, 20)) {
                    System.out.println(line);
                }
            } finally {
                if (!pool.isShutdown()) {
                    pool.shutdownNow();
                }
            }
        } finally {
            loggerContext.stop(5, TimeUnit.SECONDS);
        }
    }

    private static RollingFileAppender buildAppender(Configuration configuration, int workerId, int iteration) {
        String appenderId = "issue-2073-" + workerId + '-' + iteration + '-' + System.nanoTime();
        Path activeFile = LOG_DIR.resolve(appenderId + ".log");
        Path filePattern = LOG_DIR.resolve(appenderId + "-%d{yyyy-MM-dd-HH-mm-ss}-%i.log");

        PatternLayout layout = PatternLayout.newBuilder()
                .withConfiguration(configuration)
                .withPattern("%d{ISO8601} %-5level %logger - %msg%n")
                .build();

        TriggeringPolicy policy = CompositeTriggeringPolicy.createPolicy(
                CronTriggeringPolicy.createPolicy(configuration, "false", SCHEDULE),
                SizeBasedTriggeringPolicy.createPolicy("10 MB")
        );

        DefaultRolloverStrategy strategy = DefaultRolloverStrategy.newBuilder()
                .withConfig(configuration)
                .withMax("2")
                .build();

        return RollingFileAppender.newBuilder()
                .withName(appenderId)
                .withConfiguration(configuration)
                .withFileName(activeFile.toString())
                .withFilePattern(filePattern.toString())
                .withPolicy(policy)
                .withStrategy(strategy)
                .withLayout(layout)
                .withBufferedIo(true)
                .withBufferSize(256)
                .withImmediateFlush(true)
                .withIgnoreExceptions(false)
                .build();
    }

    private static void alignNearNextSecondBoundary() {
        long remainder = System.currentTimeMillis() % 1_000L;
        long target = 975L;
        if (remainder < target) {
            try {
                Thread.sleep(target - remainder);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void stopQuietly(RollingFileAppender appender) {
        if (appender == null) {
            return;
        }
        try {
            appender.stop();
        } catch (Exception ignored) {
            // best effort cleanup for stress runs
        }
    }

    private static boolean isIssue2073Failure(Throwable throwable) {
        if (!(throwable instanceof NullPointerException)) {
            return false;
        }
        return stackTraceLines(throwable, 30).stream()
                .anyMatch(line -> line.contains("ConfigurationScheduler$CronRunnable.toString")
                        || line.contains("ConfigurationScheduler.toString")
                        || line.contains("CronTriggeringPolicy.initialize"));
    }

    private static List<String> stackTraceLines(Throwable throwable, int maxLines) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        String[] lines = writer.toString().split("\\R");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.length && i < maxLines; i++) {
            result.add(lines[i]);
        }
        return result;
    }
}

