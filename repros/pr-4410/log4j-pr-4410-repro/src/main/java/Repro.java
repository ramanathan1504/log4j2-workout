import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(String[] args) throws Exception {
        String variant = args[0];
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        String configName = context.getConfiguration().getName();
        String configClass = context.getConfiguration().getClass().getSimpleName();
        LogManager.getLogger("org.example.Service").debug("yaml-debug");
        LogManager.shutdown();
        Path log = Paths.get(System.getProperty("outdir"), "app.log");
        List<String> lines = Files.exists(log) ? Files.readAllLines(log) : List.of();
        long copies = lines.stream().filter("DEBUG org.example.Service yaml-debug"::equals).count();
        boolean loaded = configName.equals("Jackson3Repro");
        System.out.println((loaded ? "PASS " : "FAIL ") + variant + " log4j2.yaml loaded expected=Jackson3Repro observed="
                + configName + " (" + configClass + ")");
        System.out.println((copies > 0 ? "PASS " : "FAIL ") + variant
                + " DEBUG org.example.Service written expected>=1 observed=" + copies);
        if (variant.equals("jackson2+jackson3")) {
            System.out.println((copies == 2 ? "PASS " : "FAIL ") + variant
                    + " 'additivity: yes' keeps its Jackson 2 meaning (true, 2 copies) observed=" + copies
                    + " cop" + (copies == 1 ? "y" : "ies"));
        }
    }
}
