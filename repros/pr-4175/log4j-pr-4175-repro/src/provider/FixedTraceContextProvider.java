import org.apache.logging.log4j.spi.TraceContextProvider;

public final class FixedTraceContextProvider implements TraceContextProvider {

    public static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    public static final String SPAN_ID = "00f067aa0ba902b7";
    public static final String TRACE_FLAGS = "01";

    @Override
    public String getTraceId() {
        return TRACE_ID;
    }

    @Override
    public String getSpanId() {
        return SPAN_ID;
    }

    @Override
    public String getTraceFlags() {
        return TRACE_FLAGS;
    }
}
