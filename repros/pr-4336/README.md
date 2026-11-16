# PR #4336 — Grow the DatagramOutputStream buffer and drop oversized UDP payloads

https://github.com/apache/logging-log4j2/pull/4336

`DatagramOutputStream.copy()` allocated a new exact-size array on every write (O(n²) for an event written in 8 KiB chunks), and `flush()` passed whatever had accumulated to `DatagramSocket.send`, which throws `SocketException: Message too long` above the 65,507-byte IPv4 UDP limit. The fix backs the stream with a `ByteArrayOutputStream`, stops accumulating past 65,507 bytes, and logs `WARN UDP datagram of N bytes exceeds the maximum of 65507 bytes and will be dropped` without calling `send`. The repro configures a `Socket` appender (`protocol="UDP"`, `%m%n`) towards an in-JVM `DatagramSocket` receiver, logs a small event, a large one and another small one, and reports the received datagram sizes plus the StatusLogger output at WARN.

## Commands

```bash
cd log4j-pr-4336-repro
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD
```

`/private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x` holds the `2.27.0-SNAPSHOT` jars built from 2.x HEAD `ea28f5a78b`, which contains the squash commit `51f47051d6`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | control: 20,000-byte message | delivered whole | datagram sizes `[13, 20001, 12]` | PASS |
| 2.26.1 | 70,000-byte message | dropped with a warning, no appender error | 2 status ERROR lines, `java.net.SocketException: Message too long`, no warning | FAIL |
| 2.26.1 | control: next event after the oversized one | delivered | sizes `[13, 12]`, `small-after` received | PASS |
| 2.x HEAD ea28f5a78b | control: 20,000-byte message | delivered whole | sizes `[13, 20001, 12]` | PASS |
| 2.x HEAD ea28f5a78b | 70,000-byte message | dropped with a warning, no appender error | `UDP datagram of 70001 bytes exceeds the maximum of 65507 bytes and will be dropped`, 0 ERROR lines | PASS |
| 2.x HEAD ea28f5a78b | control: next event after the oversized one | delivered | sizes `[13, 12]`, `small-after` received | PASS |

Notes: the allocation cost (the performance half of the PR) is not measured here. Run on macOS with `net.inet.udp.maxdgram: 9216`; the 20,000-byte datagram still arrives over loopback, as the review note observed for 16 KiB.
