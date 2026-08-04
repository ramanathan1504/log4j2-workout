package org.apache.logging.bench.cloud.client;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The config client — the application whose Log4j configuration is under test.
 *
 * <p>Separate package from the server for the reason given on
 * {@code ConfigServerApp}: component scanning would otherwise make each
 * application try to be both.
 */
@SpringBootApplication
public class ClientApp {}
