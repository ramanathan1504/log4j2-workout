import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.Arrays;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.FilteredObjectInputStream;

public class Repro {

    public static void main(final String[] args) throws Exception {
        final String scenario = args[0];
        final String ownFile = args[1];
        final String otherFile = args.length > 2 ? args[2] : "";
        try {
            switch (scenario) {
                case "S1": {
                    final Object anon = new Object() {
                        @Override
                        public String toString() {
                            return "not-serializable";
                        }
                    };
                    final byte[] data = serialize(event(new ParameterizedMessage("user {} id {}", anon, 42)));
                    print("plain ObjectInputStream", read(new ObjectInputStream(new ByteArrayInputStream(data))));
                    break;
                }
                case "S2": {
                    final byte[] data = serialize(event(new ParameterizedMessage("user {} id {}", "alice", 42)));
                    print("FilteredObjectInputStream", read(new FilteredObjectInputStream(new ByteArrayInputStream(data))));
                    break;
                }
                case "S3": {
                    final byte[] data = serialize(event(new ObjectMessage(URI.create("https://logging.apache.org/"))));
                    print("FilteredObjectInputStream, java.net.URI parameter", read(new FilteredObjectInputStream(new ByteArrayInputStream(data))));
                    break;
                }
                case "S4": {
                    try (FileOutputStream out = new FileOutputStream(ownFile)) {
                        out.write(serialize(event(new ParameterizedMessage("user {} id {}", "alice", 42))));
                    }
                    System.out.println("wrote " + ownFile);
                    if (otherFile.isEmpty()) {
                        System.out.println("no event from the other version to read");
                    } else {
                        try (InputStream in = new FileInputStream(otherFile)) {
                            print("event written by the other version", read(new ObjectInputStream(in)));
                        }
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException(scenario);
            }
        } catch (final Exception e) {
            System.out.println("THROWN " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static LogEvent event(final Message message) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName("repro")
                .setLevel(Level.INFO)
                .setMessage(message)
                .build();
    }

    private static byte[] serialize(final LogEvent event) throws Exception {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bout)) {
            out.writeObject(event);
        }
        return bout.toByteArray();
    }

    private static LogEvent read(final ObjectInputStream in) throws Exception {
        try (ObjectInputStream s = in) {
            return (LogEvent) s.readObject();
        }
    }

    private static void print(final String how, final LogEvent event) {
        final Message m = event.getMessage();
        System.out.println(how + " -> " + m.getClass().getSimpleName() + " | " + m.getFormattedMessage()
                + " | parameters " + Arrays.toString(m.getParameters()));
    }
}
