import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;

public final class Repro {

    public static void main(final String[] args) throws Exception {
        System.setProperty("x", "set");
        System.setProperty("p", "a$$b");
        System.setProperty("q", "a$}b");
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final StrSubstitutor substitutor = context.getConfiguration().getStrSubstitutor();
        LogManager.getLogger(Repro.class).info("msg");

        check(substitutor, "escaped-delimiter", "${sys:undefined:-a$:-b}", "a:-b");
        check(substitutor, "escaped-escape", "${sys:undefined:-a$$b}", "a$b");
        check(substitutor, "deferred-lookup-default-var-set", "${sys:x:-$${ctx:k}}", "set");
        check(substitutor, "deferred-lookup-default-var-unset", "${sys:undefined:-$${ctx:k}}", "${ctx:k}");
        check(substitutor, "nested-value-dollar-dollar", "${sys:undefined:-${sys:p}}", "a$$b");
        check(substitutor, "nested-value-dollar-brace", "${sys:undefined:-${sys:q}}", "a$}b");
        check(substitutor, "plain-default-control", "${sys:undefined:-plain}", "plain");
        System.out.println(
                "INFO trailing-escape input=${sys:undefined:-a$} observed=" + substitutor.replace("${sys:undefined:-a$}"));
        LogManager.shutdown();

        final String line = new String(
                        Files.readAllBytes(Paths.get(System.getProperty("outdir"), "app.log")), StandardCharsets.UTF_8)
                .trim();
        final boolean layoutOk = line.matches("\\d\\d:\\d\\d:\\d\\d msg");
        System.out.println((layoutOk ? "PASS " : "FAIL ")
                + "layout-escaped-braces pattern=${sys:undefined.tz:-%d{HH:mm:ss$}{GMT+00$}} expected=<HH:mm:ss> msg observed="
                + line);
    }

    private static void check(
            final StrSubstitutor substitutor, final String name, final String input, final String expected) {
        final String observed = substitutor.replace(input);
        System.out.println((expected.equals(observed) ? "PASS " : "FAIL ") + name + " input=" + input + " expected="
                + expected + " observed=" + observed);
    }
}
