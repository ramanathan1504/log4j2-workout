package repro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class App {

    private static final Log LOG = LogFactory.getLog(App.class);

    public static void main(String[] args) throws Exception {
        long millis = Long.getLong("repro.millis", 0L);
        ConfigurableApplicationContext ctx = SpringApplication.run(App.class, args);
        System.out.println("LOG_PATH=" + System.getProperty("LOG_PATH") + " LOG_FILE=" + System.getProperty("LOG_FILE"));
        System.getProperties().stringPropertyNames().stream().filter(n -> n.contains("ROLLINGPOLICY")).sorted()
                .forEach(n -> System.out.println("   sys " + n + "=" + System.getProperty(n)));
        String line = "x".repeat(200);
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            LOG.info(line);
            Thread.sleep(2);
        }
        ctx.close();
        Thread.sleep(1500);
        for (String dir : System.getProperty("repro.list", ".").split(",")) {
            list(Paths.get(dir));
        }
    }

    private static void list(Path dir) throws IOException {
        System.out.println("-- " + dir);
        Map<String, long[]> groups = new TreeMap<>();
        long gz = 0;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted().toList()) {
                if (p.equals(dir)) {
                    continue;
                }
                String rel = dir.relativize(p).toString();
                if (Files.isDirectory(p)) {
                    try (Stream<Path> c = Files.list(p)) {
                        if (c.findAny().isEmpty()) {
                            groups.computeIfAbsent(rel + "/ (empty folder)", k -> new long[2]);
                        }
                    }
                    continue;
                }
                long size = Files.size(p);
                if (rel.endsWith(".gz")) {
                    gz += size;
                }
                String key = rel.replaceFirst("\\.\\d+((\\.log)?\\.gz)$", ".<i>$1");
                long[] g = groups.computeIfAbsent(key, k -> new long[2]);
                g[0]++;
                g[1] += size;
            }
        }
        groups.forEach((k, g) -> System.out.println("   | " + k + (k.endsWith("(empty folder)") ? "" : "  files=" + g[0] + " bytes=" + g[1])));
        System.out.println("   | total .gz bytes: " + gz);
    }
}
