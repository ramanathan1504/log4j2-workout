import java.lang.reflect.Method;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.Level;

public class Repro {
    public static void main(final String[] args) throws Exception {
        final Filter filter = ThresholdFilter.createFilter(Level.WARN, Filter.Result.ACCEPT, Filter.Result.DENY);
        report("RootLogger.Builder", LoggerConfig.RootLogger.newRootBuilder().setConfig(new NullConfiguration()), filter);
        report("LoggerConfig.Builder (control)", LoggerConfig.newBuilder().setLoggerName("x").setConfig(new NullConfiguration()), filter);
    }

    private static void report(final String label, final Object builder, final Filter filter) throws Exception {
        final Method alias;
        try {
            alias = builder.getClass().getMethod("withFilter", Filter.class);
        } catch (final NoSuchMethodException e) {
            System.out.println("FAIL " + label + ".withFilter(Filter) observed=NoSuchMethodException");
            return;
        }
        final Object returned = alias.invoke(builder, filter);
        final LoggerConfig built = (LoggerConfig) builder.getClass().getMethod("build").invoke(returned);
        final boolean same = built.getFilter() == filter;
        System.out.println((same ? "PASS " : "FAIL ") + label + ".withFilter(Filter) observed=getFilter()==filter is " + same);
    }
}
