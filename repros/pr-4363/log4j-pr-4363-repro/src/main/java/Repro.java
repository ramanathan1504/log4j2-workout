import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import javax.naming.Context;
import javax.naming.spi.InitialContextFactory;
import org.apache.logging.log4j.core.net.JndiManager;

public class Repro {

    public static final AtomicInteger FACTORY_CALLS = new AtomicInteger();

    public static class RecordingContextFactory implements InitialContextFactory {
        @Override
        public Context getInitialContext(Hashtable<?, ?> environment) {
            FACTORY_CALLS.incrementAndGet();
            Object providerUrl = environment.get(Context.PROVIDER_URL);
            return (Context) Proxy.newProxyInstance(
                    Context.class.getClassLoader(), new Class<?>[] {Context.class}, (proxy, method, args) -> {
                        if (method.getName().equals("lookup")) {
                            return "ConnectionFactory from " + providerUrl;
                        }
                        return null;
                    });
        }
    }

    public static void main(String[] args) throws Exception {
        JndiManager brokerA = JndiManager.getJndiManager(environment("tcp://broker-a:61616"));
        JndiManager brokerB = JndiManager.getJndiManager(environment("tcp://broker-b:61616"));

        boolean distinct = brokerA != brokerB;
        System.out.println((distinct ? "PASS" : "FAIL") + " two environments give two managers: distinct=" + distinct);

        String resolvedByA = brokerA.lookup("ConnectionFactory");
        String resolvedByB = brokerB.lookup("ConnectionFactory");
        System.out.println(("ConnectionFactory from tcp://broker-a:61616".equals(resolvedByA) ? "PASS" : "FAIL")
                + " control manager A resolves from: " + resolvedByA);
        System.out.println(("ConnectionFactory from tcp://broker-b:61616".equals(resolvedByB) ? "PASS" : "FAIL")
                + " manager B resolves from: " + resolvedByB);
        System.out.println((FACTORY_CALLS.get() == 2 ? "PASS" : "FAIL")
                + " InitialContextFactory calls=" + FACTORY_CALLS.get() + " (expected 2)");
        brokerA.close();
        brokerB.close();
    }

    private static Properties environment(String providerUrl) {
        return JndiManager.createProperties(
                RecordingContextFactory.class.getName(), providerUrl, null, null, null, null);
    }
}
