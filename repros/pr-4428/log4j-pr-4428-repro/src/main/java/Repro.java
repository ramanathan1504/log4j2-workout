import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.status.StatusLogger;

public final class Repro {

    public static void main(final String[] args) {
        final String mode = System.getSecurityManager() != null ? "policy-denies-bulk-reads" : "no-security-manager-control";
        Level fallbackLevel;
        try {
            fallbackLevel = StatusLogger.getLogger().getFallbackListener().getStatusLevel();
        } catch (final Throwable error) {
            System.out.println("FAIL " + mode + " statuslogger-init expected=no-throw observed=" + describe(error));
            return;
        }
        System.out.println("PASS " + mode + " statuslogger-init expected=no-throw observed=ok");
        System.out.println((Level.INFO.equals(fallbackLevel) ? "PASS " : "FAIL ") + mode
                + " per-key-sysprop log4j2.StatusLogger.level=INFO expected=INFO observed=" + fallbackLevel);
        try {
            LogManager.getLogger(Repro.class).info("api logger works");
            System.out.println("PASS " + mode + " logmanager-getLogger expected=no-throw observed=ok");
        } catch (final Throwable error) {
            System.out.println("FAIL " + mode + " logmanager-getLogger expected=no-throw observed=" + describe(error));
        }
    }

    private static String describe(final Throwable error) {
        final StringBuilder chain = new StringBuilder(error.toString());
        for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
            chain.append(" <- ").append(cause);
        }
        return chain.toString();
    }
}
