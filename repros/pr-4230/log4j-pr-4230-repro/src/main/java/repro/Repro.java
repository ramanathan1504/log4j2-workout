package repro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.OrderComparator;
import org.apache.logging.log4j.plugins.Ordered;

public class Repro {

    @Order(5)
    static final class LegacyOrderFive {}

    @Ordered(10)
    static final class NewOrderedTen {}

    static final class Unannotated {}

    public static void main(final String[] args) {
        final List<Class<?>> classes = new ArrayList<>(Arrays.asList(Unannotated.class, LegacyOrderFive.class, NewOrderedTen.class));
        classes.sort(OrderComparator.getInstance());
        final String sorted = classes.stream().map(Class::getSimpleName).collect(Collectors.joining(","));
        final String expected = "NewOrderedTen,LegacyOrderFive,Unannotated";
        System.out.println((expected.equals(sorted) ? "PASS" : "FAIL") + " OrderComparator sort of [@Order(5), @Ordered(10), none]: " + sorted);

        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final String configName = context.getConfiguration().getName();
        System.out.println((ReproXmlConfigurationFactory.used ? "PASS" : "FAIL") + " @Ordered(10) custom ConfigurationFactory chosen for log4j2.xml: used=" + ReproXmlConfigurationFactory.used + " configuration=" + configName);
    }
}
