package com.playground;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

public class JsonLab {
    private static final Logger logger = LogManager.getLogger(JsonLab.class);

    public static void main(String[] args) {
        // Business Context: Adding metadata that JSON will capture
        ThreadContext.put("service", "payment-service");
        ThreadContext.put("region", "us-east-1");

        logger.info("JSON Lab: System check successful.");

        try {
            String val = null;
            val.length();
        } catch (Exception e) {
            // Watch how beautifully the JSON captures this error object!
            logger.error("JSON Lab: Caught a NullPointerException", e);
        }

        ThreadContext.clearAll();
    }
}