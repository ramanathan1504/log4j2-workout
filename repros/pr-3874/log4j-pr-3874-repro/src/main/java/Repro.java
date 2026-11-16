import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(String[] args) throws Exception {
        String firstJvmArg = ManagementFactory.getRuntimeMXBean().getInputArguments().get(0);
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        String substituted = context.getConfiguration().getStrSubstitutor().replace("${jvmrunargs:0}");
        LogManager.getLogger(Repro.class).info("hello");
        context.stop();
        String line = new String(Files.readAllBytes(Paths.get(System.getProperty("outdir"), "app.log")), StandardCharsets.UTF_8).trim();
        boolean substitutorResolved = substituted.equals(firstJvmArg);
        boolean layoutResolved = line.startsWith("arg0=" + firstJvmArg + " ");
        System.out.println((substitutorResolved ? "FAIL" : "PASS") + " config-substitution ${jvmrunargs:0} -> " + substituted + " (first JVM arg " + firstJvmArg + ")");
        System.out.println((layoutResolved ? "FAIL" : "PASS") + " runtime-layout $${jvmrunargs:0} -> " + line);
        System.out.println((line.endsWith("msg=hello") ? "PASS" : "FAIL") + " control event-written -> " + line);
    }
}
