package net.pouyan.screenshotassistant.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain data holder for all mod settings. Serialised as JSON by {@link ConfigManager}.
 *
 * Filename policy (v0.7.x):
 *   Screenshots are now saved with Minecraft's standard timestamp filename
 *   (e.g. 2026-08-04_12.34.56.png) instead of a numeric counter.
 *   {@link #screenshotCounter} is kept as a per-folder display counter used
 *   only in HUD notifications ("#42 Screenshot Saved!") — it is NOT used as
 *   the filename any more.
 */
public class ModConfig {

    // ------------------------------------------------------------------ UI
    public ModLanguage modLanguage = ModLanguage.ENGLISH;

    // ------------------------------------------------------------------ General
    /** Anti-spam cooldown in seconds; 0 disables it. */
    public int cooldownSeconds = 1;

    /**
     * Wait this many ms after a matching chat line before capturing.
     * Useful with "Chat Animations" mod (set 700-750). 0 = immediate.
     */
    public int captureDelayMs = 0;

    // ------------------------------------------------------------------ Rapid-fire
    /**
     * When enabled, consecutive screenshots from back-to-back rule matches
     * can fire as quickly as every {@link #rapidFireIntervalMs} milliseconds,
     * bypassing the global {@link #cooldownSeconds} for rapid sequences.
     *
     * The global cooldown still applies between separate "bursts"; this only
     * reduces the minimum gap WITHIN a burst of consecutive matches.
     */
    public boolean enableRapidFire = true;

    /**
     * Minimum milliseconds between screenshots when rapid-fire mode is active.
     * Default: 200 ms.  Range: 50–2000.
     */
    public int rapidFireIntervalMs = 200;

    // -------------------------------------------------------- Storage & naming
    /**
     * Base directory for ALL screenshots taken by this mod.
     * Empty = .minecraft/screenshots.  Non-empty = absolute path.
     */
    public String baseSavePath = "";

    // --- Legacy migration fields (read-only, cleared after first load) -------
    @Deprecated public FolderMode folderMode = null;
    @Deprecated public String folderValue = null;
    // -------------------------------------------------------------------------

    // ------------------------------------------------------- Weekly rotation
    public boolean weeklyRotationEnabled = false;
    public int weekStartDayOfWeek = 4;  // 1=Mon … 7=Sun; default Thu
    public int weekStartHour = 0;
    public CalendarType calendarType = CalendarType.GREGORIAN;
    public WeekNamingMode weekNamingMode = WeekNamingMode.DATE_RANGE;

    // --------------------------------------------------------- Display counter
    /**
     * Running per-folder screenshot count.
     * Used ONLY for the "#N" display in HUD notifications.
     * The actual filename uses a timestamp (MC's default format).
     */
    public int screenshotCounter = 1;

    /** Absolute path of the folder that {@link #screenshotCounter} tracks. */
    public String counterFolderKey = "";

    // ----------------------------------------------------------------- Accessibility
    public boolean fixTooltipPosition = true;

    // ============================================================ Visual Effects

    // ── Sound ──────────────────────────────────────────────────────────────────
    public boolean enableCaptureSound = false;
    public CaptureSound captureSound = CaptureSound.CAMERA;

    /**
     * Volume of the capture sound as a percentage.
     * 100 = normal volume.  Range: 0–150.  Default: 100.
     */
    public int captureSoundVolume = 100;

    // ── HUD notification message (plain overlay) ──────────────────────────────
    public boolean enableRainbowMessage = false;
    public MessageColorMode messageColorMode = MessageColorMode.RAINBOW;

    // ── Cinematic subtitle ────────────────────────────────────────────────────
    /**
     * Show a subtitle via MC's title system on each screenshot.
     * The animation RESTARTS cleanly on every consecutive screenshot.
     */
    public boolean enableCinematicSubtitle = false;

    /**
     * Text shown in the cinematic subtitle AND stacked notifications.
     * Leave blank to use the default: "📸 Screenshot Saved!"
     */
    public String subtitleText = "";

    /**
     * Colour for the cinematic subtitle and stacked notification entries.
     */
    public MessageColorMode subtitleColorMode = MessageColorMode.GOLD;

    // ── Screenshot number in notification ─────────────────────────────────────
    /**
     * When true, the per-folder screenshot counter is prepended to the
     * notification / subtitle text, e.g. "📸 #42 Screenshot Saved!".
     * The counter resets when the target folder changes.
     */
    public boolean showScreenshotNumberInNotification = false;

    // ── Stacked notifications ─────────────────────────────────────────────────
    /**
     * Show consecutive screenshot alerts as a vertical stack in the HUD.
     * Newest appears at the BOTTOM (or TOP, based on {@link #notificationPosition});
     * older entries shift away and fade out sooner.
     */
    public boolean enableStackedNotifications = false;

    /** Maximum simultaneous stack entries.  Range: 1–10.  Default: 3. */
    public int maxStackedNotifications = 3;

    /**
     * Screen position for the stacked notification stack.
     * BOTTOM_CENTER by default (centered, just above the hotbar).
     */
    public NotificationPosition notificationPosition = NotificationPosition.BOTTOM_CENTER;

    // ------------------------------------------------------------------ Rules
    public List<RuleConfig> rules = new ArrayList<>();
}
