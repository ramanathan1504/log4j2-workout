import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;

public class Repro {
    public static void main(String[] args) throws Exception {
        LogManager.getLogger("repro").info("café naïve à la carte");
        LogManager.shutdown();
        Path dir = Paths.get(args[0]);
        check("latin1 (ISO-8859-1)", dir.resolve("latin1.bin"));
        check("utf8 control", dir.resolve("utf8.bin"));
    }

    private static void check(String name, Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        int space = 0;
        while (bytes[space] != ' ') {
            space++;
        }
        int advertised = Integer.parseInt(new String(bytes, 0, space, StandardCharsets.US_ASCII));
        int actual = bytes.length - space - 1;
        String verdict = advertised == actual ? "PASS" : "FAIL";
        System.out.println(verdict + " " + name + " MSG-LEN=" + advertised + " payloadBytes=" + actual);
    }
}
