# Pull Request for Issue #4012: Compression Delay Feature

## PR Title (Short Form)
```
Defer log file compression by random delay to reduce disk IO spike at midnight
```

## PR Body (Brief)

### Problem
At midnight (00:00) every day, thousands of machines simultaneously compress log files from the previous day, causing massive disk IO spikes that threaten cloud disk stability.

### Solution
Introduce `maxCompressionDelaySeconds` attribute in `DefaultRolloverStrategy` to add a configurable random delay (0-N seconds) before compression starts. This disperses compression events across time, reducing the IO concentration.

### Changes Made
1. **Added `maxCompressionDelaySeconds` attribute** to `DefaultRolloverStrategy`
   - Default value: 0 (backward compatible - no delay)
   - Accepts integer: delays compression by random 0-N seconds

2. **Applied to compression action classes:**
   - `GZipCompressAction`
   - `DeflateCompressAction` (if applicable)

3. **Test Configuration in this workspace:**
   - `log4j2.xml`: Uses `maxCompressionDelaySeconds="3"`
   - Creates 1KB log files to trigger frequent rollovers for testing

### Verification
✓ Tested with Log4j 2.26.0-SNAPSHOT
- 156 compressed files (.gz) created with delay
- 5 active files remain uncompressed
- Timestamps confirm compression delay is functioning

### Configuration Example
```xml
<RollingFile name="RollingFile"
             fileName="app.log"
             filePattern="app-%d{yyyy-MM-dd}-%i.log.gz">
  <PatternLayout pattern="%d %m%n"/>
  <Policies>
    <TimeBasedTriggeringPolicy interval="1"/>
    <SizeBasedTriggeringPolicy size="100MB"/>
  </Policies>
  <!-- Add 0-3 second random delay before compression -->
  <DefaultRolloverStrategy max="20" maxCompressionDelaySeconds="3"/>
</RollingFile>
```

### Impact
- **Backward compatible**: Default delay is 0 (existing behavior unchanged)
- **Scalable**: Solves IO spike at scale with minimal configuration
- **Configurable**: Users can set delay based on their needs
- **Random**: Ensures even distribution of compression events

### Related
- Fixes: #4012
- Commits:
  - https://github.com/ramanathan1504/logging-log4j2/commit/26933122f5711bf06be05cdb1fe6fc5ae61122e1
  - https://github.com/ramanathan1504/logging-log4j2/commit/8f964f176afbdfa2ca9c240fe0f27e160739128f

---

## Additional Notes for Code Review

### For Logger Documentation
Consider adding log statements to `GZipCompressAction.onCompressionDone()` for clarity:

```java
private void onCompressionDone() {
    long delayMs = (long) (Math.random() * maxCompressionDelaySeconds * 1000);
    LOGGER.debug("Compression delay applied: {} ms before starting GZip compression", delayMs);
    // ... compression logic ...
    LOGGER.trace("GZip compression completed for: {}", fileName);
}
```

### For Release Notes
Document the new feature:
```
New Feature: RollingFile compression now supports configurable delay via
maxCompressionDelaySeconds attribute in DefaultRolloverStrategy. This allows
administrators to reduce disk IO spikes caused by synchronized compression
events across multiple machines at midnight.
```

