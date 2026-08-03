package com.playground.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JdbcLab {
    private static final Logger logger = LogManager.getLogger(JdbcLab.class);

    public static void main(String[] args) {
        System.out.println("Starting SQL Logging Lab...");

        logger.info("SQL Lab Check: Pipeline is open.");
        logger.warn("SQL Lab Check: Testing persistence at " + java.time.LocalDateTime.now());

        try {
            throw new Exception("Database Test Exception");
        } catch (Exception e) {
            logger.error("SQL Lab Check: Capturing stack trace", e);
        }

        System.out.println("✅ Check MySQL table now.");
    }
}