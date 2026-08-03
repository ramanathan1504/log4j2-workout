package com.springtest.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.util.UUID;

public class MongoAppenderLab {
    private static final Logger logger = LogManager.getLogger(MongoAppenderLab.class);

    // Add this method
    public static void runTest() {
        ThreadContext.put("userId", "spring-user-" + UUID.randomUUID().toString().substring(0,8));
        ThreadContext.put("labModule", "spring-boot-test");

        logger.info("Mongo Lab: Successfully connected via Spring Boot.");
        try {
            throw new RuntimeException("Spring-Mongo Error Test");
        } catch (Exception e) {
            logger.error("Mongo Lab: Something went wrong!", e);
        }
        ThreadContext.clearAll();
    }

    public static void main(String[] args) { runTest(); }
}