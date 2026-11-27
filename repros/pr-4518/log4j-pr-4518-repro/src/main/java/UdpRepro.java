import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public class UdpRepro {
    public static void main(final String[] args) throws Exception {
        try (DatagramSocket rx = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            rx.setReceiveBufferSize(1 << 20);
            rx.setSoTimeout(2000);
            System.setProperty("udp.port", Integer.toString(rx.getLocalPort()));
            final Logger logger = LogManager.getLogger(UdpRepro.class);
            final char[] big = new char[80_000];
            Arrays.fill(big, 'x');
            logger.info("BIG-START " + new String(big) + " BIG-END");
            logger.info("small-after");
            for (int i = 0; i < 2; i++) {
                final DatagramPacket p = new DatagramPacket(new byte[70_000], 70_000);
                try {
                    rx.receive(p);
                    final String s = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    System.out.printf("datagram %d: %d bytes, starts '%s', ends '%s'%n", i, p.getLength(),
                            s.substring(0, Math.min(40, s.length())).replace('\n', ' '),
                            s.substring(Math.max(0, s.length() - 20)).replace('\n', ' '));
                } catch (final SocketTimeoutException e) {
                    System.out.printf("datagram %d: none received%n", i);
                }
            }
            ((LoggerContext) LogManager.getContext(false)).stop();
        }
    }
}
