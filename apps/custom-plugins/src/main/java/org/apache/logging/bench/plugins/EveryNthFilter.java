package org.apache.logging.bench.plugins;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * A third-party filter: accepts one event in every N, and denies the rest.
 *
 * <p>Sampling rather than thresholding, which is a shape Log4j does not ship —
 * {@code BurstFilter} rate-limits by time, this one by count.
 *
 * <p>Written with a static {@code @PluginFactory} method rather than a builder,
 * deliberately: both forms are supported and both appear in real plugins, so
 * the bench exercises each once. Note that the factory must accept
 * {@code onMatch}/{@code onMismatch} itself — unlike a builder extending
 * {@code AbstractFilterBuilder}, nothing supplies them for free.
 */
@Plugin(name = "EveryNthFilter", category = "Core", elementType = Filter.ELEMENT_TYPE,
        printObject = true)
public final class EveryNthFilter extends AbstractFilter {

    private final long interval;
    private final AtomicLong seen = new AtomicLong();

    private EveryNthFilter(final long interval, final Result onMatch, final Result onMismatch) {
        super(onMatch, onMismatch);
        this.interval = interval;
    }

    private Result decide() {
        // Every filter method funnels here. AbstractFilter declares a dozen
        // overloads and a plugin that implements only filter(LogEvent) silently
        // does nothing for the parameterised call sites, which is the usual bug
        // in a hand-written filter.
        return seen.getAndIncrement() % interval == 0 ? onMatch : onMismatch;
    }

    @Override
    public Result filter(final LogEvent event) {
        return decide();
    }

    @Override
    public Result filter(final org.apache.logging.log4j.core.Logger logger,
                         final org.apache.logging.log4j.Level level,
                         final org.apache.logging.log4j.Marker marker,
                         final String msg,
                         final Object... params) {
        return decide();
    }

    @Override
    public Result filter(final org.apache.logging.log4j.core.Logger logger,
                         final org.apache.logging.log4j.Level level,
                         final org.apache.logging.log4j.Marker marker,
                         final Object msg,
                         final Throwable t) {
        return decide();
    }

    @Override
    public Result filter(final org.apache.logging.log4j.core.Logger logger,
                         final org.apache.logging.log4j.Level level,
                         final org.apache.logging.log4j.Marker marker,
                         final org.apache.logging.log4j.message.Message msg,
                         final Throwable t) {
        return decide();
    }

    @Override
    public String toString() {
        return "EveryNthFilter(interval=" + interval + ")";
    }

    @PluginFactory
    public static EveryNthFilter createFilter(
            @PluginAttribute(value = "interval", defaultLong = 2L) final long interval,
            @PluginAttribute("onMatch") final Result match,
            @PluginAttribute("onMismatch") final Result mismatch) {
        return new EveryNthFilter(
                interval < 1 ? 1 : interval,
                match == null ? Result.NEUTRAL : match,
                mismatch == null ? Result.DENY : mismatch);
    }
}
