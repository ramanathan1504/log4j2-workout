# Log4j issue #4356 reproduction

https://github.com/apache/logging-log4j2/issues/4356

> Async loggers silently discard events when the ring buffer is full and LMAX Disruptor 4 is used

## Reproduction

```bash
cd log4j-issue-4356-repro
./run.sh 2.26.1 4.0.0     # loses ~92% of lines
./run.sh 2.26.1 3.4.4     # loses nothing
```

`Main.java` is the reporter's program unchanged, apart from the package and the
Log4j core jar name added to the result line. JDK 17, macOS.

## Results (3 runs each, 16 threads x 50,000 lines)

| Log4j | Disruptor | Missing |
|---|---|---|
| 2.25.3 | 4.0.0 | 92.4%, 93.5%, 92.7% |
| 2.25.3 | 3.4.4 | 0, 0, 0 |
| 2.26.1 | 4.0.0 | 91.9%, 93.6%, 93.9% |
| 2.26.1 | 3.4.4 | 0, 0, 0 |
| 2.27.0-SNAPSHOT (2.x @ d631e827ff) | 4.0.0 | 86.1%, 90.1%, 91.9% |
| 2.27.0-SNAPSHOT (2.x @ d631e827ff) | 3.4.4 | 0, 0, 0 |
| 2.27.0-SNAPSHOT + fix (branch `fix/4356-disruptor4-discard`) | 4.0.0 | 0, 0, 0 |
| 2.27.0-SNAPSHOT + fix (branch `fix/4356-disruptor4-discard`) | 3.4.4 | 0, 0, 0 |

Nothing reaches stdout or stderr besides the result line (`status="WARN"`).
Raw output in `output/`.

## Mechanism

Disruptor 4.0.0 `MultiProducerSequencer.next()` claims with `cursor.getAndAdd(n)`
before waiting for capacity (3.4.4 uses `compareAndSet` after the wait), so a
producer parked in `next()` drives `RingBuffer.remainingCapacity()` negative.
`AsyncLoggerDisruptor.getEventRoute()` and `AsyncLoggerConfigDisruptor.getEventRoute()`
read any negative capacity as "shut down" and return `EventRoute.DISCARD` before the
`AsyncQueueFullPolicy` is consulted.
