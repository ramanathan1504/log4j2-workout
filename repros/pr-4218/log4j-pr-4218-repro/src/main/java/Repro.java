import com.sun.management.UnixOperatingSystemMXBean;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.jmx.LoggerContextAdmin;

public class Repro {

    static final List<InputStream> CAPTURED = new ArrayList<>();

    static final class NonConsumingConfigurationFactory extends ConfigurationFactory {
        @Override
        protected String[] getSupportedTypes() {
            return new String[] {"*"};
        }

        @Override
        public Configuration getConfiguration(final LoggerContext loggerContext, final ConfigurationSource source) {
            CAPTURED.add(source.getInputStream());
            return new DefaultConfiguration();
        }

        @Override
        public Configuration getConfiguration(final LoggerContext loggerContext, final String name, final URI configLocation) {
            return new DefaultConfiguration();
        }
    }

    public static void main(final String[] args) throws Exception {
        final int calls = 50;
        final Path config = Files.createTempFile("pr4218-", ".xml");
        Files.write(config, "<Configuration/>".getBytes(StandardCharsets.UTF_8));
        ConfigurationFactory.setConfigurationFactory(new NonConsumingConfigurationFactory());
        final LoggerContext context = new LoggerContext("pr4218");
        context.start(new DefaultConfiguration());
        final LoggerContextAdmin admin = new LoggerContextAdmin(context, Runnable::run);
        admin.setConfigLocationUri(config.toString());
        final long before = openDescriptors();
        for (int i = 1; i < calls; i++) {
            admin.setConfigLocationUri(config.toString());
        }
        final long after = openDescriptors();
        int stillOpen = 0;
        for (final InputStream in : CAPTURED) {
            try {
                in.read();
                stillOpen++;
            } catch (final IOException closed) {
            }
        }
        final long growth = after - before;
        System.out.println((stillOpen == 0 ? "PASS" : "FAIL") + " caller-owned streams left open after setConfigLocationUri: " + stillOpen + " of " + CAPTURED.size());
        System.out.println((growth < 10 ? "PASS" : "FAIL") + " open file descriptor growth over " + (calls - 1) + " further calls: " + growth + " (before=" + before + " after=" + after + ")");
        context.stop();
        Files.deleteIfExists(config);
    }

    private static long openDescriptors() {
        return ((UnixOperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getOpenFileDescriptorCount();
    }
}
