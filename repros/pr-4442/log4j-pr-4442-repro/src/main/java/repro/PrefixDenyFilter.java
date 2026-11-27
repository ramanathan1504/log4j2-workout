package repro;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;

@Plugin(name = "PrefixDeny", category = Node.CATEGORY, elementType = Filter.ELEMENT_TYPE)
public final class PrefixDenyFilter extends AbstractFilter {

    private final String prefix;

    private PrefixDenyFilter(final String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Result filter(final LogEvent event) {
        return event.getMessage().getFormattedMessage().startsWith(prefix) ? Result.DENY : Result.NEUTRAL;
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder extends AtomicInteger
            implements org.apache.logging.log4j.core.util.Builder<PrefixDenyFilter> {

        @PluginBuilderAttribute
        private String prefix;

        public Builder setPrefix(final String prefix) {
            this.prefix = prefix;
            return this;
        }

        @Override
        public PrefixDenyFilter build() {
            return new PrefixDenyFilter(prefix);
        }
    }
}
