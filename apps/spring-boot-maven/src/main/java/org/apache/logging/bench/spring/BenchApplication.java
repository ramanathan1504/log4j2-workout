package org.apache.logging.bench.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;

/**
 * Spring Boot front-end for the bench. Runs the same scenario classes as the
 * core-java module, but through a real servlet stack, so Spring-specific surface
 * gets exercised too: the {@code SpringProfile} arbiter, the {@code ${spring:}}
 * lookup, actuator's runtime level changes, and Log4j's Spring Boot integration.
 */
// The MongoDB driver is on the classpath because log4j-mongodb (a Log4j appender)
// pulls it in. Spring Boot sees the driver and auto-configures a client that
// tries to reach localhost:27017 at startup, which is nothing to do with logging.
// Excluded so the app boots without a Mongo container running.
@SpringBootApplication(exclude = {MongoAutoConfiguration.class, MongoReactiveAutoConfiguration.class})
public class BenchApplication {

    public static void main(final String[] args) {
        SpringApplication.run(BenchApplication.class, args);
    }
}
