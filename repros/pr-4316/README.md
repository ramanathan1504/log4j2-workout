# PR #4316 — `<log:dump>` wrote scope attribute names and values as raw HTML

https://github.com/apache/logging-log4j2/pull/4316

## Mechanism

`DumpTag.doEndTag` concatenated each attribute name and `value.toString()` into `<dt><code>…</code></dt><dd><code>…</code></dd>` on `pageContext.getOut()`, so a request or session attribute holding markup became live HTML. The PR passes both through `StringBuilders.escapeXml` (`& < > " '`). `Repro.java` drives `DumpTag` with Spring's `MockPageContext` (spring-test 5.3.39, servlet-api 4.0.1, jsp-api 2.3.3) and captures the written body.

## Commands

```bash
cd log4j-pr-4316-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD-ea28f5a78b
```

Outputs: `output/<label>/result.txt`. The "could not find a logging provider" line is expected: only `log4j-api` and `log4j-taglib` are on the classpath.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | name `<name>`, value `<script>alert('xss')</script>` | escaped | `<dt><code><name></code></dt><dd><code><script>alert('xss')</script></code></dd>` | FAIL |
| 2.26.1 | control: `user`=`alice` | unchanged | `<dt><code>user</code></dt><dd><code>alice</code></dd>` | PASS |
| 2.26.1 | value whose `toString()` returns null | `null` | `<dd><code>null</code></dd>` | PASS |
| 2.x HEAD ea28f5a78b | markup name and value | escaped | `<dt><code>&lt;name&gt;</code></dt><dd><code>&lt;script&gt;alert(&apos;xss&apos;)&lt;/script&gt;</code></dd>` | PASS |
| 2.x HEAD ea28f5a78b | control | unchanged | `<dt><code>user</code></dt><dd><code>alice</code></dd>` | PASS |
| 2.x HEAD ea28f5a78b | null `toString()` | `null` | `<dd><code>null</code></dd>` | PASS |
