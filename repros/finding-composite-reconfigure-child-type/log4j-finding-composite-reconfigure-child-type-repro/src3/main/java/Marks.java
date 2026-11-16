import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.plugins.Node;

public final class Marks {
    public static final String SETUP_LOGGER = "from.setup";
    public static final String DO_CONFIGURE_LOGGER = "programmatic";

    private Marks() {}

    static void addSetupNode(Node root) {
        for (Node loggers : root.getChildren()) {
            if (!"Loggers".equalsIgnoreCase(loggers.getName())) {
                continue;
            }
            Node template = null;
            for (Node child : loggers.getChildren()) {
                if (SETUP_LOGGER.equals(child.getAttributes().get("name"))) {
                    return;
                }
                if ("Logger".equalsIgnoreCase(child.getName())) {
                    template = child;
                }
            }
            if (template != null) {
                Node node = new Node(loggers, "Logger", template.getType());
                node.getAttributes().put("name", SETUP_LOGGER);
                node.getAttributes().put("level", "trace");
                loggers.getChildren().add(node);
            }
        }
    }

    static void addLogger(Configuration target) {
        if (target.getLoggers().containsKey(DO_CONFIGURE_LOGGER)) {
            return;
        }
        LoggerConfig loggerConfig = new LoggerConfig(DO_CONFIGURE_LOGGER, Level.TRACE, true, target);
        target.addLogger(DO_CONFIGURE_LOGGER, loggerConfig);
    }
}
