package org.apache.logging.bench.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot front-end for the bench. Runs the same scenario classes as the
 * core-java module, but through a real servlet stack, so Spring-specific surface
 * gets exercised too: the {@code SpringProfile} arbiter, the {@code ${spring:}}
 * lookup, actuator's runtime level changes, and Log4j's Spring Boot integration.
 */
@SpringBootApplication
public class BenchApplication {

    public static void main(final String[] args) {
        SpringApplication.run(BenchApplication.class, args);
    }
}
