# DRAFT — not filed

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
3. Observe the lookup rendering unresolved, and the context named after the
   webapp's display name rather than scoped to it.
4. Remove `log4j-appserver` and redeploy: the same configuration resolves.

A control is useful here — the same application without appserver behaves
correctly, which isolates the interaction from ordinary webapp misconfiguration.

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
