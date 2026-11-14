package org.apache.logging.repro;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;

public final class Main {
    public static void main(final String[] args) throws Exception {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        LogManager.getLogger(Main.class).info("hello");
        System.out.printf("log4j-core %s%n", LoggerContext.class.getPackage().getImplementationVersion());
        System.out.printf("%-10s %-10s %-24s %-24s%n", "appender", "configured", "appender.getImmediateFlush", "manager.isImmediateFlush");
        for (final String name : new String[] {"MMF_TRUE", "MMF_FALSE", "RAF_TRUE"}) {
            final AbstractOutputStreamAppender<?> a = ctx.getConfiguration().getAppender(name);
            final Object m = a.getManager();
            Object mf;
            try {
                mf = m.getClass().getMethod("isImmediateFlush").invoke(m);
            } catch (final NoSuchMethodException e) {
                mf = "n/a";
            }
            System.out.printf("%-10s %-10s %-26s %-24s%n", name, name.endsWith("TRUE"), a.getImmediateFlush(), mf);
        }
    }
}
