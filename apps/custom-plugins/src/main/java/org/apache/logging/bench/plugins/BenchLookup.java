package org.apache.logging.bench.plugins;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.AbstractLookup;
import org.apache.logging.log4j.core.lookup.StrLookup;

/**
 * A third-party lookup, reachable as <code>${bench:key}</code>.
 *
 * <p>The plugin name IS the prefix, so this class is what makes
 * {@code ${bench:...}} resolve. Category matters as much as the name: a lookup
 * registered under the wrong category is loaded, listed, and never consulted.
 */
@Plugin(name = "bench", category = StrLookup.CATEGORY)
public final class BenchLookup extends AbstractLookup {

    @Override
    public String lookup(final LogEvent event, final String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case "version":
                return "1.0.0-SNAPSHOT";
            case "hostRole":
                return "bench";
            case "eventLevel":
                // Lookups get the event, so a custom lookup can depend on it —
                // which also means it must tolerate a null event, since the
                // same lookup is called during configuration when there is none.
                return event == null ? "<config-time>" : event.getLevel().name();
            default:
                // Returning null leaves the expression verbatim in the output,
                // the same convention as the built-in lookups.
                return null;
        }
    }
}
