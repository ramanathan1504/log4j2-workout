import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public class RollingRepro {

    public static void main(String[] args) throws Exception {
        long runMillis = Long.parseLong(args[0]);
        Logger logger = LogManager.getLogger(RollingRepro.class);
        String line = new String(new char[200]).replace('\0', 'x');
        long end = System.currentTimeMillis() + runMillis;
        while (System.currentTimeMillis() < end) {
            logger.info(line);
            Thread.sleep(2);
        }
        ((LoggerContext) LogManager.getContext(false)).stop();
        Thread.sleep(500);
        for (String dir : args[1].split(",")) {
            File[] files = new File(dir).listFiles((d, n) -> n.endsWith(".gz"));
            Arrays.sort(files);
            Map<String, Integer> perPeriod = new TreeMap<>();
            long total = 0;
            for (File f : files) {
                String n = f.getName();
                String period = n.replaceAll("^.*\\.(\\d{4}(-\\d\\d)+)\\.\\d+\\.gz$", "$1");
                perPeriod.merge(period, 1, Integer::sum);
                total += f.length();
            }
            System.out.println("DIR " + dir + " gz=" + files.length + " bytes=" + total + " " + Arrays.toString(Arrays.stream(files).map(File::getName).toArray()));
            System.out.println("DIR " + dir + " per-period=" + perPeriod);
        }
    }
}
