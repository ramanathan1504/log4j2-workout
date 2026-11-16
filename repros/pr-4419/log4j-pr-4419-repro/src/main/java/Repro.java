import java.io.StringWriter;
import java.util.Collections;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class Repro {
    public static void main(String[] args) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configurator.setAllLevels("com.foo", Level.TRACE);
        Configuration config = context.getConfiguration();
        String[][] cases = {
            {"com.foo", "TRACE", "parent"},
            {"com.foo.child", "TRACE", "child"},
            {"com.foobar", "WARN", "sibling"},
            {"com.foobar.child", "ERROR", "sibling-child"}
        };
        for (String[] c : cases) {
            String observed = String.valueOf(config.getLoggers().get(c[0]).getLevel());
            System.out.println((observed.equals(c[1]) ? "PASS " : "FAIL ") + "setAllLevels(com.foo,TRACE) " + c[2]
                    + " logger=" + c[0] + " expected=" + c[1] + " observed=" + observed);
        }

        LoggerConfig mismatched = new LoggerConfig("app", Level.INFO, true);
        config.addLogger("app.service", mismatched);
        context.updateLoggers();
        StringWriter sink = new StringWriter();
        WriterAppender appender = WriterAppender.createAppender(
                PatternLayout.newBuilder().withPattern("%m%n").build(), null, sink, "SINK", false, true);
        appender.start();
        Logger logger = context.getLogger("app.service");
        config.addLoggerAppender(logger, appender);
        logger.info("delivered");
        String observed = sink.toString().contains("delivered") ? "delivered" : "lost";
        System.out.println((observed.equals("delivered") ? "PASS " : "FAIL ")
                + "addLoggerAppender on key app.service holding LoggerConfig(app) expected=delivered observed="
                + observed + " appenders=" + mismatched.getAppenders().keySet());
    }
}
