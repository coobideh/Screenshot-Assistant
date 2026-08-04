package net.pouyan.screenshotassistant.visual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.pouyan.screenshotassistant.config.CaptureSound;
import net.pouyan.screenshotassistant.config.ConfigManager;
import net.pouyan.screenshotassistant.config.MessageColorMode;
import net.pouyan.screenshotassistant.config.ModConfig;
import net.pouyan.screenshotassistant.config.NotificationPosition;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages screenshot audio/visual feedback:
 *   • Camera sound (with configurable volume)
 *   • Rainbow / coloured HUD notification text
 *   • Cinematic subtitle (restarts cleanly on every consecutive screenshot)
 *   • Stacked HUD notifications (configurable position on screen)
 *
 * Consecutive-screenshot behaviour:
 *   Each call to {@link #trigger} adds a fresh entry to the stacked notification
 *   queue.  The newest entry appears at the BOTTOM (or TOP for top-anchored
 *   positions); older entries shift away and fade out sooner.
 *   The cinematic subtitle animation resets on every trigger.
 *
 * Call {@link #trigger(int)}             from the render thread (via client.execute).
 * Call {@link #tick}                     from END_CLIENT_TICK.
 * Call {@link #renderStackedNotifications} from HudRenderCallback.
 */
public final class ScreenshotEffect {

    // ── Rainbow phase ─────────────────────────────────────────────────────────
    private static int rainbowPhase = 0;

    // ── Stacked notification queue ────────────────────────────────────────────
    // Head = newest, tail = oldest.
    private static final Deque<StackedEntry> STACK = new ArrayDeque<>();

    // Timing (ticks)
    private static final int FADE_IN_TICKS  = 6;
    private static final int HOLD_TICKS     = 70;
    private static final int FADE_OUT_TICKS = 25;
    private static final int TOTAL_TICKS    = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;

    /** Pixels between stacked notification lines. */
    private static final int LINE_HEIGHT = 13;

    /** Margin from screen edges in pixels. */
    private static final int MARGIN = 10;

    private ScreenshotEffect() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fire all enabled effects.  Must be called on the render/client thread.
     *
     * @param screenshotNumber the per-folder display counter for this shot
     *                         (shown as "#N" when showScreenshotNumberInNotification is on).
     */
    public static void trigger(int screenshotNumber) {
        ModConfig cfg = ConfigManager.get();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // ── Sound ─────────────────────────────────────────────────────────────
        if (cfg.enableCaptureSound && cfg.captureSound != CaptureSound.NONE) {
            float volume = Math.max(0f, Math.min(1.5f, cfg.captureSoundVolume / 100.0f));
            playSound(client, cfg.captureSound, volume);
        }

        // Build the display text (with optional "#N" prefix)
        String baseText = (cfg.subtitleText == null || cfg.subtitleText.isBlank())
                ? "\uD83D\uDCF8 Screenshot Saved!"
                : cfg.subtitleText;
        String displayText = cfg.showScreenshotNumberInNotification
                ? "\uD83D\uDCF8 #" + screenshotNumber + " " +
                  (cfg.subtitleText == null || cfg.subtitleText.isBlank()
                          ? "Screenshot Saved!"
                          : cfg.subtitleText)
                : baseText;

        // ── Cinematic subtitle ────────────────────────────────────────────────
        if (cfg.enableCinematicSubtitle && client.inGameHud != null) {
            // Set subtitle text first, then title (a non-empty literal space so
            // the subtitle actually renders — an empty title suppresses it in MC),
            // then restart ticks so the animation begins cleanly every time.
            client.inGameHud.setSubtitle(
                    Text.literal(displayText).formatted(toFormatting(cfg.subtitleColorMode)));
            client.inGameHud.setTitle(Text.literal(" "));
            client.inGameHud.setTitleTicks(5, 45, 20);
        }

        // ── Stacked HUD notifications ─────────────────────────────────────────
        if (cfg.enableStackedNotifications) {
            int max = Math.max(1, Math.min(10, cfg.maxStackedNotifications));
            STACK.addFirst(new StackedEntry(displayText, cfg.subtitleColorMode));
            while (STACK.size() > max) STACK.removeLast();
        }
    }

    /** Call every game tick to advance rainbow phase and notification timers. */
    public static void tick() {
        rainbowPhase = (rainbowPhase + 1) % 420;

        if (!STACK.isEmpty()) {
            for (StackedEntry e : STACK) e.ticksAlive++;
            STACK.removeIf(e -> e.ticksAlive >= TOTAL_TICKS);
        }
    }

    /**
     * Render all active stacked notifications onto the HUD.
     *
     * Position and stacking direction are controlled by
     * {@link ModConfig#notificationPosition}.
     *
     * TOP_* positions: newest at top, older entries shift downward.
     * BOTTOM_* positions: newest at bottom, older entries shift upward.
     * Horizontal alignment follows the LEFT/CENTER/RIGHT component.
     *
     * No-op when stacked notifications are disabled or the queue is empty.
     */
    public static void renderStackedNotifications(DrawContext context) {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.enableStackedNotifications || STACK.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        NotificationPosition pos = cfg.notificationPosition != null
                ? cfg.notificationPosition
                : NotificationPosition.BOTTOM_CENTER;

        // Entries: index 0 = newest
        StackedEntry[] entries = STACK.toArray(new StackedEntry[0]);

        for (int i = 0; i < entries.length; i++) {
            StackedEntry entry = entries[i];

            int alpha = computeAlpha(entry.ticksAlive);
            if (alpha <= 0) continue;

            String text  = entry.text;
            int textW    = client.textRenderer.getWidth(text);

            // ── X (horizontal alignment) ──────────────────────────────────────
            int x;
            switch (pos) {
                case TOP_LEFT:
                case BOTTOM_LEFT:
                    x = MARGIN;
                    break;
                case TOP_RIGHT:
                case BOTTOM_RIGHT:
                    x = screenW - textW - MARGIN;
                    break;
                default: // CENTER variants
                    x = (screenW - textW) / 2;
                    break;
            }

            // ── Y (vertical position, offset per stack depth) ─────────────────
            int y;
            switch (pos) {
                case TOP_LEFT:
                case TOP_CENTER:
                case TOP_RIGHT:
                    // Newest at top (i=0 → smallest Y); older entries move down
                    y = MARGIN + i * LINE_HEIGHT;
                    break;
                default: // BOTTOM_*
                    // Newest at bottom (i=0 → largest Y); older entries move up
                    y = screenH - MARGIN - 20 - i * LINE_HEIGHT;
                    break;
            }

            int rgb  = colorModeToRgb(entry.colorMode);
            int argb = (alpha << 24) | (rgb & 0x00FFFFFF);

            context.drawText(client.textRenderer, text, x, y, argb, true);
        }
    }

    /**
     * Build a coloured / rainbow Text for the HUD overlay notification.
     * Returns null when cinematic subtitle or stacked notifications are active
     * (to avoid duplicate feedback).
     */
    public static Text buildNotificationText(String base) {
        ModConfig cfg = ConfigManager.get();
        if (cfg.enableCinematicSubtitle || cfg.enableStackedNotifications) return null;
        if (!cfg.enableRainbowMessage) return Text.literal(base);
        return switch (cfg.messageColorMode) {
            case GOLD         -> Text.literal(base).formatted(Formatting.GOLD);
            case GREEN        -> Text.literal(base).formatted(Formatting.GREEN);
            case AQUA         -> Text.literal(base).formatted(Formatting.AQUA);
            case LIGHT_PURPLE -> Text.literal(base).formatted(Formatting.LIGHT_PURPLE);
            case WHITE        -> Text.literal(base).formatted(Formatting.WHITE);
            case RAINBOW      -> buildRainbow(base);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static int computeAlpha(int ticksAlive) {
        float alpha;
        if (ticksAlive < FADE_IN_TICKS) {
            alpha = (float) ticksAlive / FADE_IN_TICKS;
        } else if (ticksAlive < FADE_IN_TICKS + HOLD_TICKS) {
            alpha = 1.0f;
        } else {
            int fp = ticksAlive - FADE_IN_TICKS - HOLD_TICKS;
            alpha = 1.0f - (float) fp / FADE_OUT_TICKS;
        }
        return Math.max(0, Math.min(255, (int) (alpha * 255)));
    }

    private static int colorModeToRgb(MessageColorMode mode) {
        if (mode == null) return 0xFFAA00;
        return switch (mode) {
            case GOLD         -> 0xFFAA00;
            case GREEN        -> 0x55FF55;
            case AQUA         -> 0x55FFFF;
            case LIGHT_PURPLE -> 0xFF55FF;
            case WHITE        -> 0xFFFFFF;
            case RAINBOW      -> rainbowRgb();
        };
    }

    private static int rainbowRgb() {
        int[][] colours = {
            {0xFF,0x55,0x55},{0xFF,0xAA,0x00},{0xFF,0xFF,0x55},
            {0x55,0xFF,0x55},{0x55,0xFF,0xFF},{0x55,0x55,0xFF},{0xFF,0x55,0xFF}
        };
        int[] c = colours[(rainbowPhase / 60) % colours.length];
        return (c[0] << 16) | (c[1] << 8) | c[2];
    }

    private static Formatting toFormatting(MessageColorMode mode) {
        if (mode == null) return Formatting.GOLD;
        return switch (mode) {
            case GOLD         -> Formatting.GOLD;
            case GREEN        -> Formatting.GREEN;
            case AQUA         -> Formatting.AQUA;
            case LIGHT_PURPLE -> Formatting.LIGHT_PURPLE;
            case WHITE        -> Formatting.WHITE;
            case RAINBOW      -> Formatting.GOLD;
        };
    }

    private static Text buildRainbow(String text) {
        Formatting[] colours = {
            Formatting.RED, Formatting.GOLD, Formatting.YELLOW,
            Formatting.GREEN, Formatting.AQUA, Formatting.BLUE, Formatting.LIGHT_PURPLE
        };
        MutableText result = Text.empty();
        for (int i = 0; i < text.length(); i++) {
            Formatting c = colours[(i + rainbowPhase / 10) % colours.length];
            result.append(Text.literal(String.valueOf(text.charAt(i))).formatted(c));
        }
        return result;
    }

    private static void playSound(MinecraftClient client, CaptureSound choice, float volume) {
        if (client.getSoundManager() == null) return;
        switch (choice) {
            case CAMERA     -> client.getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.UI_TOAST_IN, 1.0f, volume));
            case XP_ORB     -> client.getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, volume));
            case LEVEL_UP   -> client.getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, volume));
            case NOTE_PLING -> client.getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, volume));
            case NONE       -> {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static final class StackedEntry {
        final String text;
        final MessageColorMode colorMode;
        int ticksAlive;

        StackedEntry(String text, MessageColorMode colorMode) {
            this.text      = text;
            this.colorMode = colorMode != null ? colorMode : MessageColorMode.GOLD;
        }
    }
}
