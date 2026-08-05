package org.apache.logging.bench.spring;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Drives the bench endpoints over real HTTP and then shuts the application down.
 *
 * <p>Without this the app cannot appear in a matrix sweep at all. {@code main}
 * is {@code SpringApplication.run} on a web application, so it serves until it
 * is killed — a sweep cell never returns, and one app silently stalls the whole
 * run. {@code ./bench matrix} bounds every cell now, but a bounded hang is still
 * a FAIL; this makes the app finish on its own and report honestly.
 *
 * <p>It exercises the servlet stack rather than skipping it: the scenarios run
 * through a real request against the running connector, which is the point of
 * having a Spring app in the bench at all. Interactive use is unaffected —
 * without {@code -Dbench.selfTest=true} the app serves as before.
 */
@Component
@ConditionalOnProperty(name = "bench.selfTest", havingValue = "true")
public class SelfTestRunner implements ApplicationRunner {

    private final ApplicationContext context;
    private final int port;

    public SelfTestRunner(final ApplicationContext context,
            @Value("${server.port:8080}") final int port) {
        this.context = context;
        this.port = port;
    }

    @Override
    public void run(final ApplicationArguments args) {
        int status = 0;
        // Not try-with-resources: HttpClient only became AutoCloseable in Java
        // 21, and this module is compiled at release 17.
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
        try {
            // /bench lists the scenarios; running one exercises the same
            // scenario classes core-java uses, but through the servlet stack.
            status = Math.max(status, get(client, "/bench"));
            status = Math.max(status, get(client, "/bench/config"));
            status = Math.max(status, get(client, "/bench/lookups"));
        } catch (final Exception e) {
            System.err.println("self-test failed: " + e);
            status = 1;
        }
        // Exit with the context's own code so a failed self-test is a failed
        // cell rather than a green one.
        final int code = status;
        System.exit(SpringApplication.exit(context, () -> code));
    }

    private int get(final HttpClient client, final String path) throws Exception {
        final HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        System.out.printf("  self-test GET %-16s -> %d%n", path, res.statusCode());
        return res.statusCode() < 400 ? 0 : 1;
    }
}
