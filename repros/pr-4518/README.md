# PR #4518 — Truncate oversized UDP datagrams instead of dropping them

Issue #4517 · regression from #4336 (on `2.x` only) · head `e74ac772fe` · 2026-11-23

## Command

```bash
log4j-pr-4518-repro/run.sh            # release 2.26.1, 2.x base, PR head × JDK 8 and 17
probe/UdpProbe.java                   # JDK-only: macOS loopback receive limits
```

Written by hand, not with `oss run --pack`: three builds have to be compared side by side
(release jar, `2.x` jar with the `origin/2.x` `DatagramOutputStream` swapped in, PR-head jar
from the local checkout), and the pack's `--checkout` would have switched the clone while
the Maven verification was running.

App: `<Syslog protocol="UDP" format="RFC5424">` to a local receiver; logs one 80,000-char
event, then `small-after`.

## Results (`output/run.txt`)

| Build | JDK 8 | JDK 17 |
|---|---|---|
| release 2.26.1 | big event lost, `Message too long (sendto failed)`; small arrives | same, `SocketException: Message too long` |
| `2.x` base (#4336) | big event dropped, `WARN … 80082 bytes exceeds … will be dropped`; small arrives | same |
| PR head | **65,507 bytes received**, `WARN UDP datagram truncated from 80082 to 65507 bytes`; small arrives | same |

## macOS probe (`output/udp-probe-macos.txt`)

JDK 8 on macOS gives a new `DatagramSocket` `SO_RCVBUF = 65507`; a 65,507-byte datagram
then never arrives (receive timed out). With `setReceiveBufferSize(262144)` it arrives.
JDK 17/21 default to 786,896 and pass. This is why the PR's `DatagramOutputStreamTest`
fails on `macos-latest` (Java 8 tests), not a product bug: the sender is fine.

## Mechanism

`copy()` keeps `min(length, 65507 - size)` bytes and counts the rest; `flush()` always sends
and warns with the original length; the footer is appended last, so it is cut first.
