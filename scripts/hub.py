#!/usr/bin/env python3
"""
hub.py — one local site over all three repos.

    bench hub                 serve on http://localhost:8787
    bench hub --port 9000
    bench hub --open          ...and open a browser
    bench hub --once          write index.html and exit, no server

The page is regenerated on every request, from the working tree as it is right
now. There is no build step, no cache and no watcher to fall out of sync: if you
commit in any of the three repos and reload, you are looking at the new state.

Stdlib only, deliberately. This machine has no markdown library and the site has
to keep working on a laptop with no network, so the renderer below is a small
subset renderer rather than a dependency.

It reads. It never writes to a repo, never fetches, never pulls.
"""

import argparse
import html
import json
import os
import re
import subprocess
import sys
import webbrowser
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

HOME = Path.home()
WORKOUT = Path(__file__).resolve().parent.parent
OSSCLI = Path(os.environ.get("BENCH_OSSCLI_DIR") or HOME / "own repo" / "oss-cli")
KB = Path(os.environ.get("BENCH_KB_DIR") or HOME / "own repo" / "knowledge-creator")

REPOS = [
    ("log4j2-workout", WORKOUT, "runs", "real apps, real JVMs, the version matrix"),
    ("oss-cli", OSSCLI, "knows", "facts on any repo from the API — no clone"),
    ("knowledge-creator", KB, "remembers", "harvest, file, index, retrieve"),
]

DOCS = [
    ("log4j2-workout", WORKOUT, ["README.md", "CLAUDE.md", "docs/PR-REVIEW.md",
                                 "docs/BY-HAND.md", "docs/BENCH-NOTES.md", "docs/HANDOVER.md",
                                 "docs/GH-COMMANDS.md", "docs/FEATURE-MATRIX.md",
                                 "docs/ISSUES.md", "docs/GAPS.md"]),
    ("oss-cli", OSSCLI, ["README.md", "COMMANDS.md", "SETUP.md", "DEVELOPING.md",
                         "MACOS_AUTOMATION.md", "CHANGELOG.md"]),
    ("knowledge-creator", KB, ["README.md", "SETUP.md", "DEVELOPING.md", "AI-OPTIONAL.md"]),
]


# ---------------------------------------------------------------- markdown ---
INLINE = [
    (re.compile(r"`([^`]+)`"), lambda m: f"<code>{html.escape(m.group(1))}</code>"),
    (re.compile(r"\*\*([^*]+)\*\*"), lambda m: f"<strong>{m.group(1)}</strong>"),
    (re.compile(r"(?<![\w*])\*([^*\n]+)\*(?![\w*])"), lambda m: f"<em>{m.group(1)}</em>"),
    (re.compile(r"~~([^~]+)~~"), lambda m: f"<del>{m.group(1)}</del>"),
    (re.compile(r"\[([^\]]+)\]\(([^)]+)\)"),
     lambda m: f'<a href="{html.escape(m.group(2), quote=True)}">{m.group(1)}</a>'),
]


def inline(text):
    """Escape first, then apply spans — so a stray < in prose cannot open a tag."""
    out = html.escape(text)
    # code spans first: their content must not be re-processed for emphasis
    parts, last = [], 0
    for m in re.finditer(r"`([^`]+)`", out):
        parts.append(("text", out[last:m.start()]))
        parts.append(("code", m.group(1)))
        last = m.end()
    parts.append(("text", out[last:]))
    rendered = []
    for kind, chunk in parts:
        if kind == "code":
            rendered.append(f"<code>{chunk}</code>")
            continue
        for pat, fn in INLINE[1:]:
            chunk = pat.sub(fn, chunk)
        rendered.append(chunk)
    return "".join(rendered)


