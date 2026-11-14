package org.apache.logging.repro;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

public final class Main {
    public static void main(final String[] args) {
        LogManager.getLogger("my.package.here").info("from my.package.here");
        LogManager.getLogger("another.package.here").info("from another.package.here");
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        for (final String name : new String[] {"my.package.here", "another.package.here"}) {
            final LoggerConfig config = context.getConfiguration().getLoggerConfig(name);
            System.out.println("RESULT " + name + " additive=" + config.isAdditive());
        }
    }
}
