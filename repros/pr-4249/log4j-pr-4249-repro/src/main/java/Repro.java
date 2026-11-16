import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.db.jpa.converter.ThrowableAttributeConverter;
import org.apache.logging.log4j.core.appender.nosql.NoSqlAppender;
import org.apache.logging.log4j.core.appender.nosql.NoSqlConnection;
import org.apache.logging.log4j.core.appender.nosql.NoSqlObject;
import org.apache.logging.log4j.core.appender.nosql.NoSqlProvider;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.util.Throwables;
import org.apache.logging.log4j.message.SimpleMessage;

public class Repro {

    static final class CollidingException extends RuntimeException {
        CollidingException(final String message) {
            super(message);
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof CollidingException;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    static final class MapObject implements NoSqlObject<Map<String, Object>> {
        final Map<String, Object> map = new LinkedHashMap<>();

        public void set(final String field, final Object value) {
            map.put(field, value);
        }

        public void set(final String field, final NoSqlObject<Map<String, Object>> value) {
            map.put(field, value == null ? null : value.unwrap());
        }

        public void set(final String field, final Object[] values) {
            map.put(field, values);
        }

        public void set(final String field, final NoSqlObject<Map<String, Object>>[] values) {
            map.put(field, values);
        }

        public Map<String, Object> unwrap() {
            return map;
        }
    }

    static final class MapConnection implements NoSqlConnection<Map<String, Object>, MapObject> {
        final List<Map<String, Object>> inserted = new ArrayList<>();
        boolean closed;

        public MapObject createObject() {
            return new MapObject();
        }

        public MapObject[] createList(final int length) {
            return new MapObject[length];
        }

        public void insertObject(final NoSqlObject<Map<String, Object>> object) {
            inserted.add(object.unwrap());
        }

        public void close() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    public static void main(final String[] args) {
        final String which = args[0];
        try {
            if ("pattern".equals(which)) {
                patternLayoutWithCollidingCauses();
            } else if ("rootcause".equals(which)) {
                rootCauseWithCollidingCauses();
            } else if ("jpa".equals(which)) {
                jpaConverterWithCycle();
            } else if ("nosql".equals(which)) {
                noSqlAppenderWithCycle();
            }
        } catch (final Throwable failure) {
            System.out.println("FAIL " + which + " threw " + failure.getClass().getName());
        }
    }

    private static Throwable cycle() {
        final Exception a = new Exception("A");
        final Exception b = new Exception("B");
        a.initCause(b);
        b.initCause(a);
        return a;
    }

    private static void patternLayoutWithCollidingCauses() {
        final Throwable root = new CollidingException("root");
        final Throwable cause = new CollidingException("cause");
        root.initCause(cause);
        root.setStackTrace(new StackTraceElement[] {new StackTraceElement("java.lang.String", "valueOf", "String.java", 1)});
        cause.setStackTrace(new StackTraceElement[] {new StackTraceElement("Repro", "create", "Repro.java", 1)});
        final PatternLayout layout = PatternLayout.newBuilder().withPattern("%xEx").build();
        final String text = layout.toSerializable(Log4jLogEvent.newBuilder()
                .setLevel(Level.ERROR)
                .setMessage(new SimpleMessage("m"))
                .setThrown(root)
                .build());
        String causeFrame = "absent";
        for (final String line : text.split("\n")) {
            if (line.contains("Repro.create")) {
                causeFrame = line.trim();
            }
        }
        final boolean resolved = causeFrame.matches(".*\\)\\s+~?\\[[^\\]]+\\]$");
        System.out.println((resolved ? "PASS" : "FAIL") + " pattern %xEx cause frame of colliding exception: " + causeFrame);
    }

    private static void rootCauseWithCollidingCauses() {
        final Throwable r = new CollidingException("r");
        final Throwable c = new CollidingException("r_c");
        final Throwable cc = new CollidingException("r_c_c");
        r.initCause(c);
        c.initCause(cc);
        final String root = Throwables.getRootCause(r).getMessage();
        System.out.println(("r_c_c".equals(root) ? "PASS" : "FAIL") + " rootcause Throwables.getRootCause(r -> r_c -> r_c_c, colliding equals) = " + root);
    }

    private static void jpaConverterWithCycle() {
        final String column = new ThrowableAttributeConverter().convertToDatabaseColumn(cycle());
        final int causedBy = column.split("Caused by ", -1).length - 1;
        System.out.println((causedBy == 1 ? "PASS" : "FAIL") + " jpa ThrowableAttributeConverter on A<->B cycle: length=" + column.length() + " 'Caused by' count=" + causedBy);
    }

    private static void noSqlAppenderWithCycle() {
        final MapConnection connection = new MapConnection();
        final NoSqlProvider<MapConnection> provider = new NoSqlProvider<MapConnection>() {
            public MapConnection getConnection() {
                return connection;
            }
        };
        final NoSqlAppender appender = NoSqlAppender.newBuilder()
                .setName("nosql")
                .setProvider(provider)
                .setConfiguration(new DefaultConfiguration())
                .setIgnoreExceptions(false)
                .build();
        appender.start();
        appender.append(Log4jLogEvent.newBuilder()
                .setLevel(Level.ERROR)
                .setMessage(new SimpleMessage("m"))
                .setThrown(cycle())
                .build());
        appender.stop();
        int depth = 0;
        Object node = connection.inserted.isEmpty() ? null : connection.inserted.get(0).get("thrown");
        while (node instanceof Map) {
            depth++;
            node = ((Map<?, ?>) node).get("cause");
        }
        System.out.println((connection.inserted.size() == 1 && depth == 2 ? "PASS" : "FAIL") + " nosql NoSqlAppender on A<->B cycle: inserted=" + connection.inserted.size() + " thrown-chain depth=" + depth);
    }
}
