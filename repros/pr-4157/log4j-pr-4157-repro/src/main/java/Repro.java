import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {

    static final int TRANSIENT_LOGGERS = 5000;

    public static void main(String[] args) throws Exception {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Logger held = context.getLogger("held.logger");
        List<Logger> transientLoggers = createTransientLoggers(context);
        long before = countTransient(context);
        transientLoggers.clear();
        for (int attempt = 0; attempt < 10 && countTransient(context) > 0; attempt++) {
            System.gc();
            Thread.sleep(100);
            context.getLogger("trigger.expunge." + attempt);
        }
        long after = countTransient(context);
        boolean heldStillRegistered = context.hasLogger("held.logger") && context.getLogger("held.logger") == held;
        held.info("held-logger-still-works");
        LogManager.shutdown();

        System.out.println((before == TRANSIENT_LOGGERS ? "PASS " : "FAIL ") + "transient-loggers-registered-while-referenced-control count=" + before);
        System.out.println((after == 0 ? "PASS " : "FAIL ") + "unreferenced-loggers-released-after-gc remaining=" + after + "/" + TRANSIENT_LOGGERS);
        System.out.println((heldStillRegistered ? "PASS " : "FAIL ") + "referenced-logger-kept-control same-instance=" + heldStillRegistered);
    }

    static List<Logger> createTransientLoggers(LoggerContext context) {
        List<Logger> loggers = new ArrayList<>();
        for (int index = 0; index < TRANSIENT_LOGGERS; index++) {
            loggers.add(context.getLogger("transient.logger." + index));
        }
        return loggers;
    }

    static long countTransient(LoggerContext context) {
        return context.getLoggers().stream().filter(logger -> logger.getName().startsWith("transient.")).count();
    }
}
