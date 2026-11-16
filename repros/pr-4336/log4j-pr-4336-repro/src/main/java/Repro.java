import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.SocketAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

public class Repro {
    public static void main(String[] args) throws Exception {
        int oversized = Integer.parseInt(args[0]);
        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            receiver.setReceiveBufferSize(1 << 20);
            receiver.setSoTimeout(1500);
            ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
            builder.setStatusLevel(Level.WARN);
            builder.add(builder.newAppender("Udp", "Socket")
                    .addAttribute("host", "127.0.0.1")
                    .addAttribute("port", receiver.getLocalPort())
                    .addAttribute("protocol", "UDP")
                    .add(builder.newLayout("PatternLayout").addAttribute("pattern", "%m%n")));
            builder.add(builder.newRootLogger(Level.INFO).add(builder.newAppenderRef("Udp")));
            LoggerContext context = Configurator.initialize(builder.build());
            Logger logger = LogManager.getLogger(Repro.class);
            logger.info("small-before");
            char[] big = new char[oversized];
            Arrays.fill(big, 'x');
            logger.info(new String(big));
            logger.info("small-after");
            Configurator.shutdown(context);
            List<Integer> sizes = new ArrayList<>();
            List<String> heads = new ArrayList<>();
            byte[] buffer = new byte[70_000];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    receiver.receive(packet);
                } catch (SocketTimeoutException e) {
                    break;
                }
                sizes.add(packet.getLength());
                heads.add(new String(packet.getData(), 0, Math.min(12, packet.getLength()), "US-ASCII").trim());
            }
            System.out.println("received datagrams: sizes=" + sizes + " heads=" + heads);
        }
    }
}
