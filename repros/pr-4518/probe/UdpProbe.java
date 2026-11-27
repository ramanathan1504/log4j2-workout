import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class UdpProbe {
    public static void main(final String[] args) throws Exception {
        final InetAddress lo = InetAddress.getByName("127.0.0.1");
        for (final int size : new int[] {9216, 16384, 65507}) {
            for (final boolean bigRcv : new boolean[] {false, true}) {
                try (DatagramSocket rx = new DatagramSocket(0, lo); DatagramSocket tx = new DatagramSocket()) {
                    if (bigRcv) {
                        rx.setReceiveBufferSize(4 * 65536);
                    }
                    rx.setSoTimeout(1000);
                    String sent;
                    try {
                        tx.send(new DatagramPacket(new byte[size], size, lo, rx.getLocalPort()));
                        sent = "sent";
                    } catch (final Exception e) {
                        sent = "send failed: " + e;
                    }
                    String got;
                    try {
                        final DatagramPacket p = new DatagramPacket(new byte[65536], 65536);
                        rx.receive(p);
                        got = "received " + p.getLength();
                    } catch (final SocketTimeoutException e) {
                        got = "receive timed out";
                    }
                    System.out.printf("java=%s size=%d rcvbuf=%d sndbuf=%d -> %s, %s%n",
                            System.getProperty("java.version"), size, rx.getReceiveBufferSize(),
                            tx.getSendBufferSize(), sent, got);
                }
            }
        }
    }
}