def md_to_html(text):
    lines, out, i = text.split("\n"), [], 0
    list_stack = []

    def close_lists(to=0):
        while len(list_stack) > to:
            out.append(f"</{list_stack.pop()}>")

    while i < len(lines):
        line = lines[i]

        if line.startswith("```"):
            lang = line[3:].strip()
            i += 1
            buf = []
            while i < len(lines) and not lines[i].startswith("```"):
                buf.append(lines[i])
                i += 1
            i += 1
            close_lists()
            cls = f' class="lang-{html.escape(lang)}"' if lang else ""
            out.append(f"<pre{cls}><code>{html.escape(chr(10).join(buf))}</code></pre>")
            continue

        if not line.strip():
            close_lists()
            i += 1
            continue

        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            close_lists()
            lvl = len(m.group(1))
            text_ = inline(m.group(2))
            slug = re.sub(r"[^a-z0-9]+", "-", re.sub("<[^>]+>", "", m.group(2)).lower()).strip("-")
            out.append(f'<h{lvl} id="{slug}">{text_}</h{lvl}>')
            i += 1
            continue

        if re.match(r"^\s*([-*_])\s*\1\s*\1[\s\-*_]*$", line):
            close_lists()
            out.append("<hr>")
            i += 1
            continue

        # table: a header row followed by a |---| separator
        if line.lstrip().startswith("|") and i + 1 < len(lines) \
                and re.match(r"^\s*\|[\s:|-]+\|\s*$", lines[i + 1]):
            close_lists()
            def cells(row):
                return [c.strip() for c in row.strip().strip("|").split("|")]
            head = cells(line)
            i += 2
            body = []
            while i < len(lines) and lines[i].lstrip().startswith("|"):
                body.append(cells(lines[i]))
                i += 1
            th = "".join(f"<th>{inline(c)}</th>" for c in head)
            rows = "".join(
                "<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>" for r in body)
            out.append(f'<div class="tw"><table><thead><tr>{th}</tr></thead>'
                       f"<tbody>{rows}</tbody></table></div>")
            continue

        if line.lstrip().startswith(">"):
            close_lists()
            buf = []
            while i < len(lines) and lines[i].lstrip().startswith(">"):
                buf.append(lines[i].lstrip()[1:].lstrip())
                i += 1
            out.append(f"<blockquote>{md_to_html(chr(10).join(buf))}</blockquote>")
            continue

        m = re.match(r"^(\s*)([-*+]|\d+[.)])\s+(.*)$", line)
        if m:
            indent, marker, rest = len(m.group(1)), m.group(2), m.group(3)
            tag = "ol" if marker[0].isdigit() else "ul"
            depth = indent // 2 + 1
            while len(list_stack) > depth:
                out.append(f"</{list_stack.pop()}>")
            if len(list_stack) < depth:
                out.append(f"<{tag}>")
                list_stack.append(tag)
            out.append(f"<li>{inline(rest)}</li>")
            i += 1
            continue

        close_lists()
        # Always consume the current line first. Every guard below can be true of
        # the line we are standing on — a `|` row whose table has no separator is
        # the common one — and without this the loop body appends nothing, never
        # advances i, and the generator hangs instead of failing.
        buf = [lines[i]]
        i += 1
        while i < len(lines) and lines[i].strip() and not lines[i].startswith(("#", "```", ">")) \
                and not re.match(r"^\s*([-*+]|\d+[.)])\s+", lines[i]) \
                and not lines[i].lstrip().startswith("|"):
            buf.append(lines[i])
            i += 1
        out.append("<p>" + inline(" ".join(buf)) + "</p>")

    close_lists()
    return "\n".join(out)


# ------------------------------------------------------------------- state ---
def git(repo, *args):
    try:
        r = subprocess.run(["git", "-C", str(repo), *args],
                           capture_output=True, text=True, timeout=10)
        return r.stdout.strip() if r.returncode == 0 else ""
    except Exception:
        return ""


def repo_state(path):
    if not (path / ".git").exists():
        return {"ok": False, "why": f"no git clone at {path}"}
    dirty = [l for l in git(path, "status", "--porcelain").split("\n") if l.strip()]
    counts = git(path, "rev-list", "--left-right", "--count", "@{u}...HEAD")
    behind = ahead = 0
    if counts and len(counts.split()) == 2:
        behind, ahead = (int(x) for x in counts.split())
    return {
        "ok": True,
        "path": str(path),
        "branch": git(path, "rev-parse", "--abbrev-ref", "HEAD"),
        "head": git(path, "log", "--oneline", "-1"),
        "when": git(path, "log", "-1", "--format=%cr"),
        "dirty": len(dirty),
        "dirty_files": dirty[:12],
        "behind": behind,
        "ahead": ahead,
    }


