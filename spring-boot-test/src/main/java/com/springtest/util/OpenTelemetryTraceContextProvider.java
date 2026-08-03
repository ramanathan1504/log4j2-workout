//package com.springtest.util;
//
//
//import io.opentelemetry.api.trace.Span;
//import io.opentelemetry.api.trace.SpanContext;
//import org.apache.logging.log4j.spi.TraceContextProvider;
//
///**
// * Real-world OpenTelemetry TraceContextProvider adapter.
// * Extracts standard W3C tracing metadata directly from the active OTel context.
// */
//public class OpenTelemetryTraceContextProvider implements TraceContextProvider {
//
//    @Override
//    public String getTraceId() {
//        final SpanContext spanContext = Span.current().getSpanContext();
//        return spanContext.isValid() ? spanContext.getTraceId() : null;
//    }
//
//    @Override
//    public String getSpanId() {
//        final SpanContext spanContext = Span.current().getSpanContext();
//        return spanContext.isValid() ? spanContext.getSpanId() : null;
//    }
//
//    @Override
//    public String getTraceFlags() {
//        final SpanContext spanContext = Span.current().getSpanContext();
//        return spanContext.isValid() ? spanContext.getTraceFlags().asHex() : null;
//    }
//}
