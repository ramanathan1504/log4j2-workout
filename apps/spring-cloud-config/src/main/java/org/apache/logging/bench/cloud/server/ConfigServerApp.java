package org.apache.logging.bench.cloud.server;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * The embedded Spring Cloud Config Server.
 *
 * <p>In its OWN package, deliberately. {@code @SpringBootApplication} component
 * scans from its own package downwards, so with both applications nested in one
 * class the client's scan picked up this class's {@code @EnableConfigServer} and
 * tried to start a second config server — failing with "You need to configure a
 * uri for the git repository", which names git rather than the scan that dragged
 * it in.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApp {}
