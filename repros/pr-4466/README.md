# PR #4466: backport the deserialization hardening series to `2.25.x`

Written by hand: `oss run … pr 4466 --checkout` checks out in the main log4j2 checkout, which held the PR branch.
The release run resolves jars from Maven Central. The head run uses the jars built in the main clone at the PR head
(`run.sh head <clone> <label>`), never `~/.m2` snapshots. JDK 17.

```bash
log4j-pr-4466-repro/run.sh 2.25.5
OTHER_EVENT=$PWD/output/2.25.5/event.ser log4j-pr-4466-repro/run.sh head ~/apache/logging-log4j2 pr-head-2b83686af6
OTHER_EVENT=$PWD/output/pr-head-2b83686af6/event.ser log4j-pr-4466-repro/run.sh 2.25.5 2.25.5-reads-head
```

| Scenario | 2.25.5 | PR head `2b83686af6` |
|---|---|---|
| S1 event with a non-serializable `ParameterizedMessage` argument, plain `ObjectInputStream` | `user not-serializable id 42`, arguments `[not-serializable, 42]` | same |
| S2 ordinary event through `FilteredObjectInputStream` | `user alice id 42` | same |
| S3 `ObjectMessage(java.net.URI)` through `FilteredObjectInputStream` | message silently becomes `null` | `InvalidObjectException: Class is not allowed for deserialization: java.net.URI` |
| S4 read an event written by the other version | `InvalidClassException` on `SortedArrayStringMap` `serialVersionUID` | same, the other way round |

Mechanism: the event's message is written as a wrapped byte array (`WrappedObjects`) instead of a `MarshalledObject`.
Nested streams keep the allow-list and the outer filter, and a rejected class now propagates instead of being turned
into `null`. The `SortedArrayStringMap` serialized form and `serialVersionUID` changed, so the two versions cannot read
each other's events, as the changelog says.

Outputs: `output/2.25.5/`, `output/pr-head-2b83686af6/`, `output/2.25.5-reads-head/`.
