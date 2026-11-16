# PR #4371 — `%m{html}` prints an empty message

https://github.com/apache/logging-log4j2/pull/4371

`MessagePatternConverter` maps the `html` option to `HtmlTextRenderer`, whose two `render` methods were empty
IDE-generated stubs. `RenderingPatternConverter` formats the message into a work buffer and hands it to the
renderer, so nothing reached the output and no status message was logged. The fix appends the input and escapes
it with `StringBuilders.escapeXml`.

## Commands

```bash
cd log4j-pr-4371-repro
./run.sh 2.26.1 2.26.1
./run.sh <scratchpad>/wt/jars-2x 2.x-HEAD
```

`log4j2.xml`: two `File` appenders, `[%p] %m{html}%n` and the control `[%p] %m%n`. One event:
`<b>Tom</b> & "Jerry" 'x'`.

## Results

| Version | Case | Expected | Observed | Verdict |
|---|---|---|---|---|
| 2.26.1 | control `%m` | `[INFO] <b>Tom</b> & "Jerry" 'x'` | same | PASS |
| 2.26.1 | `%m{html}` | `[INFO] &lt;b&gt;Tom&lt;/b&gt; &amp; &quot;Jerry&quot; &apos;x&apos;` | `[INFO] ` (6 chars, message missing) | FAIL |
| 2.x HEAD ea28f5a78b | control `%m` | `[INFO] <b>Tom</b> & "Jerry" 'x'` | same | PASS |
| 2.x HEAD ea28f5a78b | `%m{html}` | `[INFO] &lt;b&gt;Tom&lt;/b&gt; &amp; &quot;Jerry&quot; &apos;x&apos;` | same (67 chars) | PASS |

Notes: the apostrophe renders as `&apos;` (XML escaping), not the `&#39;` that `HtmlLayout` uses; the open question
in the review note shows up as expected.
