import java.io.FileInputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;

public class Repro {
    public static void main(final String[] args) throws Exception {
        final String serverKeyStore = args[0];
        final char[] password = "changeit".toCharArray();
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(serverKeyStore)) {
            keyStore.load(in, password);
        }
        final KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, password);
        final SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagers.getKeyManagers(), null, null);
        final SSLServerSocket server =
                (SSLServerSocket) serverContext.getServerSocketFactory().createServerSocket(0);
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger refused = new AtomicInteger();
        final StringBuilder refusals = new StringBuilder();
        final Thread acceptor = new Thread(() -> acceptLoop(server, completed, refused, refusals), "tls-server");
        acceptor.setDaemon(true);
        acceptor.start();

        System.setProperty("repro.port", String.valueOf(server.getLocalPort()));
        final LoggerContext context = Configurator.initialize("repro", "log4j2-cassandra.xml");
        LogManager.getLogger(Repro.class).info("hello");
        final long deadline = System.currentTimeMillis() + 8000;
        while (completed.get() + refused.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        Thread.sleep(500);
        System.out.printf("handshakesCompleted=%d handshakesRefused=%d firstRefusal=%s%n",
                completed.get(), refused.get(), refusals.length() == 0 ? "-" : refusals);
        context.stop();
        server.close();
        System.exit(0);
    }

    private static void acceptLoop(
            final ServerSocket server,
            final AtomicInteger completed,
            final AtomicInteger refused,
            final StringBuilder refusals) {
        while (!server.isClosed()) {
            try (SSLSocket socket = (SSLSocket) server.accept()) {
                try {
                    socket.startHandshake();
                    completed.incrementAndGet();
                } catch (final javax.net.ssl.SSLException e) {
                    if (refusals.length() == 0) {
                        refusals.append(e.getMessage());
                    }
                    refused.incrementAndGet();
                }
            } catch (final Exception ignored) {
                return;
            }
        }
    }
}
