import org.apache.logging.log4j.LogManager;

public class Hidden implements Runnable {
    @Override
    public void run() {
        RuntimeException exception = new RuntimeException("boom");
        StackTraceElement[] original = exception.getStackTrace();
        StackTraceElement[] withMissing = new StackTraceElement[original.length + 1];
        withMissing[0] = new StackTraceElement("com.missing.Gone", "vanish", "Gone.java", 7);
        System.arraycopy(original, 0, withMissing, 1, original.length);
        exception.setStackTrace(withMissing);
        LogManager.getLogger(Hidden.class).error("event-with-unloadable-frame", exception);
        LogManager.getLogger(Hidden.class).error("event-control", new IllegalStateException("plain"));
    }
}