def installed(cmd):
    r = subprocess.run(["/bin/bash", "-lc", f"command -v {cmd} || true"],
                       capture_output=True, text=True, timeout=10)
    return r.stdout.strip()


def reviews():
    """The ledger is the record of what was reviewed; .bench/reviews is evidence."""
    rows = []
    ledger = WORKOUT / "docs" / "pr-reviews" / "ledger.tsv"
    if ledger.exists():
        for line in ledger.read_text().split("\n"):
            if not line.strip() or line.startswith("#"):
                continue
            f = line.split("\t")
            if len(f) >= 7:
                rows.append({"pr": f[0], "state": f[1], "when": f[2],
                             "author": f[4], "posted": f[5], "note": f[6]})
    rows.sort(key=lambda r: r["pr"], reverse=True)
    evidence = sorted(
        (p.parent.name for p in (WORKOUT / ".bench" / "reviews").glob("*/00-SUMMARY.md")),
        reverse=True)
    files = {p.name.split("-")[0]: p.name
             for p in (WORKOUT / "docs" / "pr-reviews").glob("*.md")
             if p.name[0].isdigit()}
    return rows, evidence, files


# -------------------------------------------------------------------- page ---
CSS = """
:root{--bg:#fbfaf8;--fg:#1c1b19;--mut:#6b675f;--line:#e2ded6;--card:#fff;
--acc:#8a4b2a;--ok:#2f6a3f;--warn:#8a6a1a;--bad:#9b2c2c;--code:#f3f0ea;}
:root:not([data-theme=light]) @media (prefers-color-scheme:dark){}
@media (prefers-color-scheme:dark){:root:not([data-theme=light]){
--bg:#14130f;--fg:#eae7e0;--mut:#9d978c;--line:#2c2a25;--card:#1b1a16;
--acc:#d99a6c;--ok:#7fb98c;--warn:#d9b96c;--bad:#e08a8a;--code:#232019;}}
:root[data-theme=dark]{--bg:#14130f;--fg:#eae7e0;--mut:#9d978c;--line:#2c2a25;
--card:#1b1a16;--acc:#d99a6c;--ok:#7fb98c;--warn:#d9b96c;--bad:#e08a8a;--code:#232019;}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);
font:15px/1.65 ui-sans-serif,-apple-system,"Segoe UI",sans-serif;}
code,pre{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}
.wrap{display:grid;grid-template-columns:250px minmax(0,1fr);gap:0;min-height:100vh}
nav{border-right:1px solid var(--line);padding:20px 14px;position:sticky;top:0;
height:100vh;overflow:auto;background:var(--card)}
nav h1{font-size:14px;letter-spacing:.06em;text-transform:uppercase;color:var(--mut);margin:0 0 4px}
nav .grp{font-size:11px;text-transform:uppercase;letter-spacing:.08em;color:var(--mut);
margin:18px 0 6px}
nav a{display:block;padding:4px 8px;border-radius:6px;color:var(--fg);text-decoration:none;
font-size:13.5px}
nav a:hover{background:var(--code)} nav a.on{background:var(--code);color:var(--acc);font-weight:600}
main{padding:28px 34px;max-width:none;overflow:hidden}
.hd{display:flex;align-items:baseline;gap:14px;flex-wrap:wrap;margin-bottom:6px}
.hd h2{margin:0;font-size:22px}
.sub{color:var(--mut);font-size:13px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:14px;margin:18px 0}
.card{border:1px solid var(--line);border-radius:10px;padding:14px 16px;background:var(--card)}
.card h3{margin:0 0 2px;font-size:15px}
.role{font-size:11px;text-transform:uppercase;letter-spacing:.09em;color:var(--acc)}
.kv{color:var(--mut);font-size:12.5px;margin-top:8px;word-break:break-word}
.pill{display:inline-block;padding:1px 8px;border-radius:99px;font-size:11.5px;
border:1px solid var(--line)}
.ok{color:var(--ok)} .warn{color:var(--warn)} .bad{color:var(--bad)}
.tw{overflow-x:auto;margin:14px 0}
table{border-collapse:collapse;width:100%;font-size:13.5px}
th,td{border-bottom:1px solid var(--line);padding:7px 10px;text-align:left;vertical-align:top}
th{color:var(--mut);font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.05em}
pre{background:var(--code);padding:12px 14px;border-radius:8px;overflow-x:auto;font-size:13px}
:not(pre)>code{background:var(--code);padding:1px 5px;border-radius:4px;font-size:.9em}
blockquote{border-left:3px solid var(--acc);margin:14px 0;padding:2px 14px;color:var(--mut)}
h1,h2,h3,h4{line-height:1.3} main h1{font-size:24px} main h2{font-size:19px;margin-top:28px}
main h3{font-size:16px;margin-top:22px}
hr{border:0;border-top:1px solid var(--line);margin:22px 0}
a{color:var(--acc)}
.doc{border-top:1px solid var(--line);margin-top:26px;padding-top:6px}
.foot{color:var(--mut);font-size:12px;margin-top:40px;border-top:1px solid var(--line);padding-top:12px}
@media(max-width:820px){.wrap{grid-template-columns:1fr}nav{position:static;height:auto}}
"""

