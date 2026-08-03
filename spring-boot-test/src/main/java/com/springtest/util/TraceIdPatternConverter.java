//package com.springtest.util;
//
//import org.apache.logging.log4j.core.LogEvent;
//import org.apache.logging.log4j.core.config.plugins.Plugin;
//import org.apache.logging.log4j.core.pattern.ConverterKeys;
//import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
//
//@Plugin(name = "TraceIdPatternConverter", category = "Converter")
//@ConverterKeys({"traceId"})
//public final class TraceIdPatternConverter extends LogEventPatternConverter {
//
//    private TraceIdPatternConverter() {
//        super("TraceId", "traceId");
//    }
//
//    public static TraceIdPatternConverter newInstance(final String[] options) {
//        return new TraceIdPatternConverter();
//    }
//
//    @Override
//    public void format(final LogEvent event, final StringBuilder toAppendTo) {
//        final String traceId = event.getTraceId();
//        if (traceId != null) {
//            toAppendTo.append(traceId);
//        } else {
//            toAppendTo.append("N/A"); // Default fallback when no trace is active
//        }
//    }
//}
