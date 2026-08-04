package org.apache.logging.bench.plugins;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

/**
 * A third-party appender, written the way the documentation says to write one.
 *
 * <p>The point of this module is that {@code @Plugin} alone is not enough: the
 * class is only discoverable because log4j-plugin-processor sees the annotation
 * at compile time and writes {@code Log4j2Plugins.dat}. Delete that file from
 * the build output and this appender stops existing as far as a configuration
 * is concerned, with the only symptom being "Unable to locate plugin type".
 *
 * <p>Uses the Builder form rather than a static {@code @PluginFactory} method,
 * which is the one Log4j has preferred since 2.7 — a factory method with more
 * than a handful of parameters becomes unreadable and cannot express defaults or
 * validation.
 */
@Plugin(name = "Counting", category = "Core", elementType = "appender", printObject = true)
public final class CountingAppender extends AbstractAppender {

    private final AtomicLong count = new AtomicLong();
    private final boolean echo;

    private CountingAppender(
            final String name,
            final Filter filter,
            final Layout<? extends Serializable> layout,
            final boolean ignoreExceptions,
            final boolean echo) {
        super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
        this.echo = echo;
    }

    @Override
    public void append(final LogEvent event) {
        final long seen = count.incrementAndGet();
        if (echo) {
            // Through the layout, so a custom appender that ignores its layout —
            // a common mistake — is visible here rather than assumed correct.
            System.out.print("[counting #" + seen + "] "
                    + new String(getLayout().toByteArray(event)));
        }
    }

    @Override
    public boolean stop(final long timeout, final java.util.concurrent.TimeUnit timeUnit) {
        final boolean stopped = super.stop(timeout, timeUnit);
        System.out.println("[counting] " + getName() + " saw " + count.get() + " event(s)");
        return stopped;
    }

    public long getCount() {
        return count.get();
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * @Required on name is what turns a missing attribute into a clear
     * "Builder contains an invalid element or attribute" instead of a
     * NullPointerException halfway through configuration.
     */
    public static final class Builder
            implements org.apache.logging.log4j.core.util.Builder<CountingAppender> {

        @PluginBuilderAttribute
        @Required(message = "A Counting appender requires a name")
        private String name;

        @PluginBuilderAttribute
        private boolean echo = true;

        @PluginBuilderAttribute
        private boolean ignoreExceptions = true;

        @PluginElement("Layout")
        private Layout<? extends Serializable> layout;

        @PluginElement("Filter")
        private Filter filter;

        public Builder setName(final String name) {
            this.name = name;
            return this;
        }

        public Builder setEcho(final boolean echo) {
            this.echo = echo;
            return this;
        }

        /**
         * Required, not optional. log4j-plugin-processor refuses to compile a
         * {@code @PluginBuilderAttribute} field with no public setter:
         *
         * <pre>
         *   The field `ignoreExceptions` does not have a public setter, Note that
         *   {@code @SuppressWarnings("log4j.public.setter")} can be used on the
         *   field to suppress the compilation error.
         * </pre>
         *
         * Worth knowing before writing a plugin: this is one of the few plugin
         * mistakes caught at build time rather than as a runtime surprise.
         */
        public Builder setIgnoreExceptions(final boolean ignoreExceptions) {
            this.ignoreExceptions = ignoreExceptions;
            return this;
        }

        public Builder setLayout(final Layout<? extends Serializable> layout) {
            this.layout = layout;
            return this;
        }

        public Builder setFilter(final Filter filter) {
            this.filter = filter;
            return this;
        }

        @Override
        public CountingAppender build() {
            // A null layout is legal in the plugin API and fatal at append time,
            // so supply the default rather than discovering it per event.
            final Layout<? extends Serializable> effective = layout == null
                    ? org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout()
                    : layout;
            return new CountingAppender(name, filter, effective, ignoreExceptions, echo);
        }
    }
}
