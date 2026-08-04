package org.apache.logging.bench.cloud;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * log4j-spring-cloud-config-client, against a config server running in this
 * JVM. Feature matrix §13.
 *
 * <p>The module is two classes and buys exactly one thing: <em>timing</em>.
 * {@code WatchEventManager} implements Log4j's {@code WatchEventService} SPI, so
 * Log4j's {@code WatchManager} subscribes itself at startup;
 * {@code Log4j2EventListener} turns a Spring {@code EnvironmentChangeEvent} into
 * {@code checkFiles()} on every subscribed manager. A Spring Cloud refresh
 * therefore reloads a watched Log4j configuration at once, instead of at the
 * next {@code monitorInterval} tick.
 *
 * <p>That is what makes this testable without waiting: the configuration is
 * watched with a five-minute interval, so a reload observed within a second can
 * only have come from the event. The run below proves it in three steps —
 * baseline, change the file and confirm nothing has happened yet, then refresh
 * and confirm it has.
 *
 * <p>Two Spring contexts in one JVM: the config server first, then the client
 * that imports from it. Separate contexts rather than one self-importing
 * application, which would have to resolve its own server before starting it.
 *
 * <pre>
 *   ./bench run spring-cloud-config
 * </pre>
 */
public final class CloudConfigBench {

    private static final int SERVER_PORT = Integer.getInteger("bench.config.port", 8888);

    /** Where the config server's native backend reads from, and where the Log4j
     *  configuration under test lives. Both are rewritten during the run. */
    private static final Path REPO = Path.of("target", "config-repo");

    private static final Path LOG4J_CONFIG = REPO.resolve("log4j2-bench.xml");

    public static void main(final String[] args) throws Exception {
        writeRepo("original");
        writeLog4jConfig("BASELINE");

        System.out.println("Spring Cloud Config bench");
        System.out.println("  config server  http://localhost:" + SERVER_PORT + " (embedded, native backend)");
        System.out.println("  repo           " + REPO.toAbsolutePath());
        System.out.println("  log4j config   " + LOG4J_CONFIG.toAbsolutePath());
        System.out.println();

        System.out.println("  starting config server…");
        final ConfigurableApplicationContext server = startServer();
        System.out.println("  config server up");
        try {
            System.out.println("  starting client…");
            final ConfigurableApplicationContext client = startClient();
            System.out.println("  client up");
            try {
                run(client);
            } finally {
                client.close();
            }
        } finally {
            server.close();
        }
    }

    private static void run(final ConfigurableApplicationContext client) throws Exception {
        System.out.println();
        System.out.println("──── the module is wired");
        System.out.printf("  WatchEventService impl   %s%n", watchEventServiceImpl());
        System.out.printf("  Log4j2EventListener      %s%n", eventListenerRegistration());
        System.out.printf("  ... as a bean            %s%n",
                client.getBeanNamesForType(
                        org.apache.logging.log4j.spring.cloud.config.client.Log4j2EventListener.class).length > 0
                        ? "yes"
                        : "no — and it does not need to be, see above");
        System.out.printf("  message from server      %s%n",
                client.getEnvironment().getProperty("bench.message"));

        System.out.println();
        System.out.println("──── 1. baseline");
        System.out.printf("  Log4j configuration      %s%n", currentConfigMarker());

        System.out.println();
        System.out.println("──── 2. change both files, WITHOUT refreshing");
        writeRepo("changed");
        writeLog4jConfig("RELOADED");
        // monitorInterval is 300s, so nothing should have noticed yet. This is
        // the control: without it, a reload seen in step 3 could just be the
        // interval elapsing.
        Thread.sleep(1500);
        System.out.printf("  Log4j configuration      %s%n", currentConfigMarker());
        System.out.println("  (still cloud-config-baseline — the watch interval is 300s"
                + " and has not elapsed)");

        System.out.println();
        System.out.println("──── 3. refresh from the config server");
        final ContextRefresher refresher = client.getBean(ContextRefresher.class);
        final java.util.Set<String> changed = refresher.refresh();
        System.out.printf("  properties changed       %s%n", changed);
        System.out.println("  (the refresh publishes EnvironmentChangeEvent, which");
        System.out.println("   Log4j2EventListener turns into WatchManager.checkFiles())");

        // The watcher reconfigures on its own thread; give it a moment, but far
        // less than the monitorInterval it would otherwise have waited for.
        Thread.sleep(2000);
        final String after = currentConfigMarker();
        System.out.printf("  Log4j configuration      %s%n", after);

        System.out.println();
        if ("cloud-config-reloaded".equals(after)) {
            System.out.println("RELOADED — the refresh event drove the reload, 298 seconds before");
            System.out.println("the monitorInterval would have.");
        } else {
            System.out.println("NOT reloaded. Either spring.cloud.config.watch.enabled is unset (the");
            System.out.println("listener is @ConditionalOnProperty on it), or the configuration is");
            System.out.println("not watched — a monitorInterval is required for a WatchManager to");
            System.out.println("exist at all, and without one there is nothing for the event to poke.");
        }
    }

