import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {

    public static class UnreachableNamingServerFactory implements InitialContextFactory {
        @Override
        public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
            throw new NamingException("naming server " + environment.get(Context.PROVIDER_URL) + " unreachable");
        }
    }

    public static void main(String[] args) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LogManager.getLogger(Repro.class).info("startup with the broker down");
        context.stop();
    }
}
