import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.jmx.AppenderDynamicMBean;
import org.apache.log4j.jmx.LoggerDynamicMBean;

public class Repro {
    public static void main(String[] args) throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Logger logger = Logger.getLogger("jmxrepro");

        ObjectName loggerName = new ObjectName("log4j:logger=jmxrepro");
        server.registerMBean(new LoggerDynamicMBean(logger), loggerName);
        String addAppender = invoke(server, loggerName, "addAppender",
                new Object[] {"com.example.NoSuchAppender", "ghost"}, new String[] {"java.lang.String", "java.lang.String"});
        System.out.println((addAppender.startsWith("MBeanException") ? "PASS " : "FAIL ") + "addAppender-bad-class observed=" + addAppender);

        StringWriter sink = new StringWriter();
        WriterAppender appender = new WriterAppender(new PatternLayout("%m%n"), sink);
        appender.setName("sink");
        ObjectName appenderName = new ObjectName("log4j:appender=sink");
        server.registerMBean(new AppenderDynamicMBean(appender), appenderName);
        String setLayout = invoke(server, appenderName, "setLayout",
                new Object[] {"com.example.NoSuchLayout"}, new String[] {"java.lang.String"});
        System.out.println((setLayout.startsWith("MBeanException") ? "PASS " : "FAIL ") + "setLayout-bad-class observed=" + setLayout);
        boolean layoutKept = appender.getLayout() != null;
        System.out.println((layoutKept ? "PASS " : "FAIL ") + "setLayout-bad-class-keeps-layout observed=" + appender.getLayout());
        logger.setAdditivity(false);
        logger.addAppender(appender);
        logger.error("after-failed-setLayout");
        String written = sink.toString().trim();
        System.out.println((written.equals("after-failed-setLayout") ? "PASS " : "FAIL ") + "appender-still-writes observed=[" + written + "]");
    }

    static String invoke(MBeanServer server, ObjectName name, String op, Object[] params, String[] signature) {
        try {
            return "returned " + server.invoke(name, op, params, signature);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            return e.getClass().getSimpleName() + (cause != null ? " cause=" + cause.getClass().getSimpleName() + ": " + cause.getMessage() : "");
        }
    }
}
