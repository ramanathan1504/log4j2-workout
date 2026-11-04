package org.apache.logging.probe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.util.FilteredObjectInputStream;

/**
 * Measures whether a class outside Log4j's allow-list is instantiated when a serialized log event is read, for the
 * three streams that matter to sonatype-2026-006746.
 */
public final class Main {

    private interface StreamFactory {
        ObjectInputStream open(byte[] bytes) throws IOException;
    }

    public static void main(final String[] args) throws Exception {
        final LogEvent event = Log4jLogEvent.newBuilder()
                .setLoggerName("probe")
                .setLevel(Level.INFO)
                .setMessage(new Canary())
                .build();
        final byte[] eventBytes = serialize(event);
        final byte[] canaryBytes = serialize(new Canary());

        report("filtered-direct", canaryBytes, bytes -> new FilteredObjectInputStream(new ByteArrayInputStream(bytes)));
        report("filtered-nested", eventBytes, bytes -> new FilteredObjectInputStream(new ByteArrayInputStream(bytes)));
        report("plain-ois", eventBytes, bytes -> new ObjectInputStream(new ByteArrayInputStream(bytes)));
    }

    private static void report(final String name, final byte[] bytes, final StreamFactory factory) {
        Canary.instantiated = false;
        String detail;
        try (ObjectInputStream in = factory.open(bytes)) {
            final Object read = in.readObject();
            detail = read instanceof LogEvent
                    ? "message=" + ((LogEvent) read).getMessage().getClass().getSimpleName()
                    : "read=" + read.getClass().getSimpleName();
        } catch (final Exception e) {
            detail = "rejected: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.printf("%-16s %-13s %s%n", name, Canary.instantiated ? "INSTANTIATED" : "BLOCKED", detail);
    }

    private static byte[] serialize(final Object object) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }
}
