import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;

public class Repro {
    private static final PatternLayout LAYOUT = PatternLayout.newBuilder().withPattern("%enc{%m}{HTML}").build();

    public static void main(String[] args) {
        String mixed = repeat("a<b>&\"c'/\r\n", 200);
        String expected = mixed.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;").replace("/", "&#x2F;")
                .replace("\r", "\\r").replace("\n", "\\n");
        String actual = format(mixed);
        System.out.println((expected.equals(actual) ? "PASS" : "FAIL") + " escaped output equals String.replace reference, length=" + actual.length());

        for (int i = 0; i < 2; i++) {
            time(16 * 1024);
            time(64 * 1024);
        }
        long small = time(16 * 1024);
        long large = time(64 * 1024);
        double ratio = (double) large / small;
        System.out.printf("INFO 16 KiB of '\"' = %d ms, 64 KiB = %d ms%n", small / 1_000_000, large / 1_000_000);
        System.out.printf("%s 4x input costs %.1fx time (linear ~4x, quadratic ~16x; PASS below 8x)%n", ratio < 8 ? "PASS" : "FAIL", ratio);
    }

    private static long time(int size) {
        String input = repeat("\"", size);
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            long start = System.nanoTime();
            format(input);
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    private static String format(String message) {
        LogEvent event = Log4jLogEvent.newBuilder().setMessage(new SimpleMessage(message)).build();
        return LAYOUT.toSerializable(event);
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
