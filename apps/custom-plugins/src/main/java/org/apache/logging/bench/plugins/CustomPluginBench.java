package org.apache.logging.bench.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * Plugin authoring, end to end. Feature matrix §13.
 *
 * <p>Checks the two things that can fail independently:
 *
 * <ol>
 *   <li><strong>Build time.</strong> log4j-plugin-processor must have seen the
 *       {@code @Plugin} annotations and written {@code Log4j2Plugins.dat}.
 *       Without it Log4j falls back to scanning packages by name, which is
 *       slower and often finds nothing inside a shaded jar — the classic
 *       "works in the IDE, not in production".</li>
 *   <li><strong>Run time.</strong> the plugins must actually resolve from a
 *       configuration, in the right category, with the right names.</li>
 * </ol>
 *
 * <p>Either can pass while the other fails, so both are reported separately
 * rather than inferred from "the log looks right".
 *
 * <pre>
 *   ./bench run custom-plugins --config xml/custom-plugins
 * </pre>
 */
public final class CustomPluginBench {

    private static final String DAT =
            "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat";

    private static final Logger log = LogManager.getLogger(CustomPluginBench.class);

    public static void main(final String[] args) {
        System.out.println("Log4j bench — custom plugin authoring");
        reportDescriptor();
        reportRegistrations();

        System.out.println();
        System.out.println("──── logging through the custom plugins");
        // The filter accepts one event in two, so half of these are dropped
        // before they reach the appender — the counts printed at shutdown are
        // the assertion.
        for (int i = 1; i <= 6; i++) {
            log.info("event {} of 6", Integer.valueOf(i));
        }
        log.error("an error, to show %elapsed and ${bench:} on a second level");

        LogManager.shutdown();
        System.out.println();
        System.out.println("Custom plugin bench complete.");
    }

    /** Build-time half: is the generated descriptor actually in the artifact? */
    private static void reportDescriptor() {
        System.out.println("  plugin descriptor");
        final ClassLoader loader = CustomPluginBench.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(DAT)) {
            if (in == null) {
                System.out.println("    " + DAT);
                System.out.println("    NOT FOUND — the annotation processor did not run.");
                System.out.println("    Log4j will fall back to package scanning, which works");
                System.out.println("    here and frequently does not inside a shaded jar.");
            } else {
                System.out.printf("    found, %d bytes%n", in.readAllBytes().length);
            }
        } catch (final IOException e) {
            System.out.println("    unreadable: " + e);
        }
        // Where it came from matters too: log4j-core ships its own copy, so
        // finding *a* descriptor is not proof that ours was generated.
        System.out.println("    sources on the classpath:");
        try {
            final java.util.Enumeration<java.net.URL> all = loader.getResources(DAT);
            while (all.hasMoreElements()) {
                System.out.println("      " + all.nextElement());
            }
        } catch (final IOException e) {
            System.out.println("      <unreadable: " + e + ">");
        }
    }

    /** Run-time half: did Log4j register them, under the expected categories? */
    private static void reportRegistrations() {
        System.out.println("  registrations");
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);

        report(ctx, "Core", "Counting");
        report(ctx, "Core", "EveryNthFilter");
        report(ctx, "Lookup", "bench");
        report(ctx, "Converter", "ElapsedConverter");
    }

    /**
     * Reports where a plugin registered, on either Log4j line.
     *
     * <p>The two lines expose registrations through entirely different types, so
     * this reflects rather than compiles against either. 2.x has
     * {@code core.config.plugins.util.PluginManager}, populated by
     * {@code collectPlugins()}. 3.x moved the plugin system into log4j-plugins
     * and replaced it with {@code plugins.model.PluginRegistry}, whose
     * {@code getNamespace(String)} returns a {@code PluginNamespace} that is
     * itself a collection.
     *
     * <p>Compiling against 2.x's PluginManager is what made this app die on 3.x
     * with NoClassDefFoundError, after the descriptor had already been found —
     * the plugin machinery was fine, the bench's own introspection was not.
     */
    private static void report(final LoggerContext ctx, final String category, final String name) {
        final String key = name.toLowerCase(java.util.Locale.ROOT);
        String where = tryLog4j2(category, key);
        if (where == null) {
            where = tryLog4j3(category, key);
        }
        System.out.printf("    %-10s %-18s %s%n", category, name,
                where == null ? "NOT REGISTERED" : where);
    }

    /** 2.x: PluginManager per category. Keys are lower-cased, so look up by that. */
    private static String tryLog4j2(final String category, final String key) {
        try {
            final Class<?> mgrClass =
                    Class.forName("org.apache.logging.log4j.core.config.plugins.util.PluginManager");
            final Object manager = mgrClass.getConstructor(String.class).newInstance(category);
            mgrClass.getMethod("collectPlugins").invoke(manager);
            final Object plugins = mgrClass.getMethod("getPlugins").invoke(manager);
            if (!(plugins instanceof Map)) {
                return null;
            }
            final Object type = ((Map<?, ?>) plugins).get(key);
            return type == null ? null : pluginClassName(type);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** 3.x: PluginRegistry -> PluginNamespace, from log4j-plugins. */
    private static String tryLog4j3(final String category, final String key) {
        try {
            final Class<?> regClass =
                    Class.forName("org.apache.logging.log4j.plugins.model.PluginRegistry");
            final Object registry = regClass
                    .getConstructor(ClassLoader.class)
                    .newInstance(CustomPluginBench.class.getClassLoader());
            final Object namespace =
                    regClass.getMethod("getNamespace", String.class).invoke(registry, category);
            if (namespace == null) {
                return null;
            }
            final Object type = namespace.getClass()
                    .getMethod("get", String.class).invoke(namespace, key);
            return type == null ? null : pluginClassName(type);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Both lines expose getPluginClass() on their PluginType. */
    private static String pluginClassName(final Object pluginType) {
        try {
            final Object cls = pluginType.getClass().getMethod("getPluginClass").invoke(pluginType);
            return cls instanceof Class ? ((Class<?>) cls).getName() : String.valueOf(cls);
        } catch (final ReflectiveOperationException e) {
            return pluginType.toString();
        }
    }

    private CustomPluginBench() {}
}
