import java.io.File;
import java.util.HashMap;
import org.apache.logging.log4j.test.junit.SerialUtil;

public final class Repro {

    private static String deserialize(final byte[] data) {
        try {
            final Object read = SerialUtil.deserialize(data);
            return "accepted " + read.getClass().getName();
        } catch (final IllegalStateException e) {
            final Throwable cause = e.getCause() == null ? e : e.getCause();
            return "rejected " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
    }

    private static void report(final String name, final String expectPrefix, final String observed) {
        System.out.println((observed.startsWith(expectPrefix) ? "PASS " : "FAIL ") + name + " observed=" + observed);
    }

    public static void main(final String[] args) {
        System.out.println("INFO java.version=" + System.getProperty("java.version")
                + " jdk.serialFilter=" + System.getProperty("jdk.serialFilter"));
        final byte[] file = SerialUtil.serialize(new File("/etc/passwd"));
        final byte[] map = SerialUtil.serialize(new HashMap<String, String>());
        report("non-allowlisted-class-rejected", "rejected", deserialize(file));
        report("control-allowlisted-class-accepted", "accepted java.util.HashMap", deserialize(map));
    }
}