JS = """
const secs=[...document.querySelectorAll('section[id]')];
const links=[...document.querySelectorAll('nav a[data-t]')];
function show(id){
  secs.forEach(s=>s.hidden = s.id!==id);
  links.forEach(a=>a.classList.toggle('on',a.dataset.t===id));
  location.hash=id; window.scrollTo(0,0);
}
links.forEach(a=>a.onclick=e=>{e.preventDefault();show(a.dataset.t)});
show(location.hash.slice(1)||'home');
// The server re-reads the repos on every request, so a reload is the refresh.
setInterval(()=>{fetch('status.json').then(r=>r.json()).then(d=>{
  if(window.__stamp && window.__stamp!==d.stamp) location.reload();
  window.__stamp=d.stamp;}).catch(()=>{})},15000);
"""


def status_badge(s):
    if not s["ok"]:
        return f'<span class="pill bad">{html.escape(s["why"])}</span>'
    bits = [f'<span class="pill">{html.escape(s["branch"])}</span>']
    if s["dirty"]:
        bits.append(f'<span class="pill warn">{s["dirty"]} uncommitted</span>')
    else:
        bits.append('<span class="pill ok">clean</span>')
    if s["behind"]:
        bits.append(f'<span class="pill warn">{s["behind"]} behind — git pull</span>')
    if s["ahead"]:
        bits.append(f'<span class="pill">{s["ahead"]} ahead</span>')
    return " ".join(bits)


