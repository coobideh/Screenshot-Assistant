package net.pouyan.screenshotassistant.logic;

/**
 * Tracks the last time a screenshot was taken to enforce minimum shot intervals.
 *
 * Normal mode:
 *   Enforces {@code cooldownSeconds} between any two screenshots.
 *
 * Rapid-fire mode (when {@code enableRapidFire} is true):
 *   Allows consecutive screenshots as quickly as every {@code rapidFireIntervalMs}
 *   milliseconds.  The intent is to capture rapid back-to-back rule matches
 *   (e.g. several rules firing within seconds) without losing any.
 *   The global cooldown is replaced by the much shorter rapid-fire interval
 *   while this mode is active.
 */
public final class CooldownManager {

    private long lastTriggerMillis = 0L;

    /**
     * Try to record a new trigger, returning true if allowed.
     *
     * @param cooldownSeconds  Normal minimum gap in seconds (used when rapid-fire is off).
     * @param enableRapidFire  When true, use rapidFireIntervalMs instead.
     * @param rapidFireIntervalMs Minimum gap in ms for rapid-fire mode.
     */
    public boolean tryTrigger(int cooldownSeconds, boolean enableRapidFire, int rapidFireIntervalMs) {
        long now = System.currentTimeMillis();
        long minGapMs;
        if (enableRapidFire) {
            minGapMs = Math.max(50, rapidFireIntervalMs);   // floor at 50 ms
        } else {
            minGapMs = Math.max(0, cooldownSeconds) * 1000L;
        }
        if (now - lastTriggerMillis < minGapMs) return false;
        lastTriggerMillis = now;
        return true;
    }

    /**
     * Convenience overload for callers that only use the normal cooldown
     * (no rapid-fire awareness).
     */
    public boolean tryTrigger(int cooldownSeconds) {
        return tryTrigger(cooldownSeconds, false, 0);
    }
}
