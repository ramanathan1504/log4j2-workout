package org.apache.logging.bench.plugins;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;

/**
 * A third-party pattern converter, reachable as {@code %elapsed}.
 *
 * <p>Converters need BOTH {@code @Plugin} and {@code @ConverterKeys} — the
 * plugin name registers the class, the keys are what a pattern can actually
 * write. Omit the keys and the converter is loaded and unreachable, with the
 * pattern rendering the literal text instead.
 *
 * <p>Note {@code category = "Converter"}: converters live in their own plugin
 * category, and one registered under "Core" is never consulted by
 * PatternLayout.
 */
@Plugin(name = "ElapsedConverter", category = "Converter")
@ConverterKeys({"elapsed", "sinceStart"})
public final class ElapsedConverter extends LogEventPatternConverter {

    private static final long STARTED = System.currentTimeMillis();

    private ElapsedConverter() {
        super("Elapsed", "elapsed");
    }

    /**
     * The factory is found reflectively by name — it MUST be called
     * {@code newInstance} and take a {@code String[]}, whatever the converter
     * does with the options. A differently named method compiles, registers and
     * then fails at pattern-parse time.
     */
    public static ElapsedConverter newInstance(final String[] options) {
        return new ElapsedConverter();
    }

    @Override
    public void format(final LogEvent event, final StringBuilder toAppendTo) {
        // Appending to the supplied builder rather than returning a String is
        // the whole reason this API is shaped like this: it is what lets
        // PatternLayout format an event without allocating.
        toAppendTo.append(event.getTimeMillis() - STARTED).append("ms");
    }
}
