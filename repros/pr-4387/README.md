# PR #4387 — port of #4316 to `2.25.x`: `<log:dump>` wrote scope attribute names and values as raw HTML

https://github.com/apache/logging-log4j2/pull/4387

## Mechanism

Cherry-pick of #4316 (`5a29054cbd`) onto `2.25.x`. `DumpTag.doEndTag` concatenated each attribute name and `value.toString()` into `<dt><code>…</code></dt><dd><code>…</code></dd>`, so an attribute holding markup became live HTML; the port escapes both through `StringBuilders.escapeXml`. `DumpTag.java` at `18d2ed7e28` (2.25.x) is byte-identical to `origin/2.x`, and no `2.25.x` build is available, so the fixed column is the 2.x build, which carries the same change. The repro is the one from `repros/pr-4316`: `Repro.java` drives `DumpTag` with Spring's `MockPageContext` and captures the written body.

## Commands

```bash
cd log4j-pr-4387-repro
./run.sh 2.25.5 2.25.5
./run.sh 2.26.1 2.26.1
./run.sh /private/tmp/claude-501/-Users-ramanathan-apache-log4j2-workout/7180a8bd-7341-4eed-92c9-cb3b625c5257/scratchpad/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Outputs: `output/<label>/result.txt`. The "could not find a logging provider" line is expected: only `log4j-api` and `log4j-taglib` are on the classpath.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.25.5 | name `<name>`, value `<script>alert('xss')</script>` | escaped | `<dt><code><name></code></dt><dd><code><script>alert('xss')</script></code></dd>` | FAIL |
| 2.25.5 | control: `user`=`alice` | unchanged | `<dt><code>user</code></dt><dd><code>alice</code></dd>` | PASS |
| 2.25.5 | value whose `toString()` returns null | `null` | `<dd><code>null</code></dd>` | PASS |
| 2.26.1 | markup name and value | escaped | raw, as 2.25.5 | FAIL |
| 2.26.1 | control | unchanged | unchanged | PASS |
| 2.26.1 | null `toString()` | `null` | `null` | PASS |
| 2.x HEAD ea28f5a78b | markup name and value | escaped | `<dt><code>&lt;name&gt;</code></dt><dd><code>&lt;script&gt;alert(&apos;xss&apos;)&lt;/script&gt;</code></dd>` | PASS |
| 2.x HEAD ea28f5a78b | control | unchanged | unchanged | PASS |
| 2.x HEAD ea28f5a78b | null `toString()` | `null` | `null` | PASS |

## Notes

The `2.25.x` branch itself was not built or run; the fixed column stands for it only because `DumpTag.java` is identical on both branches.
