import org.apache.logging.log4j.core.appender.db.jpa.converter.ThrowableAttributeConverter;

public class Repro {
    public static class InitializationProbe {
        static {
            System.setProperty("repro.probe.initialized", "true");
        }
    }

    private static final ThrowableAttributeConverter CONVERTER = new ThrowableAttributeConverter();

    public static void main(final String[] args) {
        roundTrip("message with LF", new IllegalStateException("line one\nline two"));
        roundTrip("message with CRLF", new IllegalStateException("line one\r\nline two"));
        roundTrip("message starting with the escape prefix", new IllegalStateException("!LOG4J2!:literal"));
        roundTrip("control: single-line message with a backslash", new IllegalStateException("C:\\new"));

        final String injected = "boom\nCaused by " + InitializationProbe.class.getName() + ": injected";
        final String injectedColumn = CONVERTER.convertToDatabaseColumn(new IllegalStateException(injected));
        String outcome;
        try {
            CONVERTER.convertToEntityAttribute(injectedColumn);
            outcome = "read";
        } catch (final RuntimeException e) {
            outcome = "threw " + e.getClass().getSimpleName();
        }
        final boolean initialized = Boolean.getBoolean("repro.probe.initialized");
        System.out.printf(
                "%s message naming a non-throwable class in a fake Caused-by line: expected=not initialized observed=%s (%s)%n",
                initialized ? "FAIL" : "PASS", initialized ? "initialized" : "not initialized", outcome);

        final String oldRow = "java.lang.IllegalStateException: line one\nline two\n\tat Repro.main(Repro.java:1)\n";
        try {
            final Throwable restored = CONVERTER.convertToEntityAttribute(oldRow);
            System.out.printf(
                    "PASS legacy row with an unescaped line break: expected=no exception observed=message=%s frames=%d%n",
                    restored == null ? "restored=null" : restored.getMessage(),
                    restored == null ? -1 : restored.getStackTrace().length);
        } catch (final RuntimeException e) {
            System.out.printf("FAIL legacy row with an unescaped line break: expected=no exception observed=threw %s%n", e);
        }
    }

    private static void roundTrip(final String label, final Throwable original) {
        final String column = CONVERTER.convertToDatabaseColumn(original);
        String observed;
        boolean pass;
        try {
            final Throwable restored = CONVERTER.convertToEntityAttribute(column);
            observed = restored == null ? "restored=null" : "message=" + escape(restored.getMessage());
            pass = restored != null && original.getMessage().equals(restored.getMessage());
        } catch (final RuntimeException e) {
            observed = "threw " + e;
            pass = false;
        }
        System.out.printf(
                "%s %s: expected=message=%s observed=%s%n",
                pass ? "PASS" : "FAIL", label, escape(original.getMessage()), observed);
    }

    private static String escape(final String text) {
        return text == null ? "null" : text.replace("\r", "\\r").replace("\n", "\\n");
    }
}
