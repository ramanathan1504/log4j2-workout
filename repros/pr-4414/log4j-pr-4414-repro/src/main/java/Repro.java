import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;

public class Repro {
    public static void main(String[] args) throws Exception {
        String[] names = {"com.foo", "com.foo.Service", "com.foobar.Service", "com.food"};
        String[] expected = {"WARN", "WARN", "INFO", "INFO"};
        for (String name : names) {
            LogManager.getLogger(name).info("event");
        }
        LogManager.shutdown();
        List<String> lines = Files.readAllLines(Paths.get(System.getProperty("outdir"), "app.log"));
        for (int i = 0; i < names.length; i++) {
            String observed = "MISSING";
            for (String line : lines) {
                String[] parts = line.split(" ");
                if (parts.length >= 2 && parts[1].equals(names[i])) {
                    observed = parts[0];
                }
            }
            String role = i < 2 ? "control" : "sibling";
            System.out.println((observed.equals(expected[i]) ? "PASS " : "FAIL ") + role + " logger=" + names[i]
                    + " expected=" + expected[i] + " observed=" + observed);
        }
    }
}
