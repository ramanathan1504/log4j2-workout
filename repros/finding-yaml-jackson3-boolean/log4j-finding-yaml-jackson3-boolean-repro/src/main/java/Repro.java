import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    private static final Map<String, Boolean> ADDITIVITY = new LinkedHashMap<>();

    static {
        for (String s : new String[] {"yes", "Yes", "YES", "on", "On", "ON", "true", "True", "TRUE"}) {
            ADDITIVITY.put(s, true);
        }
        for (String s : new String[] {"y", "Y", "no", "off", "n", "false", "FALSE", "qyes"}) {
            ADDITIVITY.put(s, false);
        }
    }

    public static void main(String[] args) throws Exception {
        String variant = args[0];
        Path dir = Paths.get(System.getProperty("outdir"));
        Files.createDirectories(dir);
        Files.write(dir.resolve("seed.log"), List.of("seed"));
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        String configName = context.getConfiguration().getName();
        String configClass = context.getConfiguration().getClass().getSimpleName();
        for (String s : ADDITIVITY.keySet()) {
            LogManager.getLogger("s." + s).debug("m");
        }
        LogManager.getLogger("lvl.off").error("m");
        LogManager.getLogger("app").info("appended");
        LogManager.getLogger("loc").info("loc");
        LogManager.shutdown();
        List<String> app = read(dir.resolve("app.log"));
        List<String> seed = read(dir.resolve("seed.log"));
        List<String> loc = read(dir.resolve("loc.log"));
        System.out.println(verdict(configName.equals("YamlBooleanRepro")) + variant + " config loaded expected=YamlBooleanRepro observed="
                + configName + " (" + configClass + ")");
        for (Map.Entry<String, Boolean> e : ADDITIVITY.entrySet()) {
            String scalar = e.getKey().equals("qyes") ? "'yes'" : e.getKey();
            long copies = app.stream().filter(("s." + e.getKey() + " m")::equals).count();
            long expected = e.getValue() ? 2 : 1;
            String control = e.getKey().matches("(?i)true|false|qyes") ? " [control]" : "";
            System.out.println(verdict(copies == expected) + variant + " additivity: " + scalar + control + " expected="
                    + e.getValue() + " (" + expected + " copies) observed=" + (copies == 2 ? "true" : copies == 1 ? "false" : "?")
                    + " (" + copies + " copies)");
        }
        long offLines = app.stream().filter("lvl.off m"::equals).count();
        System.out.println(verdict(offLines == 0) + variant + " level: off expected=0 lines observed=" + offLines);
        boolean seedKept = seed.contains("seed") && seed.contains("appended");
        System.out.println(verdict(seedKept) + variant + " File append: yes expected=[seed, appended] observed=" + seed);
        String locLine = loc.isEmpty() ? "" : loc.get(0);
        System.out.println(verdict(locLine.equals("main|loc")) + variant + " Logger includeLocation: yes expected=main|loc observed="
                + locLine);
    }

    private static String verdict(boolean ok) {
        return ok ? "PASS " : "FAIL ";
    }

    private static List<String> read(Path p) throws Exception {
        return Files.exists(p) ? Files.readAllLines(p) : List.of();
    }
}
