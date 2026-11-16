import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(String[] args) throws Exception {
        int events = Integer.parseInt(args[0]);
        try (Connection admin = DriverManager.getConnection(FailingAfterCommitConnectionFactory.URL, "sa", "");
                Statement statement = admin.createStatement()) {
            statement.execute("CREATE TABLE LOGS (MSG VARCHAR(64))");
            Logger logger = LogManager.getLogger(Repro.class);
            for (int i = 1; i <= events; i++) {
                logger.info("event-" + i);
            }
            String stopOutcome = "clean";
            try {
                ((LoggerContext) LogManager.getContext(false)).stop();
            } catch (RuntimeException e) {
                stopOutcome = e.getClass().getSimpleName();
            }
            Map<String, Integer> rows = new LinkedHashMap<>();
            int total = 0;
            try (ResultSet resultSet =
                    statement.executeQuery("SELECT MSG, COUNT(*) FROM LOGS GROUP BY MSG ORDER BY MIN(_ROWID_)")) {
                while (resultSet.next()) {
                    rows.put(resultSet.getString(1), resultSet.getInt(2));
                    total += resultSet.getInt(2);
                }
            }
            long duplicated = rows.values().stream().filter(count -> count > 1).count();
            System.out.println("logged=" + events + " stored=" + total + " distinct=" + rows.size() + " duplicated="
                    + duplicated + " rowsSentPerFlush=" + FailingAfterCommitConnectionFactory.ROWS_SENT_PER_FLUSH
                    + " stop=" + stopOutcome);
        }
    }
}
