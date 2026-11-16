import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Repro {

    static final class CollidingException extends RuntimeException {
        CollidingException(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CollidingException;
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }

    public static void main(String[] args) throws Exception {
        String outdir = System.getProperty("outdir");
        Logger logger = LogManager.getLogger(Repro.class);
        CollidingException colliding =
                new CollidingException("colliding-outer", new CollidingException("colliding-middle", new CollidingException("colliding-inner", null)));
        RuntimeException plain =
                new RuntimeException("plain-outer", new RuntimeException("plain-middle", new RuntimeException("plain-inner")));
        logger.error("colliding-event", colliding);
        logger.error("plain-event", plain);
        LogManager.shutdown();

        List<String> json = Files.readAllLines(Paths.get(outdir, "events.json"), StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(Paths.get(outdir, "events.txt")), StandardCharsets.UTF_8);
        report("json-colliding-chain", find(json, "colliding-event"), "colliding-");
        report("json-plain-chain-control", find(json, "plain-event"), "plain-");
        report("pattern-xEx-colliding-chain", text, "colliding-");
    }

    static String find(List<String> lines, String marker) {
        for (String line : lines) {
            if (line.contains("\"message\":\"" + marker + "\"")) {
                return line;
            }
        }
        return "";
    }

    static void report(String name, String rendered, String prefix) {
        StringBuilder seen = new StringBuilder();
        for (String level : new String[] {"outer", "middle", "inner"}) {
            if (rendered.contains(prefix + level)) {
                seen.append(seen.length() == 0 ? "" : ",").append(level);
            }
        }
        boolean complete = seen.toString().equals("outer,middle,inner");
        System.out.println((complete ? "PASS " : "FAIL ") + name + " levels-rendered=" + seen);
    }
}
