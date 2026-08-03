package com.springtest.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JdbcLab {
    private static final Logger logger = LogManager.getLogger(JdbcLab.class);

    // Add this method
    public static void runTest() {
        logger.info("SQL Lab Check: Pipeline is open via Spring Boot.");
        logger.warn("SQL Lab Check: Testing persistence at " + java.time.LocalDateTime.now());
        try {
            throw new Exception("Spring-JDBC Test Exception");
        } catch (Exception e) {
            logger.error("SQL Lab Check: Capturing stack trace", e);
        }
    }

    public static void main(String[] args) { runTest(); }
}