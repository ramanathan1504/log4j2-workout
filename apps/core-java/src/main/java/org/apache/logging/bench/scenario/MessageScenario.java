package org.apache.logging.bench.scenario;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.bench.Scenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFormatMessage;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.message.StringFormattedMessage;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.apache.logging.log4j.message.ThreadDumpMessage;

/**
 * Every {@code Message} implementation Log4j ships, plus the lambda/{@code Supplier}
 * forms. Feature matrix §14.
 */
public final class MessageScenario implements Scenario {

    private static final Logger log = LogManager.getLogger(MessageScenario.class);

    @Override
    public String name() {
        return "messages";
    }

    @Override
    public String describes() {
        return "All Message types: Simple, Parameterized, Formatted, MessageFormat, Object, Map, StructuredData, ThreadDump, Flow, Supplier";
    }

    @Override
    public void run() {
        // SimpleMessage implements both Message and CharSequence, so an uncast
        // argument is ambiguous against Logger.info(Message)/info(CharSequence).
        log.info((Message) new SimpleMessage("SimpleMessage — no formatting at all"));

        // The everyday form: {} placeholders, arguments formatted lazily.
        log.info("ParameterizedMessage — user {} placed order {} for {}", "alice", 4711, "£20.00");
        log.info(new ParameterizedMessage("ParameterizedMessage — explicit, {} and {}", "one", "two"));

        // printf-style
        log.info(new StringFormattedMessage("StringFormattedMessage — %s scored %.2f%%", "bob", 91.5));

        // java.text.MessageFormat style
        log.info(new MessageFormatMessage("MessageFormatMessage — {0} retried {1,number,integer} times", "job-7", 3));

        // Picks the formatter based on the pattern's shape
        log.info(new FormattedMessage("FormattedMessage — %s then {}", "printf-wins"));

        // Arbitrary object; layouts may serialise it structurally
        log.info(new ObjectMessage(Map.of("kind", "ObjectMessage", "answer", 42)));

        // Map messages surface as real fields in JSON layouts rather than flat text
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("event", "checkout");
        fields.put("cartValue", "89.90");
        fields.put("currency", "GBP");
        log.info(new StringMapMessage(fields));

        final MapMessage<?, Object> typed = new MapMessage<>()
                .with("event", "payment")
                .with("amountMinor", 8990)
                .with("settled", true);
        log.info(typed);

        // RFC 5424 structured data — pairs with Rfc5424Layout and the Syslog appender
        final StructuredDataMessage sd =
                new StructuredDataMessage("order-4711", "order accepted", "audit");
        sd.put("customer", "alice");
        sd.put("channel", "web");
        log.info(sd);

        // Lambda forms — the argument is only evaluated if the level is enabled
        log.debug("Supplier form — expensive value: {}", () -> expensiveToCompute());
        log.atInfo().withLocation().log("Fluent API — LogBuilder with location capture");

        // Flow tracing produces FlowMessages (ENTRY/EXIT markers)
        traced("flow-demo");

        // A thread dump of every live thread
        log.debug(new ThreadDumpMessage("ThreadDumpMessage — all live threads"));
    }

    private String traced(final String input) {
        log.traceEntry(input);
        final String result = input.toUpperCase(java.util.Locale.ROOT);
        return log.traceExit(result);
    }

    private static String expensiveToCompute() {
        return "computed-lazily";
    }
}
