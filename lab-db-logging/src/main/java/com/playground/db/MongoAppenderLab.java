package com.playground.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.util.UUID;

public class MongoAppenderLab {
    private static final Logger logger = LogManager.getLogger(MongoAppenderLab.class);

    public static void main(String[] args) {
        System.out.println("--- Starting Database Logging Lab ---");

        // We add some "Context" data. Log4j MongoDB appender will
        // automatically save these as separate fields in the DB!
        ThreadContext.put("userId", "user-" + UUID.randomUUID().toString().substring(0,8));
        ThreadContext.put("labModule", "lab-db-logging");

        logger.info("First log: Successfully connected to the Log4j Playground.");
        logger.warn("Warning log: Testing how Mongo handles levels.");

        try {
            throw new RuntimeException("Simulated Database Error");
        } catch (Exception e) {
            // This will save the full stack trace into a field in MongoDB
            logger.error("Error log: Something went wrong in the lab!", e);
        }

        ThreadContext.clearAll();
        System.out.println("--- Lab Finished: Check your MongoDB container ---");
    }
}