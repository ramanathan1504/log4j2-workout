package org.apache.logging.repro;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    static final int THREADS = 16;
    static final int LINES = 50_000;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("log4j-loss");
        Path log = dir.resolve("test.log");
        Path config = dir.resolve("log4j2.xml");
        Files.writeString(config, """
                <Configuration status="WARN">
                  <Appenders>
                    <File name="file" fileName="%s">
                      <PatternLayout pattern="%%d %%-5p [%%t] %%c - %%m%%n"/>
                    </File>
                  </Appenders>
                  <Loggers>
                    <Root level="INFO"><AppenderRef ref="file"/></Root>
                  </Loggers>
                </Configuration>
                """.formatted(log.toAbsolutePath().toString().replace('\\', '/')));
        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
        System.setProperty("log4j2.configurationFile", config.toUri().toString());

        Logger logger = LogManager.getLogger("test");
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            int thread = t;
            Thread th = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < LINES; i++) {
                    logger.info("LINE {} {} padding padding padding padding padding padding padding padding", thread, i);
                }
            }, "producer-" + t);
            th.start();
            threads.add(th);
        }
        long startNanos = System.nanoTime();
        start.countDown();
        for (Thread th : threads) {
            th.join();
        }
        long producedMs = (System.nanoTime() - startNanos) / 1_000_000;
        LogManager.shutdown();

        BitSet[] seen = new BitSet[THREADS];
        for (int t = 0; t < THREADS; t++) {
            seen[t] = new BitSet(LINES);
        }
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            int at = line.indexOf(" - LINE ");
            if (at < 0) {
                continue;
            }
            String[] fields = line.substring(at + 8).split(" ", 3);
            seen[Integer.parseInt(fields[0])].set(Integer.parseInt(fields[1]));
        }
        long expected = (long) THREADS * LINES;
        long found = 0;
        for (BitSet bits : seen) {
            found += bits.cardinality();
        }
        String disruptor = com.lmax.disruptor.RingBuffer.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String core = org.apache.logging.log4j.core.LoggerContext.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        System.out.printf("%s + %s: %d threads logged %d lines in %d ms, %d in the file, %d missing (%.1f%%)%n",
                core.substring(core.lastIndexOf('/') + 1),
                disruptor.substring(disruptor.lastIndexOf('/') + 1), THREADS, expected, producedMs, found,
                expected - found, 100.0 * (expected - found) / expected);
    }
}
