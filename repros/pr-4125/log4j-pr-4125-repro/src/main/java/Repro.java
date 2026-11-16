import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.mom.kafka.KafkaManager;
import org.apache.logging.log4j.core.appender.mom.kafka.KafkaProducerFactory;

public class Repro {

    public static void main(final String[] args) throws Exception {
        final AtomicInteger sendAttempts = new AtomicInteger();
        final MockProducer<byte[], byte[]> producer =
                new MockProducer<byte[], byte[]>(true, new ByteArraySerializer(), new ByteArraySerializer()) {
                    @Override
                    public synchronized Future<RecordMetadata> send(
                            final ProducerRecord<byte[], byte[]> record, final Callback callback) {
                        if (sendAttempts.getAndIncrement() == 0) {
                            throw new RuntimeException("transient failure on first send");
                        }
                        return super.send(record, callback);
                    }
                };
        final KafkaProducerFactory factory = config -> producer;
        final Field field = KafkaManager.class.getDeclaredField("producerFactory");
        field.setAccessible(true);
        field.set(null, factory);

        final Logger logger = LogManager.getLogger("app");
        logger.info("retried-event");
        final List<String> delivered = producer.history().stream()
                .map(record -> new String(record.value()))
                .collect(Collectors.toList());
        LogManager.shutdown();
        System.out.println("RESULT sendAttempts=" + sendAttempts.get() + " delivered=" + delivered);
    }
}
