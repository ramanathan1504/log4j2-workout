package com.springtest.service;

import com.springtest.SpringBootTestApp;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SpringBootTestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SimpleOrderServiceListAppenderTest {

    @Autowired
    private SimpleOrderService simpleOrderService;

    private LoggerContext loggerContext;
    private Configuration configuration;
    private LoggerConfig loggerConfig;
    private ListAppender listAppender;
    private boolean dedicatedLoggerConfigCreated;

    @BeforeEach
    void setUpAppender() {
        loggerContext = (LoggerContext) LogManager.getContext(false);
        configuration = loggerContext.getConfiguration();

        listAppender = new ListAppender("SimpleOrderTestAppender");
        listAppender.start();
        configuration.addAppender(listAppender);

        loggerConfig = configuration.getLoggerConfig(SimpleOrderService.class.getName());
        if (!SimpleOrderService.class.getName().equals(loggerConfig.getName())) {
            loggerConfig = new LoggerConfig(SimpleOrderService.class.getName(), Level.INFO, true);
            configuration.addLogger(SimpleOrderService.class.getName(), loggerConfig);
            dedicatedLoggerConfigCreated = true;
        }

        loggerConfig.addAppender(listAppender, Level.INFO, null);
        loggerContext.updateLoggers();
    }

    @AfterEach
    void tearDownAppender() {
        loggerConfig.removeAppender(listAppender.getName());
        if (dedicatedLoggerConfigCreated) {
            configuration.removeLogger(SimpleOrderService.class.getName());
        }
        listAppender.stop();
        loggerContext.updateLoggers();
    }

    @Test
    void logsInfoAndReturnsAcceptedForValidOrder() {
        String orderId = "ORD-101";
        String result = simpleOrderService.processOrder(orderId);

        assertEquals("ORDER_ACCEPTED:" + orderId, result);
        assertTrue(hasLog(Level.INFO, "Order accepted for orderId=" + orderId));
    }

    @Test
    void logsInfoAndReturnsAcceptedForDynamicOrderId() {
        String orderId = "ORD-" + System.currentTimeMillis();
        String result = simpleOrderService.processOrder(orderId);

        assertEquals("ORDER_ACCEPTED:" + orderId, result);
        assertTrue(hasLogContaining(Level.INFO, "Order accepted for orderId="));
        assertTrue(hasLogContaining(Level.INFO, orderId));
    }

    @Test
    void logsWarnAndReturnsInvalidForBlankOrder() {
        String result = simpleOrderService.processOrder("  ");

        assertEquals("INVALID_ORDER", result);
        assertTrue(hasLog(Level.WARN, "Order rejected: missing orderId"));
    }

    @Test
    void capturesDynamicRequestIdInMdc() {
        String orderId = "ORD-202";
        String requestId = "req-" + System.currentTimeMillis();

        String result = simpleOrderService.processOrderWithRequestId(orderId, requestId);

        assertEquals("ORDER_ACCEPTED:" + orderId, result);
        Optional<LogEvent> event = findLogContaining(Level.INFO, requestId);
        assertTrue(event.isPresent(), "Expected log event with dynamic requestId");
        assertNotNull(event.get().getContextData().getValue("requestId"));
        assertEquals(requestId, event.get().getContextData().getValue("requestId"));
    }

    @Test
    void clearsRequestIdFromMdcAfterRequestScopedLogging() {
        String orderId = "ORD-303";
        String requestId = "req-" + System.currentTimeMillis();

        String result = simpleOrderService.processOrderWithRequestId(orderId, requestId);

        assertEquals("ORDER_ACCEPTED:" + orderId, result);
        assertNull(ThreadContext.get("requestId"), "Expected requestId to be cleared after service call");
    }

    private boolean hasLog(Level level, String exactMessage) {
        List<LogEvent> events = listAppender.getEvents();
        return events.stream().anyMatch(event -> event.getLevel() == level
                && event.getMessage().getFormattedMessage().equals(exactMessage));
    }

    private boolean hasLogContaining(Level level, String messagePart) {
        List<LogEvent> events = listAppender.getEvents();
        return events.stream().anyMatch(event -> event.getLevel() == level
                && event.getMessage().getFormattedMessage().contains(messagePart));
    }

    private Optional<LogEvent> findLogContaining(Level level, String messagePart) {
        List<LogEvent> events = listAppender.getEvents();
        return events.stream().filter(event -> event.getLevel() == level
                && event.getMessage().getFormattedMessage().contains(messagePart)).findFirst();
    }
}
