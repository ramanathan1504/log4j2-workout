-- The Cassandra appender cannot create this itself.
--
-- The DataStax driver pulls in Netty, whose InternalLoggerFactory calls
-- LogManager.getLogger while the driver is still initialising. That first
-- logger acquisition triggers Log4j configuration, which starts the Cassandra
-- appender, which calls cluster.connect("log4j") — for a keyspace that any
-- in-JVM bootstrap code has not reached yet, because it is the very code
-- initialising the driver. startupInternal runs once, so the appender is then
-- dead for the whole run.
--
-- Creating the schema out-of-band, before the JVM starts, is the only fix
-- that works from outside. See docs/evidence/nosql-cassandra-startup.log.

CREATE KEYSPACE IF NOT EXISTS log4j
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS log4j.log_events (
    id          timeuuid PRIMARY KEY,
    timeid      timeuuid,
    message     text,
    level       text,
    logger      text,
    marker      text,
    thread      text,
    event_date  timestamp,
    mdc         map<text, text>
);
