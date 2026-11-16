import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectArrayMessage;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.FilteredObjectInputStream;

public final class Repro {

    static final class NonSerializable {
        @Override
        public String toString() {
            return "non-serializable";
        }
    }

    static Object roundTrip(final Object original) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        try (FilteredObjectInputStream in = new FilteredObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }

    static void report(final String name, final boolean pass, final String observed) {
        System.out.println((pass ? "PASS " : "FAIL ") + name + " observed=" + observed);
    }

    static String describe(final Throwable error) {
        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    public static void main(final String[] args) {
        try {
            final Message restored = (Message) roundTrip(new ParameterizedMessage("Hello!", (Object[]) null, null));
            report("parameterized-null-args", restored.getParameters() == null && "Hello!".equals(restored.getFormattedMessage()),
                    "params=" + Arrays.toString(restored.getParameters()) + " formatted=" + restored.getFormattedMessage());
        } catch (final Throwable error) {
            report("parameterized-null-args", false, describe(error));
        }
        try {
            final Message restored = (Message) roundTrip(new ObjectArrayMessage("A", new NonSerializable(), "C"));
            report("object-array-non-serializable-element",
                    Arrays.equals(new Object[] {"A", "non-serializable", "C"}, restored.getParameters()),
                    Arrays.toString(restored.getParameters()));
        } catch (final Throwable error) {
            report("object-array-non-serializable-element", false, describe(error));
        }
        try {
            final Message restored = (Message) roundTrip(new ObjectArrayMessage("A", 1, "C"));
            report("control-object-array-serializable",
                    Arrays.equals(new Object[] {"A", 1, "C"}, restored.getParameters()),
                    Arrays.toString(restored.getParameters()));
        } catch (final Throwable error) {
            report("control-object-array-serializable", false, describe(error));
        }
        System.out.println("INFO serialVersionUID ObjectArrayMessage="
                + ObjectStreamClass.lookup(ObjectArrayMessage.class).getSerialVersionUID()
                + " ParameterizedMessage="
                + ObjectStreamClass.lookup(ParameterizedMessage.class).getSerialVersionUID());
    }
}