def build():
    states = {name: repo_state(p) for name, p, _, _ in REPOS}
    cmds = {c: installed(c) for c in ("bench", "oss-cli", "kb")}
    led, evid, files = reviews()

    nav = ['<h1>bench hub</h1><div class="sub">three repos, one page</div>',
           '<div class="grp">start</div>',
           '<a href="#home" data-t="home">Overview &amp; status</a>',
           '<a href="#flow" data-t="flow">Reviewing a PR</a>',
           '<a href="#reviews" data-t="reviews">Reviews</a>']
    secs = []

    # ---- home
    cards = []
    for name, path, role, blurb in REPOS:
        s = states[name]
        cards.append(f"""<div class="card"><div class="role">{role}</div>
        <h3>{html.escape(name)}</h3><div class="sub">{html.escape(blurb)}</div>
        <div style="margin-top:10px">{status_badge(s)}</div>
        <div class="kv">{html.escape(s.get("head", "") or "—")}<br>
        {html.escape(s.get("when", ""))} · <code>{html.escape(s.get("path", str(path)))}</code></div></div>""")

    inst = "".join(
        f"<tr><td><code>{c}</code></td><td>{'<span class=ok>installed</span>' if p else '<span class=bad>not on PATH</span>'}</td>"
        f"<td><code>{html.escape(p or '—')}</code></td></tr>" for c, p in cmds.items())

    secs.append(f"""<section id="home"><div class="hd"><h2>Three repos, one job each</h2></div>
    <p><strong>oss-cli knows → log4j2-workout runs → knowledge-creator remembers.</strong>
    One test decides where new work belongs: <em>does it need to execute code against a
    real app?</em> If yes it is the workout; if it only needs to be retrievable later it is
    knowledge-creator; if neither, oss-cli.</p>
    <div class="cards">{''.join(cards)}</div>
    <h3>Installed commands</h3>
    <div class="tw"><table><thead><tr><th>command</th><th>state</th><th>path</th></tr></thead>
    <tbody>{inst}</tbody></table></div>
    <p class="sub">This page re-reads all three working trees on every request — no build
    step and no cache, so a reload is the refresh. It never fetches, pulls or writes.
    “behind” is measured against the last <code>git fetch</code> you ran.</p>
    </section>""")

    # ---- flow
    secs.append(f"""<section id="flow" hidden><div class="hd"><h2>Reviewing a PR, across the three</h2></div>
    {md_to_html(FLOW)}</section>""")

    # ---- reviews
    rows = ""
    for r in led:
        ev = "yes" if r["pr"] in evid else "—"
        wf = files.get(r["pr"])
        link = f'<code>{html.escape(wf)}</code>' if wf else "—"
        rows += (f'<tr><td><a href="https://github.com/apache/logging-log4j2/pull/{r["pr"]}">'
                 f'#{r["pr"]}</a></td><td>{html.escape(r["state"])}</td><td>{html.escape(r["when"])}</td>'
                 f'<td>{html.escape(r["author"])}</td><td>{html.escape(r["posted"])}</td>'
                 f'<td>{link}</td><td>{ev}</td><td>{html.escape(r["note"])}</td></tr>')
    secs.append(f"""<section id="reviews" hidden><div class="hd"><h2>Reviews</h2>
    <span class="sub">{len(led)} in the ledger · {len(evid)} with evidence in .bench/reviews</span></div>
    <p class="sub">“posted” is whether the paste-ready comment went upstream. The write-up
    column is the file under <code>docs/pr-reviews/</code>; evidence is a
    <code>bench review &lt;n&gt;</code> run still on disk.</p>
    <div class="tw"><table><thead><tr><th>PR</th><th>state</th><th>reviewed</th><th>author</th>
    <th>posted</th><th>write-up</th><th>evidence</th><th>note</th></tr></thead>
    <tbody>{rows or '<tr><td colspan=8>ledger empty</td></tr>'}</tbody></table></div></section>""")

    # ---- docs
    for repo_name, base, paths in DOCS:
        nav.append(f'<div class="grp">{html.escape(repo_name)}</div>')
        for rel in paths:
            f = base / rel
            if not f.exists():
                continue
            sid = re.sub(r"[^a-z0-9]+", "-", f"{repo_name}-{rel}".lower()).strip("-")
            nav.append(f'<a href="#{sid}" data-t="{sid}">{html.escape(rel)}</a>')
            try:
                body = md_to_html(f.read_text(errors="replace"))
            except Exception as e:  # a doc that cannot be read is a fact, not a crash
                body = f"<p class='bad'>could not read: {html.escape(str(e))}</p>"
            secs.append(f'<section id="{sid}" hidden><div class="hd">'
                        f'<h2>{html.escape(rel)}</h2><span class="sub">{html.escape(repo_name)}</span></div>'
                        f'<div class="doc">{body}</div></section>')

    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%SZ")
    return f"""<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>bench hub</title><style>{CSS}</style></head><body>
<div class="wrap"><nav>{''.join(nav)}</nav><main>{''.join(secs)}
<div class="foot">generated {stamp} · read-only · reload to refresh</div></main></div>
<script>{JS}</script></body></html>"""


