import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;

public class Repro {
    public static void main(String[] args) throws Exception {
        LogManager.getLogger("repro").info("event");
        LogManager.shutdown();
        String line = new String(Files.readAllBytes(Paths.get(args[0], "app.log")), StandardCharsets.UTF_8).trim();
        String[] parts = line.split("\\|", -1);
        report("unknown key falls back to <Properties>", "fromProperties", parts[1]);
        report("unknown key uses :-default", "dflt", parts[2]);
        boolean known = parts[3].startsWith("Java version");
        System.out.println((known ? "PASS" : "FAIL") + " control java:version observed=" + parts[3]);
    }

    private static void report(String name, String expected, String observed) {
        System.out.println((expected.equals(observed) ? "PASS" : "FAIL") + " " + name + " expected=" + expected + " observed=" + observed);
    }
}
