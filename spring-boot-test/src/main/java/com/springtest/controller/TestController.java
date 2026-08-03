//package com.springtest.controller;
//
//
//import com.springtest.util.SpringTraceContextProvider;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//public class TestController {
//
//    private static final Logger logger = LogManager.getLogger(TestController.class);
//
//    @GetMapping("/test-tracing")
//    public String testTracing() {
//        // 1. Simulate tracer context extraction
//        SpringTraceContextProvider.setContext(
//                "4bf92f3577b34da6a3ce929d0e0e4736",
//                "00f067aa0ba902b7",
//                "01"
//        );
//
//        try {
//            // 3. Log message
//            logger.info("This is a native tracing log statement!");
//            return "Tracing log generated in console!";
//        } finally {
//            // 4. Clear context
//            SpringTraceContextProvider.clear();
//        }
//    }
//}
