package com.springtest.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.stereotype.Service;

@Service
public class SimpleOrderService {

    private static final Logger logger = LogManager.getLogger(SimpleOrderService.class);

    public String processOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            logger.warn("Order rejected: missing orderId");
            return "INVALID_ORDER";
        }

        logger.info("Order accepted for orderId={}", orderId);
        return "ORDER_ACCEPTED:" + orderId;
    }

    public String processOrderWithRequestId(String orderId, String requestId) {
        ThreadContext.put("requestId", requestId);
        try {
            if (orderId == null || orderId.isBlank()) {
                logger.warn("Order rejected: missing orderId requestId={}", requestId);
                return "INVALID_ORDER";
            }

            logger.info("Order accepted for orderId={} requestId={}", orderId, requestId);
            return "ORDER_ACCEPTED:" + orderId;
        } finally {
            ThreadContext.clearAll();
        }
    }
}

