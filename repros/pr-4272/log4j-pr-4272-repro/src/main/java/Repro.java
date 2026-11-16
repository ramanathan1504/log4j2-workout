import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.util.FilteredObjectInputStream;
import org.apache.logging.log4j.util.SortedArrayStringMap;

public final class Repro {

    static final int FORGED_CAPACITY = 1 << 24;

    static final class NonSerializable {
        @Override
        public String toString() {
            return "non-serializable";
        }
    }

    static byte[] serialize(final Object original) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        return bytes.toByteArray();
    }

    static Object deserialize(final byte[] data) throws Exception {
        try (FilteredObjectInputStream in = new FilteredObjectInputStream(new ByteArrayInputStream(data))) {
            return in.readObject();
        }
    }

    static int keysLength(final SortedArrayStringMap map) throws Exception {
        final Field keys = SortedArrayStringMap.class.getDeclaredField("keys");
        keys.setAccessible(true);
        return ((Object[]) keys.get(map)).length;
    }

    static void report(final String name, final boolean pass, final String observed) {
        System.out.println((pass ? "PASS " : "FAIL ") + name + " observed=" + observed);
    }

    static void forge(final String target) throws Exception {
        final SortedArrayStringMap map = new SortedArrayStringMap();
        map.putValue("k", "v");
        final byte[] data = serialize(map);
        final byte[] header = {0x77, 0x08, 0, 0, 0, 4, 0, 0, 0, 1};
        for (int i = 0; i + header.length <= data.length; i++) {
            boolean match = true;
            for (int j = 0; j < header.length && match; j++) {
                match = data[i + j] == header[j];
            }
            if (match) {
                data[i + 2] = (byte) (FORGED_CAPACITY >>> 24);
                data[i + 3] = (byte) (FORGED_CAPACITY >>> 16);
                data[i + 4] = (byte) (FORGED_CAPACITY >>> 8);
                data[i + 5] = (byte) FORGED_CAPACITY;
                Files.write(Paths.get(target), data);
                System.out.println("INFO forged legacy stream bytes=" + data.length + " capacity=" + FORGED_CAPACITY
                        + " mappings=1");
                return;
            }
        }
        throw new IllegalStateException("capacity/size block not found in the legacy stream");
    }

    static void check(final String forged) throws Exception {
        final byte[] data = Files.readAllBytes(Paths.get(forged));
        try {
            final SortedArrayStringMap map = (SortedArrayStringMap) deserialize(data);
            final int length = keysLength(map);
            report("forged-legacy-capacity", length < 1024,
                    "accepted size=" + map.size() + " keys.length=" + length + " from " + data.length + " bytes");
        } catch (final Throwable error) {
            report("forged-legacy-capacity", !(error instanceof OutOfMemoryError),
                    "rejected " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        final SortedArrayStringMap values = new SortedArrayStringMap();
        values.putValue("plain", "text");
        values.putValue("object", new NonSerializable());
        try {
            final SortedArrayStringMap restored = (SortedArrayStringMap) deserialize(serialize(values));
            final Object object = restored.getValue("object");
            report("non-serializable-value", "non-serializable".equals(object), "object=" + object);
            report("control-serializable-value", "text".equals(restored.getValue("plain")),
                    "plain=" + restored.getValue("plain") + " keys.length=" + keysLength(restored));
        } catch (final Throwable error) {
            report("non-serializable-value", false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        System.out.println("INFO serialVersionUID SortedArrayStringMap="
                + ObjectStreamClass.lookup(SortedArrayStringMap.class).getSerialVersionUID());
    }

    public static void main(final String[] args) throws Exception {
        if ("forge".equals(args[0])) {
            forge(args[1]);
        } else {
            check(args[1]);
        }
    }
}