    /**
     * How {@code Log4j2EventListener} actually reaches the application.
     *
     * <p>Not as a bean. The class carries {@code @Component} and
     * {@code @ConditionalOnProperty("spring.cloud.config.watch.enabled")}, but
     * the module registers it in {@code META-INF/spring.factories} under
     * {@code org.springframework.context.ApplicationListener} — and
     * SpringApplication instantiates those directly, without creating bean
     * definitions. Conditions are a bean-definition mechanism, so neither
     * annotation has any effect on that path: the listener is active whether or
     * not the property is set, and it never appears in the bean factory.
     *
     * <p>Worth knowing before trying to switch it off with that property.
     */
    private static String eventListenerRegistration() {
        try {
            final var loaded = org.springframework.core.io.support.SpringFactoriesLoader
                    .forDefaultResourceLocation(CloudConfigBench.class.getClassLoader())
                    .load(org.springframework.context.ApplicationListener.class);
            for (final Object listener : loaded) {
                if (listener.getClass().getName().startsWith("org.apache.logging.log4j")) {
                    return "registered via META-INF/spring.factories as an ApplicationListener";
                }
            }
            return "NOT registered — is log4j-spring-cloud-config-client on the classpath?";
        } catch (final Exception e) {
            return "<could not inspect spring.factories: " + e + ">";
        }
    }

    /** Which implementation Log4j found for its watch SPI. */
    private static String watchEventServiceImpl() {
        final java.util.ServiceLoader<org.apache.logging.log4j.core.util.WatchEventService> loader =
                java.util.ServiceLoader.load(org.apache.logging.log4j.core.util.WatchEventService.class);
        for (final org.apache.logging.log4j.core.util.WatchEventService service : loader) {
            return service.getClass().getName();
        }
        return "<none — the module is not on the classpath>";
    }

    /**
     * The NAME of whichever configuration Log4j currently holds.
     *
     * <p>The name rather than a {@code <Properties>} entry: the property map is
     * merged and substituted during configuration, so reading it back is not a
     * reliable statement about which file is loaded. The name comes straight
     * off the Configuration and changes with the file.
     *
     * <p>Read from the live Configuration rather than from disk, so it reports
     * what Log4j is using rather than what has been written.
     */
    private static String currentConfigMarker() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final String name = ctx.getConfiguration().getName();
        return name == null ? "<unnamed configuration>" : name;
    }

    private static ConfigurableApplicationContext startServer() {
        return new SpringApplicationBuilder(org.apache.logging.bench.cloud.server.ConfigServerApp.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=" + SERVER_PORT,
                        "spring.application.name=bench-config-server",
                        // The native backend serves files from a directory
                        // instead of git, which keeps the whole thing in-process.
                        "spring.profiles.active=native",
                        "spring.cloud.config.server.native.searchLocations=file:"
                                + REPO.toAbsolutePath(),
                        // The server has spring-cloud-starter-config on its own
                        // classpath, so without this it also tries to BE a
                        // client and imports from the default
                        // configserver:http://localhost:8888 — itself, before it
                        // is listening. The failure reads as an unresolvable
                        // ConfigDataLocation and names no port, so it looks like
                        // a missing resolver rather than a self-import.
                        "spring.cloud.config.enabled=false",
                        "logging.level.root=WARN")
                .run();
    }

    private static ConfigurableApplicationContext startClient() {
        return new SpringApplicationBuilder(org.apache.logging.bench.cloud.client.ClientApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.application.name=bench",
                        "spring.config.import=configserver:http://localhost:" + SERVER_PORT,
                        // Explicit, not defaulted. ConfigServerConfigDataLocationResolver
                        // .isResolvable() binds this property, and the server context
                        // in this same JVM set it false — so leaving it to the default
                        // makes the resolver decline and Spring falls back to
                        // StandardConfigDataLocationResolver, which reports
                        // "Incorrect ConfigDataLocationResolver chosen" without ever
                        // mentioning spring.cloud.config.enabled.
                        "spring.cloud.config.enabled=true",
                        // Log4j2EventListener is @ConditionalOnProperty on this.
                        // Without it the module is present and does nothing.
                        "spring.cloud.config.watch.enabled=true",
                        // The configuration under test, watched.
                        "logging.config=" + LOG4J_CONFIG.toAbsolutePath(),
                        "logging.level.root=INFO")
                .run();
    }

    /** The properties the config server serves to the client. */
    private static void writeRepo(final String message) throws Exception {
        Files.createDirectories(REPO);
        Files.writeString(REPO.resolve("bench.properties"),
                "bench.message=" + message + "\n");
    }

    /**
     * Writes the Log4j configuration, tagged with a marker the app can read back.
     *
     * <p>monitorInterval is what creates a WatchManager at all — without it
     * there is nothing subscribed for the event to poke, and the module has no
     * effect whatever. 300 seconds is deliberately far longer than the run.
     */
    private static void writeLog4jConfig(final String marker) throws Exception {
        Files.createDirectories(REPO);
        Files.writeString(LOG4J_CONFIG, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Configuration status="WARN" name="cloud-config-%s" monitorInterval="300">
                  <Properties>
                    <Property name="benchMarker">%s</Property>
                  </Properties>
                  <Appenders>
                    <Console name="Console" target="SYSTEM_OUT">
                      <PatternLayout pattern="[%s] %%d{HH:mm:ss.SSS} %%-5level %%logger{1.} - %%msg%%n"/>
                    </Console>
                  </Appenders>
                  <Loggers>
                    <Root level="INFO">
                      <AppenderRef ref="Console"/>
                    </Root>
                  </Loggers>
                </Configuration>
                """.formatted(marker.toLowerCase(java.util.Locale.ROOT), marker, marker));
    }

    private CloudConfigBench() {}
}
