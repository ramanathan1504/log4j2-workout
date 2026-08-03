package org.apache.logging.bench.scenario;

import org.apache.logging.bench.Scenario;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

/**
 * Builds a configuration entirely in code via {@link ConfigurationBuilder} — no
 * config file involved. This is the path libraries and embedders use, and it is
 * a distinct code path from XML/JSON/YAML/properties parsing, so it needs its own
 * coverage. Feature matrix §11.
 */
public final class ProgrammaticScenario implements Scenario {

    @Override
    public String name() {
        return "programmatic";
    }

    @Override
    public String describes() {
        return "ConfigurationBuilder API — build a full configuration in code, no config file";
    }

    @Override
    public void run() {
        final ConfigurationBuilder<BuiltConfiguration> builder =
                ConfigurationBuilderFactory.newConfigurationBuilder();

        builder.setStatusLevel(Level.WARN);
        builder.setConfigurationName("BenchProgrammatic");

        // Console appender with an inline pattern layout
        final LayoutComponentBuilder pattern = builder.newLayout("PatternLayout")
                .addAttribute("pattern", "%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n");

        final AppenderComponentBuilder console = builder.newAppender("BuiltConsole", "Console")
                .addAttribute("target", "SYSTEM_OUT")
                .add(pattern);

        // A threshold filter attached to the appender
        console.add(builder.newFilter("ThresholdFilter", org.apache.logging.log4j.core.Filter.Result.ACCEPT,
                        org.apache.logging.log4j.core.Filter.Result.DENY)
                .addAttribute("level", Level.DEBUG));

        builder.add(console);

        // A rolling file appender assembled component by component
        final AppenderComponentBuilder rolling = builder.newAppender("BuiltRolling", "RollingFile")
                .addAttribute("fileName", "logs/programmatic/app.log")
                .addAttribute("filePattern", "logs/programmatic/app-%d{yyyy-MM-dd}-%i.log.gz")
                .add(builder.newLayout("JsonTemplateLayout")
                        .addAttribute("eventTemplateUri", "classpath:EcsLayout.json"))
                .addComponent(builder.newComponent("Policies")
                        .addComponent(builder.newComponent("SizeBasedTriggeringPolicy")
                                .addAttribute("size", "10KB"))
                        .addComponent(builder.newComponent("TimeBasedTriggeringPolicy")
                                .addAttribute("interval", 1)))
                .addComponent(builder.newComponent("DefaultRolloverStrategy")
                        .addAttribute("max", 10));
        builder.add(rolling);

        // Custom level, a named logger, and the root logger
        builder.add(builder.newCustomLevel("AUDIT", 350));

        builder.add(builder.newLogger("org.apache.logging.bench.audit", Level.forName("AUDIT", 350))
                .add(builder.newAppenderRef("BuiltRolling"))
                .addAttribute("additivity", false));

        final RootLoggerComponentBuilder root = builder.newRootLogger(Level.DEBUG)
                .add(builder.newAppenderRef("BuiltConsole"));
        builder.add(root);

        // Swap the running configuration for the one just built
        try (LoggerContext ctx = Configurator.initialize(builder.build())) {
            final Logger log = LogManager.getLogger(ProgrammaticScenario.class);
            log.debug("Built entirely in code — DEBUG passes the threshold filter");
            log.info("Built entirely in code — INFO");
            log.error("Built entirely in code — ERROR with a throwable", new IllegalStateException("synthetic"));

            final Logger audit = LogManager.getLogger("org.apache.logging.bench.audit");
            audit.log(Level.forName("AUDIT", 350), "Custom AUDIT level routed to the rolling appender only");

            System.out.println("Programmatic configuration name: " + ctx.getConfiguration().getName());
        }
    }
}
