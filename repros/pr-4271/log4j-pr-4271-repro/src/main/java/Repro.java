import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.FilteredObjectInputStream;

public final class Repro {

    static final AtomicInteger GADGET_READS = new AtomicInteger();

    public static final class GadgetMessage implements Message {
        private static final long serialVersionUID = 1L;

        @Override
        public String getFormattedMessage() {
            return "gadget";
        }

        @Override
        public String getFormat() {
            return "gadget";
        }

        @Override
        public Object[] getParameters() {
            return null;
        }

        @Override
        public Throwable getThrowable() {
            return null;
        }

        private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            GADGET_READS.incrementAndGet();
        }
    }

    static byte[] serialize(final Message message) throws Exception {
        final LogEvent event = Log4jLogEvent.newBuilder()
                .setLoggerName("repro")
                .setLevel(Level.INFO)
                .setMessage(message)
                .build();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(event);
        }
        return bytes.toByteArray();
    }

    static LogEvent deserialize(final byte[] data) throws Exception {
        try (FilteredObjectInputStream in = new FilteredObjectInputStream(new ByteArrayInputStream(data))) {
            return (LogEvent) in.readObject();
        }
    }

    static boolean containsMarshalledObject(final byte[] data) {
        return new String(data, StandardCharsets.ISO_8859_1).contains("java.rmi.MarshalledObject");
    }

    static void report(final String name, final boolean pass, final String observed) {
        System.out.println((pass ? "PASS " : "FAIL ") + name + " observed=" + observed);
    }

    public static void main(final String[] args) throws Exception {
        final byte[] gadget = serialize(new GadgetMessage());
        String outcome;
        try {
            final LogEvent restored = deserialize(gadget);
            outcome = "accepted message=" + restored.getMessage().getClass().getName();
        } catch (final Throwable error) {
            outcome = "rejected " + error.getClass().getSimpleName() + ": " + error.getMessage();
        }
        report("non-allowlisted-message-class", GADGET_READS.get() == 0,
                "GadgetMessage.readObject calls=" + GADGET_READS.get() + " " + outcome);
        report("stream-free-of-MarshalledObject", !containsMarshalledObject(gadget),
                "contains java.rmi.MarshalledObject=" + containsMarshalledObject(gadget) + " bytes=" + gadget.length);
        try {
            final LogEvent restored = deserialize(serialize(new SimpleMessage("hello")));
            report("control-simple-message", "hello".equals(restored.getMessage().getFormattedMessage()),
                    restored.getMessage().getClass().getSimpleName() + "=" + restored.getMessage().getFormattedMessage());
        } catch (final Throwable error) {
            report("control-simple-message", false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }
}
