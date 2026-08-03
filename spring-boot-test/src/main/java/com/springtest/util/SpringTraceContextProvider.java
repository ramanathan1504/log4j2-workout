//package com.springtest.util;
//
//import org.apache.logging.log4j.spi.TraceContextProvider;
//
//public class SpringTraceContextProvider implements TraceContextProvider {
//
//    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();
//    private static final ThreadLocal<String> SPAN_ID_HOLDER = new ThreadLocal<>();
//    private static final ThreadLocal<String> FLAGS_HOLDER = new ThreadLocal<>();
//
//    public static void setContext(String traceId, String spanId, String flags) {
//        TRACE_ID_HOLDER.set(traceId);
//        SPAN_ID_HOLDER.set(spanId);
//        FLAGS_HOLDER.set(flags);
//    }
//
//    public static void clear() {
//        TRACE_ID_HOLDER.remove();
//        SPAN_ID_HOLDER.remove();
//        FLAGS_HOLDER.remove();
//    }
//
//    @Override
//    public String getTraceId() {
//        return TRACE_ID_HOLDER.get();
//    }
//
//    @Override
//    public String getSpanId() {
//        return SPAN_ID_HOLDER.get();
//    }
//
//    @Override
//    public String getTraceFlags() {
//        return FLAGS_HOLDER.get();
//    }
//}
