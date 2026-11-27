import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.util.FilteredObjectInputStream;

public final class Repro {

    private static final ObjectInputFilter REJECT_ARRAY_LIST = info ->
            info.serialClass() == ArrayList.class ? ObjectInputFilter.Status.REJECTED : ObjectInputFilter.Status.UNDECIDED;

    private static final ObjectInputFilter ALLOW_ALL = info -> ObjectInputFilter.Status.ALLOWED;

    private static byte[] serialize(final Serializable obj) throws IOException {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bout)) {
            out.writeObject(obj);
        }
        return bout.toByteArray();
    }

    private static LogEvent event(final Message message) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName("repro")
                .setLevel(Level.INFO)
                .setMessage(message)
                .build();
    }

    private static Object payload(final Object read) {
        final Message message = read instanceof LogEvent ? ((LogEvent) read).getMessage() : (Message) read;
        return message == null ? null : message.getParameters()[0];
    }

    private static String describe(final Object value) {
        return value == null ? "null" : value.getClass().getName() + "=" + value;
    }

    private static String read(final ObjectInputStream in) {
        try (ObjectInputStream ois = in) {
            return "accepted " + describe(payload(ois.readObject()));
        } catch (final Exception e) {
            return "rejected " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static void report(final String name, final String expectPrefix, final String observed) {
        System.out.println((observed.startsWith(expectPrefix) ? "PASS " : "FAIL ") + name + " observed=" + observed);
    }

    private static ObjectInputStream filtered(final byte[] data, final ObjectInputFilter filter) throws IOException {
        final ObjectInputStream in = new FilteredObjectInputStream(new ByteArrayInputStream(data));
        if (filter != null) {
            in.setObjectInputFilter(filter);
        }
        return in;
    }

    private static ObjectInputStream plain(final byte[] data, final ObjectInputFilter filter) throws IOException {
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data));
        if (filter != null) {
            in.setObjectInputFilter(filter);
        }
        return in;
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("INFO java.version=" + System.getProperty("java.version"));
        final ArrayList<String> list = new ArrayList<>(Arrays.asList("a", "b"));
        final URI uri = URI.create("https://example.org/");

        final byte[] listMessage = serialize(new ObjectMessage(list));
        final byte[] listEvent = serialize((Serializable) event(new ObjectMessage(list)));
        final byte[] uriMessage = serialize(new ObjectMessage(uri));
        final byte[] uriEvent = serialize((Serializable) event(new ObjectMessage(uri)));

        report("filtered-stream-outer-filter-reaches-nested-message",
                "rejected", read(filtered(listMessage, REJECT_ARRAY_LIST)));
        report("filtered-stream-outer-filter-reaches-nested-event-message",
                "rejected", read(filtered(listEvent, REJECT_ARRAY_LIST)));
        report("control-filtered-stream-no-filter-allowlisted-payload",
                "accepted java.util.ArrayList", read(filtered(listMessage, null)));

        report("plain-stream-allow-all-filter-keeps-non-allowlisted-message-payload",
                "accepted java.net.URI", read(plain(uriMessage, ALLOW_ALL)));
        report("plain-stream-allow-all-filter-keeps-non-allowlisted-event-payload",
                "accepted java.net.URI", read(plain(uriEvent, ALLOW_ALL)));
        report("plain-stream-no-filter-keeps-non-allowlisted-event-payload",
                "accepted java.net.URI", read(plain(uriEvent, null)));
        report("control-plain-stream-own-filter-still-applies",
                "rejected", read(plain(listEvent, REJECT_ARRAY_LIST)));
    }
}
