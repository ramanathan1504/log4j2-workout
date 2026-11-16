import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(String[] args) throws Exception {
        ThreadContext.put("message", "forged-by-mdc");
        ThreadContext.put("log.level", "FATAL");
        ThreadContext.put("user", "alice");
        ThreadContext.put("secret", "s3cr3t");
        LogManager.getLogger("ecs").info("real-message");
        LogManager.getLogger("allow").info("real-message");
        ((LoggerContext) LogManager.getContext(false)).stop();

        String ecs = read(System.getProperty("ecsfile"));
        int messageKeys = count(ecs, "\"message\":");
        int levelKeys = count(ecs, "\"log.level\":");
        boolean ok = messageKeys == 1 && levelKeys == 1 && ecs.contains("\"message\":\"real-message\"") && ecs.contains("\"user\":\"alice\"");
        System.out.println((ok ? "PASS " : "FAIL ") + "ecs-mdc-cannot-overwrite-structural-fields message-keys=" + messageKeys
                + " log.level-keys=" + levelKeys + " forged-present=" + ecs.contains("forged-by-mdc") + " line=" + ecs);

        String allow = read(System.getProperty("allowfile"));
        boolean allowOk = allow.contains("\"ctx\":{\"user\":\"alice\"}");
        System.out.println((allowOk ? "PASS " : "FAIL ") + "mdc-key-allowed-[user] line=" + (allow.isEmpty() ? "<empty>" : allow));
    }

    static String read(String path) throws Exception {
        File file = new File(path);
        return file.exists() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim() : "";
    }

    static int count(String text, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(text);
        int n = 0;
        while (matcher.find()) {
            n++;
        }
        return n;
    }
}
