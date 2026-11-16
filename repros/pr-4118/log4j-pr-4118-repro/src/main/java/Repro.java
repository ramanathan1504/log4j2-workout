import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.core.config.ConfigurationScheduler;
import org.apache.logging.log4j.core.util.CronExpression;

public class Repro {

    public static void main(final String[] args) throws Exception {
        final AtomicInteger runs = new AtomicInteger();
        final CountDownLatch firstRun = new CountDownLatch(1);
        final AtomicBoolean firstRunObserved = new AtomicBoolean();
        final Runnable command = () -> {
            runs.incrementAndGet();
            firstRun.countDown();
        };
        final ConfigurationScheduler scheduler = new ConfigurationScheduler() {
            private final AtomicBoolean first = new AtomicBoolean(true);

            @Override
            public ScheduledFuture<?> schedule(final Runnable cmd, final long delay, final TimeUnit unit) {
                if (first.compareAndSet(true, false)) {
                    final ScheduledFuture<?> future = super.schedule(cmd, 0, TimeUnit.MILLISECONDS);
                    try {
                        firstRunObserved.set(firstRun.await(3, TimeUnit.SECONDS));
                        Thread.sleep(100);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return future;
                }
                return super.schedule(cmd, delay, unit);
            }
        };
        scheduler.incrementScheduledItems();
        scheduler.start();
        final ScheduledFuture<?> future =
                scheduler.scheduleWithCron(new CronExpression("0/1 * * * * ?"), new Date(), command);
        final String firstFireOutcome = firstRunObserved.get() ? "ran" : "did-not-run-within-3s";
        future.cancel(false);
        final int runsAtCancel = runs.get();
        Thread.sleep(2500);
        final int runsAfterCancel = runs.get() - runsAtCancel;
        final String description = future.toString();
        scheduler.stop(1, TimeUnit.SECONDS);
        verdict(firstRunObserved.get(), "first cron fire runs the command", firstFireOutcome);
        verdict(runsAfterCancel == 0, "no run after cancel() within 2.5s", "runsAfterCancel=" + runsAfterCancel);
        System.out.println("INFO total-runs=" + runs.get() + " future=" + description);
    }

    private static void verdict(final boolean ok, final String name, final String observed) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + " observed=" + observed);
    }
}