FLOW = """
One PR, end to end. Each step is the repo that owns it, and nothing is done twice.

## 1. Know what you are looking at — `oss-cli`

No clone, no build. Facts, conventions, and your own prior work on the changed paths.

```bash
oss-cli review 4218 --no-verdict     # facts and convention gates only
oss-cli review 4218                  # ...plus a local verdict, if Ollama is up
```

Evidence is cached by **head commit SHA**, not PR number — a push invalidates it
automatically, so you are never reading a review of code that no longer exists.

## 2. Judge it — by hand

The part no tool does. `docs/PR-REVIEW.md` §2, in this order:

- Who filed the linked issue, and when? Hours after your own issue, from an
  account with no history, means the PR was written *from your issue text* — its
  passing tests assert the specification you wrote.
- Does the fix match the bug, or overshoot it? Check **every** implementation of
  the thing being changed, not only the one that motivated it.
- Does it change something a working user config depends on?

## 3. Run it — `bench review`

```bash
bench review 4218                 # eleven steps, one directory
bench review 4218 --no-build      # steps 1-5 only, seconds
bench review 4301 --3x --full     # 3.x clone, full reactor
```

Everything happens in a throwaway git worktree, so your clone stays on its branch
and `~/.m2` is never overwritten. Safe to run with the clone open in an IDE.

The two steps that matter most, because a human cannot do them by reading:

- **RED** — the PR's *test files only*, on the base commit, without its fix. It
  must fail. A test that passes here asserts that the code loads, not that it was
  fixed. Read the verdict carefully: *fail as required*, *compile error* (weaker
  — it pins an API, not a behaviour), *PASS* (blocking finding), or
  *inconclusive* (the build broke before any test ran — never read this as red).
- **pollution** — did the tests write into the source tree?

Step 8 runs the whole module suite and **does not** tell you the PR is broken:
`log4j-core-test` is 8729 tests with its own failures on any machine. The summary
splits failures into classes the PR touches and classes it does not. Untouched
ones are *probably* pre-existing — and "probably" is not a review finding, so run
the base before writing it down.

## 4. Reproduce it — `bench repro`

```bash
bench run core-java --config xml/<cfg> --log4j 2.26.1
bench repro 4218 --pr --config xml/<cfg> --scenario <s> --log4j 2.25.5 --log4j 2.26.1
```

Baseline against **releases first**. `--install` overwrites `2.27.0-SNAPSHOT`, so
a baseline taken afterwards measures the PR twice.

A clean exit proves nothing: Log4j catches appender exceptions, reports them
through `StatusLogger`, and exits 0. Verify the artefact, not the exit code.

## 5. Write it up, then post — by hand

One file per PR under `docs/pr-reviews/`, ending in a paste-ready block. Draft
first, post second. Separate blocking from non-blocking so the author knows what
gates the merge.

```bash
bench followup --comment 4218        # read it
bench followup --comment 4218 | gh pr comment 4218 -R apache/logging-log4j2 --body-file -
```

## 6. Keep it — `--file`

```bash
bench review 4218 --file
```

Hands the write-up to `knowledge-creator/pr-review-file.py`, which files it under
`Projects/<topic>/pr-reviews/` and indexes it. This is the one artefact no
harvester can reconstruct: public threads record what was *said*, not the
reasoning that got there — including the parts you decided not to post.
"""


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_GET(self):
        if self.path.startswith("/status.json"):
            body = json.dumps({
                "stamp": "|".join(
                    f"{n}:{repo_state(p).get('head', '')}:{repo_state(p).get('dirty', 0)}"
                    for n, p, _, _ in REPOS)}).encode()
            ctype = "application/json"
        else:
            body = build().encode()
            ctype = "text/html; charset=utf-8"
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)


def main():
    ap = argparse.ArgumentParser(description="One local site over all three repos.")
    ap.add_argument("--port", type=int, default=8787)
    ap.add_argument("--once", action="store_true", help="write index.html and exit")
    ap.add_argument("--open", action="store_true", help="open a browser")
    args = ap.parse_args()

    missing = [n for n, p, _, _ in REPOS if not (p / ".git").exists()]
    if missing:
        print(f"note: not a git clone — {', '.join(missing)} "
              f"(set BENCH_OSSCLI_DIR / BENCH_KB_DIR)", file=sys.stderr)

    if args.once:
        out = WORKOUT / ".bench" / "hub" / "index.html"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(build())
        print(out)
        return

    url = f"http://localhost:{args.port}/"
    try:
        srv = HTTPServer(("127.0.0.1", args.port), Handler)
    except OSError as e:
        sys.exit(f"error: cannot bind {url} — {e}")
    print(f"bench hub on {url}   (ctrl-c to stop)", file=sys.stderr)
    if args.open:
        webbrowser.open(url)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("", file=sys.stderr)


if __name__ == "__main__":
    main()
