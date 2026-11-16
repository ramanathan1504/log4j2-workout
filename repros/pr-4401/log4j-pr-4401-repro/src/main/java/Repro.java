import org.apache.logging.log4j.core.appender.db.jpa.converter.ThrowableAttributeConverter;

public class Repro {
    public static class DefaultConstructorException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DefaultConstructorException() {
            super("Message from default constructor.");
        }
    }

    private static final ThrowableAttributeConverter CONVERTER = new ThrowableAttributeConverter();

    public static void main(final String[] args) {
        check("no-arg constructor with message", new DefaultConstructorException());
        final DefaultConstructorException withCause = new DefaultConstructorException();
        withCause.initCause(new IllegalArgumentException("Original cause."));
        check("control: no-arg constructor with cause", withCause);
        check("control: String constructor", new IllegalStateException("String constructor message."));
    }

    private static void check(final String label, final Throwable original) {
        final String column = CONVERTER.convertToDatabaseColumn(original);
        String observed;
        boolean pass;
        try {
            final Throwable restored = CONVERTER.convertToEntityAttribute(column);
            observed = restored == null
                    ? "restored=null"
                    : "class=" + restored.getClass().getSimpleName() + " message=" + restored.getMessage();
            pass = restored != null
                    && restored.getClass() == original.getClass()
                    && String.valueOf(original.getMessage()).equals(String.valueOf(restored.getMessage()));
        } catch (final RuntimeException e) {
            observed = "threw " + e;
            pass = false;
        }
        System.out.printf("%s %s expected=%s observed=%s%n", pass ? "PASS" : "FAIL", label, original.getMessage(), observed);
    }
}
