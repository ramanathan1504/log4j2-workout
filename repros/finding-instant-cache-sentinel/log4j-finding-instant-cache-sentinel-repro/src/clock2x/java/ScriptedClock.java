import org.apache.logging.log4j.core.util.Clock;

public final class ScriptedClock implements Clock {

    public static volatile long now = 12345L;

    @Override
    public long currentTimeMillis() {
        return now;
    }
}
