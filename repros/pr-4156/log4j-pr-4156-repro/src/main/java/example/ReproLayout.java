package example;

import org.apache.logging.log4j.plugins.Configurable;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.plugins.PluginFactory;

@Configurable(elementType = "layout")
@Plugin("ReproLayout")
public final class ReproLayout {

    private ReproLayout() {}

    @PluginFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder implements org.apache.logging.log4j.plugins.util.Builder<ReproLayout> {

        @PluginBuilderAttribute
        private String prefix;

        @PluginBuilderAttribute
        private String fieldWithoutSetter;

        @PluginBuilderAttribute
        @SuppressWarnings("log4j.public.setter")
        private String suppressedFieldWithoutSetter;

        public Builder setPrefix(final String prefix) {
            this.prefix = prefix;
            return this;
        }

        @Override
        public ReproLayout build() {
            return new ReproLayout();
        }
    }
}
