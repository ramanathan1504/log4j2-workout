import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class Repro {

    private static List<Diagnostic<? extends JavaFileObject>> compile(final Path source, final List<String> extraOptions)
            throws Exception {
        final Path classes = Files.createTempDirectory("repro-classes");
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            final List<String> options = new ArrayList<>(Arrays.asList(
                    "-cp", System.getProperty("java.class.path"), "-d", classes.toString(), "-Xlint:-options"));
            options.addAll(extraOptions);
            compiler.getTask(null, files, diagnostics, options, null, files.getJavaFileObjects(source.toFile()))
                    .call();
        }
        return diagnostics.getDiagnostics();
    }

    private static long countLog4j(final List<Diagnostic<? extends JavaFileObject>> diagnostics, final Diagnostic.Kind kind) {
        return diagnostics.stream()
                .filter(d -> d.getKind() == kind)
                .filter(d -> d.getMessage(Locale.ROOT).matches("(?s).*(PluginProcessor|GraalVmProcessor).*"))
                .count();
    }

    private static void runCase(final Path source, final String name, final List<String> options, final long expectedNotes, final long expectedWarnings)
            throws Exception {
        final List<Diagnostic<? extends JavaFileObject>> diagnostics = compile(source, options);
        final long notes = countLog4j(diagnostics, Diagnostic.Kind.NOTE);
        final long warnings = countLog4j(diagnostics, Diagnostic.Kind.WARNING);
        final boolean pass = notes == expectedNotes && warnings == expectedWarnings;
        System.out.println((pass ? "PASS " : "FAIL ") + name + " options=" + options + " notes=" + notes
                + " warnings=" + warnings + " expectedNotes=" + expectedNotes + " expectedWarnings=" + expectedWarnings);
        for (final Diagnostic<? extends JavaFileObject> d : diagnostics) {
            System.out.println("    " + d.getKind() + ": " + d.getMessage(Locale.ROOT).replace('\n', ' '));
        }
    }

    public static void main(final String[] args) throws Exception {
        final Path dir = Files.createTempDirectory("repro-src");
        final Path source = Files.createDirectories(dir.resolve("sample")).resolve("SampleLookup.java");
        try (InputStream in = Repro.class.getResourceAsStream("/sample/SampleLookup.java.txt")) {
            Files.copy(in, source);
        }
        final List<String> graal = Arrays.asList(
                "-Alog4j.graalvm.groupId=repro", "-Alog4j.graalvm.artifactId=repro");
        runCase(source, "default-severity-no-notes", graal, 0, 0);
        final List<String> noteOptIn = new ArrayList<>(graal);
        noteOptIn.add("-Alog4j.plugin.processor.minAllowedMessageKind=NOTE");
        runCase(source, "control-opt-in-NOTE-shows-notes", noteOptIn, 2, 0);
        final List<String> typo = new ArrayList<>(graal);
        typo.add("-Alog4j.plugin.processor.minAllowedMessageKind=WARNINGS");
        runCase(source, "typo-option-reported-once-per-processor", typo, 0, 2);
    }
}
