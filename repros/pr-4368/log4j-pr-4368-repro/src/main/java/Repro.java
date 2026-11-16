import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {

    public static final class UnserializableMarker implements Marker {
        private static final long serialVersionUID = 1L;
        private final Object lock = new Object();

        public Marker addParents(Marker... markers) { return this; }
        public String getName() { return "UNSERIALIZABLE"; }
        public Marker[] getParents() { return new Marker[0]; }
        public boolean hasParents() { return false; }
        public boolean isInstanceOf(Marker m) { return m == this; }
        public boolean isInstanceOf(String name) { return getName().equals(name); }
        public boolean remove(Marker marker) { return false; }
        public Marker setParents(Marker... markers) { return this; }
        public String toString() { return getName() + lock.hashCode(); }
    }

    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        System.setProperty("repro.file", file.getPath());
        Logger logger = LogManager.getLogger(Repro.class);
        logger.info("event-1");
        logger.info(new UnserializableMarker(), "event-2-unserializable-marker");
        logger.info("event-3");
        ((LoggerContext) LogManager.getContext(false)).stop();

        List<String> read = new ArrayList<>();
        String readerError = "none";
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            while (true) {
                read.add(((LogEvent) in.readObject()).getMessage().getFormattedMessage());
            }
        } catch (EOFException endOfStream) {
            readerError = "none (clean EOF)";
        } catch (Exception e) {
            readerError = e.getClass().getSimpleName();
        }
        System.out.println("file bytes=" + file.length() + ", events read=" + read + ", reader error=" + readerError);
        boolean event3Readable = read.contains("event-3");
        System.out.println((read.contains("event-1") ? "PASS" : "FAIL") + " control event-1 readable: " + read.contains("event-1"));
        System.out.println((event3Readable ? "PASS" : "FAIL")
                + " event-3 readable after failed event-2: " + event3Readable + ", reader error=" + readerError);
    }
}
