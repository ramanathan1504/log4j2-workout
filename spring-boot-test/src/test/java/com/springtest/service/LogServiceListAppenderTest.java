package com.springtest.service;

import com.springtest.SpringBootTestApp;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SpringBootTestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LogServiceListAppenderTest {

    @Autowired
    private LogService logService;

    private LoggerContext loggerContext;
    private Configuration configuration;
    private LoggerConfig loggerConfig;
    private ListAppender listAppender;
    private boolean dedicatedLoggerConfigCreated;

    @BeforeEach
    void setUpAppender() {
        loggerContext = (LoggerContext) LogManager.getContext(false);
        configuration = loggerContext.getConfiguration();

        listAppender = new ListAppender("TestListAppender");
        listAppender.start();
        configuration.addAppender(listAppender);

        loggerConfig = configuration.getLoggerConfig(LogService.class.getName());
        if (!LogService.class.getName().equals(loggerConfig.getName())) {
            loggerConfig = new LoggerConfig(LogService.class.getName(), Level.TRACE, true);
            configuration.addLogger(LogService.class.getName(), loggerConfig);
            dedicatedLoggerConfigCreated = true;
        }

        loggerConfig.addAppender(listAppender, Level.TRACE, null);
        loggerContext.updateLoggers();
    }

    @AfterEach
    void tearDownAppender() {
        loggerConfig.removeAppender(listAppender.getName());

        if (dedicatedLoggerConfigCreated) {
            configuration.removeLogger(LogService.class.getName());
        }

        listAppender.stop();
        loggerContext.updateLoggers();
    }

    @Test
    void capturesServiceLogsAndMdcUsingListAppender() {
        String requestId = "req-123";

        logService.logAllLevels(requestId);

        List<LogEvent> events = listAppender.getEvents();
        assertFalse(events.isEmpty(), "Expected captured events from LogService");
        assertTrue(
                containsMessage(events, "processing started for requestId=req-123"),
                "Expected INFO log from LogService"
        );
        assertTrue(
                containsMessage(events, "SLF4J INFO"),
                "Expected SLF4J bridge message to be routed through Log4j2"
        );
        assertTrue(
                containsLevelAndMessage(events, Level.ERROR, "downstream service returned 503"),
                "Expected ERROR event from the service call"
        );

        LogEvent requestEvent = events.stream()
                .filter(event -> event.getMessage().getFormattedMessage().contains("processing started"))
                .findFirst()
                .orElseThrow();
        assertEquals(requestId, requestEvent.getContextData().getValue("requestId"));
    }

    @Test
    void asyncWithoutPropagationLosesTraceAndSpanContext() {
        String traceId = "trace-no-propagation";
        String spanId = "span-no-propagation";

        logService.logAsyncWithTraceContext(traceId, spanId, false).join();

        List<LogEvent> events = listAppender.getEvents();
        LogEvent traceEvent = events.stream()
                .filter(event -> event.getMessage().getFormattedMessage().contains("Async trace context check"))
                .findFirst()
                .orElseThrow();

        assertNull(traceEvent.getContextData().getValue("trace_id"));
        assertNull(traceEvent.getContextData().getValue("span_id"));
    }

    @Test
    void asyncWithPropagationKeepsTraceAndSpanContext() {
        String traceId = "trace-with-propagation";
        String spanId = "span-with-propagation";

        logService.logAsyncWithTraceContext(traceId, spanId, true).join();

        List<LogEvent> events = listAppender.getEvents();
        LogEvent traceEvent = events.stream()
                .filter(event -> event.getMessage().getFormattedMessage().contains("Async trace context check"))
                .findFirst()
                .orElseThrow();

        assertEquals(traceId, traceEvent.getContextData().getValue("trace_id"));
        assertEquals(spanId, traceEvent.getContextData().getValue("span_id"));
    }

    private static boolean containsMessage(List<LogEvent> events, String expectedPart) {
        return events.stream()
                .map(event -> event.getMessage().getFormattedMessage())
                .anyMatch(message -> message.contains(expectedPart));
    }

    private static boolean containsLevelAndMessage(List<LogEvent> events, Level level, String expectedPart) {
        return events.stream().anyMatch(event -> event.getLevel() == level
                && event.getMessage().getFormattedMessage().contains(expectedPart));
    }
}
