# Issue #3832: Create a standalone artifact for non-internal test fixtures

https://github.com/apache/logging-log4j2/issues/3832

The current `log4j-core-test` module includes a variety of test fixtures primarily designed to test Log4j Core itself and its internal plugins. These fixtures assume that:

* Static loggers (other than `StatusLogger`) are absent from the code under test.
* A new `LoggerContext` can be created per test case.
* Loggers are created by the test cases, not the code under test.

This model works well for testing Log4j itself but breaks down in the context of third-party libraries and applications, where:

* Loggers are typically declared as `static final` fields.
* Logger initialization might happen before the test setup, making it impractical to substitute a different `LoggerContext` or logger instance at runtime.

As a result, most test fixtures in `log4j-core-test` are not reusable outside of Log4j’s own testing. The only widely useful component is `ListAppender`, which depends solely on Log4j Core. However, to use just `ListAppender`, developers must still depend on the entire `log4j-core-test` module—bringing in unnecessary dependencies and potential conflicts that often need to be manually excluded.

### Proposal

To improve the usability and modularity of Log4j test support for third-party projects:

1. **Extract `ListAppender` into a new, standalone artifact**
   This artifact would:

   * Be explicitly intended for use by external libraries and applications in testing scenarios.
   * Only include minimal and broadly useful test utilities like `ListAppender`.
   * Have the same backward compatibility guarantees as Log4j Core itself.

2. **Evaluate and incorporate improvements from past community feedback**
   In particular, we should revisit insights from this [dev@logging thread](https://lists.apache.org/thread/3n9xqc132pm49w3m67zm6wnjfw6yphkq)
   Specifically, we should:

   * Address concerns around using `ListAppender` in parallel test executions.
   * Improve thread-safety and isolation for better test reliability.

### Benefits

Although testing log output in unit tests is sometimes seen as excessive or even bad practice, there are specific cases where it is both justified and valuable:

* **Audit Logging Compliance**
  Verifying that audit logs are emitted correctly is crucial in systems where traceability and accountability are required. This aligns with one of the core purposes of Log4j Core itself.

* **Capturing Operational Details Not Exposed to Users**
  In many systems, user-facing error messages are intentionally vague for security or UX reasons. Logs often carry the detailed context needed for debugging, so it's useful to ensure those details are reliably recorded.

* **Monitoring Failures in Background Threads**
  Failures in asynchronous or background threads often can't propagate to the main execution flow. Logging may be the *only* way these failures surface, making it essential to validate they’re captured appropriately.

To support these use cases effectively, it's important to provide users with a lightweight, purpose-built artifact focused solely on general logging test utilities. This avoids the complexity and overhead of `log4j-core-test`, which includes many features intended only for internal testing of Log4j Core itself.


### 1. Refined Synchronization Strategy (The "Monitor" Pattern)
The maintainer explicitly asked to "mark all public methods synchronized." While the warning suggests caution against "blind" synchronization, in the specific case of a **Test Utility** like `ListAppender`, simplicity is more important than high-concurrency performance.

*   **Do:** Mark `append`, `getEvents`, `getMessages`, `getData`, and `clear` as `synchronized`.
*   **Don't:** Synchronize internal private helper methods that are only called by synchronized public methods (to avoid re-entrant overhead, though it's minor).
*   **Reasoning for the PR:** "Following the suggestion, I've implemented method-level synchronization. Since this is a test utility, a simple monitor-based approach provides the best balance of safety and readability."

### 2. The `toImmutable()` Justification
You need a clear answer ready for when the maintainer asks why you added this.
*   **The Argument:** "Log4j 2.x supports **garbage-free logging** where `LogEvent` objects are reused. If `ListAppender` stores the raw reference, the event data can change after it has been 'captured.' By calling `toImmutable()`, we guarantee the test is asserting against the actual state of the log at the moment it was generated."

### 3. "The Hammer Test" (Proving it works)
A test that doesn't fail when the fix is removed is a weak test. You should write a test that **would** throw a `ConcurrentModificationException` (CME) if `synchronized` were missing.

**The Implementation Secret:**
To trigger a CME, you need one thread **iterating** while another **modifies**.
```java
@Test
void testThreadSafety() throws Exception {
    ListAppender app = new ListAppender("test");
    // Start writer thread
    Thread writer = new Thread(() -> {
        for(int i=0; i<10000; i++) app.append(new Log4jLogEvent());
    });

    writer.start();

    // Main thread acts as reader
    for(int i=0; i<1000; i++) {
        List<LogEvent> events = app.getEvents();
        // If getEvents() doesn't return a safe snapshot,
        // iterating here would throw ConcurrentModificationException
        for(LogEvent e : events) {
            assertNotNull(e);
        }
    }
    writer.join();
}
```

### 4. Precise Javadoc (Words Matter)
Instead of a broad "This is thread-safe," use the precise language suggested:

```java
/**
 * Item-capturing Appender for use in unit tests.
 * <p>
 * This appender provides thread-safe access for use in testing scenarios.
 * Note that {@link #getEvents()} returns a snapshot copy of the captured events
 * to ensure thread-safety during iteration.
 * </p>
 */
```

### 5. Backward Compatibility (The "No Surprises" Rule)
*   **Ordering:** Ensure you use `ArrayList` (which preserves insertion order) because tests often rely on `getEvents().get(0)` being the first log.
*   **Empty State:** Ensure `getEvents()` returns an empty list (not `null`) if no logs have been captured, matching the current behavior.

---

### Final PR Description (Draft for Phase 1)

**Title:** Revamp ListAppender for thread-safety and snapshot stability

**Description:**
Following the discussion in Issue #3832, this PR revamps `ListAppender` to make it a reliable tool for multi-threaded testing.

**Key Changes:**
*   **Thread-Safety:** Transitioned to a consistent monitor-based synchronization model. All public accessors and the `append` method are now `synchronized`.
*   **Snapshot Semantics:** `getEvents()`, `getMessages()`, and `getData()` now return snapshot copies. This prevents `ConcurrentModificationException` if the appender continues to receive logs while a test is asserting against the returned list.
*   **Garbage-Free Support:** Now calls `event.toImmutable()` during `append()`. This ensures that captured events remain stable even when Log4j's garbage-free (reusable) event mechanism is active.
*   **Documentation:** Updated Javadoc to clarify thread-safety guarantees and the behavior of the `CountDownLatch`.
*   **Testing:** Added a concurrency "hammer" test to verify that simultaneous reads and writes do not result in data corruption or exceptions.

---

### What to do now?
1.  **Post the reply** we drafted in the previous turn.
2.  **Wait** for the "recipe."
3.  If he doesn't reply in 48 hours, **implement Phase 1** using the "Hammer Test" and the "Monitor Pattern" described above.

This level of detail shows you are not just a "coder," but a "software engineer." Good luck!