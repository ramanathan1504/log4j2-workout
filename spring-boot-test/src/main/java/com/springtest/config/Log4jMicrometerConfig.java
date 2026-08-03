package com.springtest.config;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.log4j2.MicrometerTraceContextProvider;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Log4jMicrometerConfig {

    private final Tracer tracer;

    public Log4jMicrometerConfig(Tracer tracer) {
        this.tracer = tracer;
    }

    @PostConstruct
    public void init() {
        // Hand the tracer over to our Log4j SPI!
        MicrometerTraceContextProvider.setTracer(tracer);
        System.out.println(">>> TRACER SUCCESSFULLY BRIDGED TO LOG4J SPI <<<");
    }
}