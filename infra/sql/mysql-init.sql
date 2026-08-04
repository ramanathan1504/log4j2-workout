-- Schema for the JDBC appender against Postgres. The appender never creates
-- tables, so this runs at container init instead.
CREATE TABLE IF NOT EXISTS LOG_EVENTS (
    ID          BIGINT AUTO_INCREMENT PRIMARY KEY,
    EVENT_DATE  TIMESTAMP,
    LEVEL       VARCHAR(10),
    LOGGER      VARCHAR(255),
    MESSAGE     TEXT,
    THREAD      VARCHAR(100),
    MARKER      VARCHAR(100),
    TRACE_ID    VARCHAR(64),
    THROWABLE   TEXT
);
