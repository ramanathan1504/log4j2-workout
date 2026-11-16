import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;

public class Repro {
    public static void main(String[] args) throws Exception {
        LogManager.getLogger("repro").info("DATA");
        LogManager.shutdown();
        Path dir = Paths.get(args[0]);
        check("File (control)", dir.resolve("file.log"));
        check("RandomAccessFile", dir.resolve("raf.log"));
        check("MemoryMappedFile", dir.resolve("mmf.log"));
    }

    private static void check(String name, Path file) throws Exception {
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\u0000", "");
        String expected = "HEADER\nDATA\nFOOTER\n";
        String verdict = expected.equals(content) ? "PASS" : "FAIL";
        System.out.println(verdict + " " + name + " content=" + content.replace("\n", "\\n"));
    }
}
