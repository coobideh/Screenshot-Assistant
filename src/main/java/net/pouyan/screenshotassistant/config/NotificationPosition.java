package net.pouyan.screenshotassistant.config;

/**
 * Screen position where stacked screenshot notifications are rendered.
 *
 * TOP_*    — stack grows DOWNWARD (newest at top, older entries shift down).
 * BOTTOM_* — stack grows UPWARD   (newest at bottom, older entries shift up).
 *
 * For each variant the text is left-aligned, centered, or right-aligned
 * depending on the horizontal component.
 */
public enum NotificationPosition {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT
}
