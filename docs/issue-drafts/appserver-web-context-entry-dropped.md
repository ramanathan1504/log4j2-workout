# DO NOT FILE — duplicate of #2314

[#2314](https://github.com/apache/logging-log4j2/issues/2314), open since
February 2024, is the same mechanism: `locateContext`'s parent walk returning an
ancestor's context. It already names #1430 and #2311 as consequences.

What is new here is the `log4j-appserver` + `log4j-web` trigger and a captured
before/after. That belongs as a **comment on #2314**, drafted in `docs/SLACK.md`,
not as a new issue.

**Title:** `ClassLoaderContextSelector.locateContext` parent walk returns a context without applying the `ServletContext` entry

---

## Description

When `log4j-appserver` and `log4j-web` are both present, the per-webapp
`LoggerContext` is not established and `${web:}` never resolves. The webapp ends
up sharing the container's context, which is additionally renamed to the webapp's
display name.

The mechanism is in
`log4j-core/src/main/java/org/apache/logging/log4j/core/selector/ClassLoaderContextSelector.java`,
lines 185–204:

```java
private LoggerContext locateContext(
        final ClassLoader loaderOrNull, final Map.Entry<String, Object> entry, final URI configLocation) {
    final ClassLoader loader = loaderOrNull != null ? loaderOrNull : ClassLoader.getSystemClassLoader();
    final String name = toContextMapKey(loader);
    AtomicReference<WeakReference<LoggerContext>> ref = CONTEXT_MAP.get(name);
    if (ref == null) {
        if (configLocation == null) {
            ClassLoader parent = loader.getParent();
            while (parent != null) {
                ref = CONTEXT_MAP.get(toContextMapKey(parent));
                if (ref != null) {
                    final WeakReference<LoggerContext> r = ref.get();
                    final LoggerContext ctx = r.get();
                    if (ctx != null) {
                        return ctx;          // <-- entry is never applied
                    }
                }
                parent = parent.getParent();
```

The `entry` parameter — which carries the `ServletContext` that `${web:}` and the
per-webapp naming depend on — is not applied on this return path. It is used only
when a context is created, not when one is inherited from a parent classloader.

## Why the two modules together trigger it

Alone, `log4j-web`'s `Log4jServletContainerInitializer` runs before anything has
registered a context for a parent classloader, so `locateContext` creates one and
applies the entry.

`log4j-appserver` routes the container's own logging into Log4j. Tomcat therefore
logs through Log4j *while starting*, before any webapp initialises, which
registers a context keyed to the parent classloader. By the time the SCI runs,
the parent walk finds that context and returns it — entry dropped.

So the failure appears only in the combination, which is a supported and
reasonable one: appserver exists precisely to capture container logging.

## Configuration

**Version:** 2.x @ `04c93c1d33`

**Operating system:** macOS 15 (Darwin 25.5.0)

**JDK:** Temurin 21

**Container:** Tomcat 9 (`javax`), with `log4j-appserver` and `log4j-web`

## Reproduction

1. Deploy a webapp with `log4j-web` **and** `log4j-appserver` on the container
   classpath.
2. Use `${web:contextPath}` or `${web:servletContextName}` in the configuration.
3. Ask the running webapp what context it got.

Captured on Log4j **2.26.1**, JDK 21, Tomcat 9 (`javax`):

```
LoggerContext class : org.apache.logging.log4j.core.LoggerContext
LoggerContext name  : log4j-bench-javax
Configuration       : baseline-console
Appenders           : [Console]
ServletContext bound: false
WebLoggerContextUtils.getServletContext(): null
```

Two symptoms in that output:

- `ServletContext bound: false` — the entry was supplied to `locateContext` and
  discarded, so `${web:}` cannot resolve.
- `LoggerContext name : log4j-bench-javax` — the *shared* container context has
  been renamed to this webapp's display name, which also affects anything else
  using it.

### The control

A sibling deployment **without** `log4j-appserver`, same configuration file:

```
LoggerContext class : org.apache.logging.log4j.core.LoggerContext
LoggerContext name  : log4j-bench
Configuration       : baseline-console
Appenders           : [Console]
Config source       : /Users/ramanathan/apache/log4j2-workout/configs/xml/baseline-console.xml
ServletContext bound to LoggerContext : true
WebLoggerContextUtils.getServletContext() : org.apache.catalina.core.ApplicationContextFacade@351afa4e
StrLookup                            : org.apache.logging.log4j.core.lookup.Interpolator

web lookups:
  ${web:contextPath}               /bench
  ${web:servletContextName}        log4j-bench
  ${web:serverInfo}                Apache Tomcat/10.1.36
  ${web:effectiveMajorVersion}     6
  ${web:rootDir}                   /private/var/folders/9p/3mz8f0gx5lzcxbx73gsvpbxw0000gn/T/log4j-bench-tomcat7916513248010340563/webapp/

this servlet's classloader   : ParallelWebappClassLoader@2d1ef81a
ServletContext classloader   : ParallelWebappClassLoader@2d1ef81a
LoggerContext resolved here  : LoggerContext@39bab98c
LoggerContext on ServletCtx  : LoggerContext@39bab98c

all LoggerContexts in this JVM:
  log4j-bench              servletContext=true config=baseline-console

servlet contextPath : /bench
server info         : Apache Tomcat/10.1.36
```

`ServletContext bound to LoggerContext : true`, the facade is present, and the
`${web:}` lookups resolve — `contextPath` to `/bench`, `serverInfo` to the
container banner. That is what the failing case should look like.

**One caveat, stated plainly:** this control is a sibling application on
Tomcat 10 / `jakarta.servlet` with `log4j-jakarta-web`, not the identical
deployment with `log4j-appserver` removed. It differs in servlet API as well as
in the presence of appserver, so it is strong evidence rather than a controlled
single-variable comparison.

Removing `log4j-appserver` from the failing deployment and redeploying is the
cleaner check, and worth doing before filing if a reviewer is likely to press on
it. The source path in `locateContext` explains the mechanism regardless of which
control is used.

## Note on classification

This may be the intended trade-off rather than a defect: inheriting a parent
context is deliberate, and the alternative — creating a distinct context whenever
an entry is supplied — would change behaviour for deployments that currently rely
on sharing.

What seems worth addressing either way is that it is **completely silent**. A
`ServletContext` entry is passed in and discarded with no diagnostic, so the
symptom is an unresolved lookup with nothing to connect it to a cause. A debug
line on the parent-walk return path, noting that an entry was supplied and not
applied, would make this diagnosable in minutes rather than by reading the
selector.
