import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class FailingAfterCommitConnectionFactory {
    public static final String URL = "jdbc:h2:mem:repro;DB_CLOSE_DELAY=-1";
    public static final List<Integer> ROWS_SENT_PER_FLUSH = new CopyOnWriteArrayList<>();
    private static final String MODE = System.getProperty("repro.failure", "after-commit-once");
    private static final AtomicInteger COMMITS = new AtomicInteger();

    private FailingAfterCommitConnectionFactory() {}

    public static Connection getConnection() throws SQLException {
        Connection real = DriverManager.getConnection(URL, "sa", "");
        AtomicInteger rowsSent = new AtomicInteger();
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if ("commit".equals(name)) {
                        ROWS_SENT_PER_FLUSH.add(rowsSent.get());
                        int commitNumber = COMMITS.incrementAndGet();
                        if (failsBeforeCommit(commitNumber)) {
                            real.rollback();
                            throw new SQLException("commit failed, transaction rolled back");
                        }
                        real.commit();
                        if ("after-commit-once".equals(MODE) && commitNumber == 1) {
                            throw new SQLException("connection reset after the commit reached the database");
                        }
                        return null;
                    }
                    Object result = invoke(real, method, args);
                    if ("prepareStatement".equals(name)) {
                        return countingStatement((PreparedStatement) result, rowsSent);
                    }
                    return result;
                });
    }

    private static boolean failsBeforeCommit(int commitNumber) {
        return "rollback-always".equals(MODE) || ("rollback-once".equals(MODE) && commitNumber == 1);
    }

    private static PreparedStatement countingStatement(PreparedStatement real, AtomicInteger rowsSent) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("addBatch".equals(method.getName()) || "executeUpdate".equals(method.getName())) {
                rowsSent.incrementAndGet();
            }
            return invoke(real, method, args);
        };
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[] {PreparedStatement.class}, handler);
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
