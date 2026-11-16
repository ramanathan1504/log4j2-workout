import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;

public class Repro {

    private static final Pattern UNUSED_CHILD = Pattern.compile("(\\w+) has no field or parameter that matches element (\\w+)");

    private static final Pattern UNUSED =
            Pattern.compile("(\\w+) contains (?:an invalid element or attribute|invalid attributes) (.*)$");

    public static void main(String[] args) throws Exception {
        PrintStream originalErr = System.err;
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(new Tee(originalErr, captured), true, StandardCharsets.UTF_8));
        System.setOut(new PrintStream(new Tee(originalErr, captured), true, StandardCharsets.UTF_8));
        LogManager.getLogger(Repro.class).info("hello");
        LogManager.shutdown();
        System.setErr(originalErr);
        System.setOut(originalOut);

        List<String> lines = List.of(captured.toString(StandardCharsets.UTF_8).split("\\R"));
        List<String> reported = new ArrayList<>();
        for (String line : lines) {
            Matcher m = UNUSED.matcher(line);
            if (m.find()) {
                reported.add(m.group(1) + ":" + m.group(2).replace("\"", ""));
            }
            Matcher c = UNUSED_CHILD.matcher(line);
            if (c.find()) {
                reported.add(c.group(1) + ":<" + c.group(2) + ">");
            }
        }
        System.out.println("unused-attribute-or-element-errors: " + reported);

        check("StringMatchFilter-without-text", reported, "StringMatchFilter", "onMatch", false);
        check("File-without-fileName", reported, "File", "append", false);
        check("File-without-fileName-child", reported, "File", "<PatternLayout>", false);
        check("Appenders-parent-of-failed-File", reported, "Appenders", "<File>", false);
        check("AppenderRef-without-ref", reported, "AppenderRef", "level", false);
        check("AppenderRef-without-ref-parent", reported, "Logger", "<AppenderRef>", false);
        check("control-Console-typo-bogusAttr", reported, "Console", "bogusAttr", true);
        check("control-valid-RandomAccessFile", reported, "RandomAccessFile", "fileName", false);
    }

    private static void check(String name, List<String> reported, String element, String attr, boolean expected) {
        String hit = null;
        for (String r : reported) {
            if (r.startsWith(element + ":") && List.of(r.substring(element.length() + 1).split(", ")).contains(attr)) {
                hit = r;
            }
        }
        boolean observed = hit != null;
        System.out.println((observed == expected ? "PASS " : "FAIL ") + name + " valid-attribute '" + attr
                + "' reported-as-invalid=" + observed + (hit != null ? " (" + hit + ")" : "")
                + " expected=" + expected);
    }

    private static final class Tee extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;

        Tee(OutputStream a, OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int c) throws java.io.IOException {
            a.write(c);
            b.write(c);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws java.io.IOException {
            a.write(buf, off, len);
            b.write(buf, off, len);
        }
    }
}
