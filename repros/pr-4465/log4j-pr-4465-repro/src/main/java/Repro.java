import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Repro {
    public static void main(String[] args) {
        Handler handler = new ConsoleHandler();
        for (String name : new String[] {"velocity.one", "velocity.two"}) {
            Logger logger = Logger.getLogger(name);
            logger.setLevel(Level.FINE);
            logger.setUseParentHandlers(false);
            logger.addHandler(handler);
            logger.removeHandler(handler);
        }
        Logger probe = Logger.getLogger("velocity.one");
        boolean levelKept = Level.FINE.equals(probe.getLevel());
        System.out.println((levelKept ? "PASS" : "FAIL") + " control getLevel() after setLevel(FINE) observed=" + probe.getLevel()
                + " class=" + probe.getClass().getSimpleName());
    }
}
