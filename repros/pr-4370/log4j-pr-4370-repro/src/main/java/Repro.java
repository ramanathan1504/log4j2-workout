import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;

public class Repro {
    public static void main(String[] args) {
        String hostile = "a\"b\\c]d";
        check("control StringMapMessage plain value",
                new StringMapMessage().with("user", "alice").getFormattedMessage(),
                "user=\"alice\"");
        check("StringMapMessage value with \" \\ ]",
                new StringMapMessage().with("user", hostile).getFormattedMessage(),
                "user=\"a\\\"b\\\\c\\]d\"");
        StructuredDataMessage structured = new StructuredDataMessage("id@32473", "message", "type");
        structured.put("user", hostile);
        check("StructuredDataMessage value with \" \\ ]",
                structured.getFormattedMessage(),
                "type [id@32473 user=\"a\\\"b\\\\c\\]d\"] message");
        check("PatternLayout %m of StringMapMessage",
                render(new StringMapMessage().with("user", "x\" admin=\"true")),
                "user=\"x\\\" admin=\\\"true\"");
    }

    private static String render(Message message) {
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%m").build();
        return layout.toSerializable(Log4jLogEvent.newBuilder()
                .setLoggerName("repro")
                .setLevel(Level.INFO)
                .setMessage(message)
                .build());
    }

    private static void check(String name, String observed, String expected) {
        System.out.println((expected.equals(observed) ? "PASS " : "FAIL ") + name + ": observed=" + observed
                + " expected=" + expected);
    }
}
