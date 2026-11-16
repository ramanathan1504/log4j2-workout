import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(String[] args) throws Exception {
        LogManager.getLogger("ansi").info("x");
        ((LoggerContext) LogManager.getContext(false)).stop();
        List<String> lines = Files.readAllLines(Paths.get(System.getProperty("logfile")), StandardCharsets.UTF_8);
        check(lines, "underline", "\u001b[4mU\u001b[m");
        check(lines, "italic", "\u001b[3mI\u001b[m");
        check(lines, "bold-control", "\u001b[1mB\u001b[m");
    }

    static void check(List<String> lines, String name, String expected) {
        String line = lines.stream().filter(l -> l.startsWith(name + "=")).findFirst().orElse(name + "=<missing>");
        String observed = line.substring(name.length() + 1);
        System.out.println((observed.equals(expected) ? "PASS " : "FAIL ") + name + " expected=" + visible(expected) + " observed=" + visible(observed));
    }

    static String visible(String s) {
        return s.replace("\u001b", "ESC");
    }
}
