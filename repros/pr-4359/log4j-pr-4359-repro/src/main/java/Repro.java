import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.net.ssl.SslConfigurationFactory;

public class Repro {
    public static void main(String[] args) {
        String storePath = args[0];
        System.setProperty("log4j2.trustStoreLocation", storePath);
        System.setProperty("log4j2.trustStorePassword", "changeit");
        try {
            SslConfiguration configuration = SslConfigurationFactory.getSslConfiguration();
            String type = configuration == null || configuration.getTrustStoreConfig() == null
                    ? "none"
                    : configuration.getTrustStoreConfig().getKeyStoreType();
            boolean loaded = configuration != null
                    && configuration.getTrustStoreConfig() != null
                    && configuration.getTrustStoreConfig().getKeyStore().size() == 1;
            System.out.println((loaded ? "PASS" : "FAIL")
                    + " trustStoreLocation without type: trust store type=" + type + " loaded=" + loaded);
        } catch (Throwable e) {
            System.out.println("FAIL trustStoreLocation without type: threw " + e);
        }
    }
}
