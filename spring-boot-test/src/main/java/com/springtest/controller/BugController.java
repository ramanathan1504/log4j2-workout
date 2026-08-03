package com.springtest.controller;


import com.springtest.util.CollidingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BugController {
    private static final Logger log = LoggerFactory.getLogger(BugController.class);

    @GetMapping("/trigger-bug")
    public String trigger() {
        try {
            level1();
        } catch (Exception e) {
            // This is where Log4j will try to render the trace and CRASH
            log.error("This will trigger ArrayIndexOutOfBoundsException", e);
        }
        return "Check your console for AIOOBE!";
    }
    @GetMapping("/trigger-aioobe")
    public String triggerAioobe() {
        try {
            level1();
        } catch (Exception e) {
            // Use ThrowableProxy directly to force it to calculate common frames
            org.apache.logging.log4j.core.impl.ThrowableProxy proxy =
                    new org.apache.logging.log4j.core.impl.ThrowableProxy(e);

            // This method forces the calculation of "Common Frames" (the "55 more" part)
            // This is where the AIOOBE lives!
            return proxy.getExtendedStackTraceAsString();
        }
        return "No AIOOBE";
    }
    private void level1() { level2(); }
    private void level2() { level3(); }

    private void level3() {
        // Create an exception that we will throw
        CollidingException inner = new CollidingException("collision", null);

        // Create the outer exception
        // We add a 'cause' that makes the trace longer
        CollidingException outer = new CollidingException("collision", inner);

        // THE TRICK: We create a custom "cause" structure
        // that has MANY frames.
        throw outer;
    }
}