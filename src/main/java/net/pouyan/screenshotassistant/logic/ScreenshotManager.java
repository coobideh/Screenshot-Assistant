package net.pouyan.screenshotassistant.logic;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.pouyan.screenshotassistant.config.ConfigManager;
import net.pouyan.screenshotassistant.config.ModConfig;
import net.pouyan.screenshotassistant.visual.ScreenshotEffect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Resolves the destination folder and captures/saves a screenshot.
 *
 * Filename policy (v0.7.x):
 *   Files are named using Minecraft's standard timestamp format:
 *     {@code yyyy-MM-dd_HH.mm.ss.png}
 *   If a file with that name already exists (rare — two shots in the same
 *   second), a numeric suffix is appended:
 *     {@code yyyy-MM-dd_HH.mm.ss_2.png}, {@code …_3.png}, etc.
 *
 *   The per-folder display counter ({@link ModConfig#screenshotCounter}) is
 *   incremented as before and shown in HUD notifications, but it is no longer
 *   used as the actual filename.
 *
 * Threading model (unchanged):
 *   1. Path resolution + counter bump → render thread  (pure logic, zero I/O)
 *   2. Framebuffer capture            → render thread  (must stay here)
 *   3. Sound / subtitle effects       → render thread  (no I/O, lightweight)
 *   4. PNG encode + disk write        → background thread (all blocking I/O)
 *   5. Config save + HUD notification → background → re-dispatched to render
 *
 * NOTE (MC 1.21.5+): {@link ScreenshotRecorder#takeScreenshot} no longer
 * returns a {@link NativeImage} directly; it instead accepts a
 * {@code Consumer<NativeImage>} callback that is invoked once the capture is
 * ready. Steps 3-5 are therefore performed inside that callback rather than
 * sequentially after a direct method return, but the same thread boundaries
 * still apply (the callback fires on the render thread).
 */
public final class ScreenshotManager {

    /** Minecraft's standard screenshot timestamp format. */
    private static final DateTimeFormatter MC_TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    /**
     * Capture a screenshot triggered by a rule.
     *
     * @param ruleFolderName the rule's folder name (null/blank = base folder).
     */
    public void takeRuleScreenshot(String ruleFolderName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        final String folder = (ruleFolderName == null) ? "" : ruleFolderName.trim();
        client.execute(() -> doTakeAndSave(client, folder));
    }

    private void doTakeAndSave(MinecraftClient client, String ruleFolderName) {
        ModConfig config = ConfigManager.get();

        // ── 1. Resolve target directory (pure logic, no I/O) ─────────────────
        final Path targetDir = resolveTargetDirectory(config, ruleFolderName);

        // ── 2. Bump display counter (per-folder, for notification "#N") ───────
        String folderKey = targetDir.toAbsolutePath().normalize().toString();
        if (!folderKey.equals(config.counterFolderKey)) {
            config.counterFolderKey  = folderKey;
            config.screenshotCounter = 1;
        }
        final int displayNumber = config.screenshotCounter;
        config.screenshotCounter = displayNumber + 1;

        // ── 3. Capture framebuffer (render thread only) ───────────────────────
        // MC 1.21.5+: takeScreenshot() is now callback-based (void return).
        // The callback below fires on the render thread once the capture is
        // ready, so steps 4 (effects) and 5 (save) run from inside it.
        Framebuffer framebuffer = client.getFramebuffer();
        ScreenshotRecorder.takeScreenshot(framebuffer, image ->
                onImageCaptured(client, image, targetDir, ruleFolderName, displayNumber));
    }

    /**
     * Invoked (on the render thread) once {@link ScreenshotRecorder} has
     * finished capturing the framebuffer into a {@link NativeImage}.
     * Fires the immediate sound/subtitle effect, then hands the image off
     * to a background thread for PNG encode + disk write.
     */
    private void onImageCaptured(MinecraftClient client, NativeImage image, Path targetDir,
                                  String ruleFolderName, int displayNumber) {
        // ── 4. Fire immediate effects (sound / subtitle) — zero I/O ──────────
        ScreenshotEffect.trigger(displayNumber);

        // ── 5. Encode PNG + write to disk on a background thread ─────────────
        Thread saveThread = new Thread(() -> {
            try {
                Files.createDirectories(targetDir);

                // Generate MC-style timestamp filename with collision avoidance
                String filename = generateMcFilename(targetDir);
                Path outputPath = targetDir.resolve(filename);

                image.writeTo(outputPath.toFile());
                ConfigManager.save();

                // HUD notification (render thread)
                String msg = "Screenshot Assistant: saved " + filename;
                if (!ruleFolderName.isBlank()) msg += "  [" + ruleFolderName + "]";
                Text notifText = ScreenshotEffect.buildNotificationText(msg);
                if (notifText != null) {
                    client.execute(() -> {
                        try {
                            if (client.inGameHud != null)
                                client.inGameHud.setOverlayMessage(notifText, false);
                        } catch (RuntimeException ignored) {}
                    });
                }
            } catch (IOException e) {
                System.err.println("[Screenshot Assistant] Failed to save screenshot: " + e);
            } finally {
                image.close();
            }
        }, "screenshotassistant-save");
        saveThread.setDaemon(true);
        saveThread.setPriority(Thread.MIN_PRIORITY);
        saveThread.start();
    }

    /**
     * Generate a Minecraft-style timestamp filename, avoiding collisions if a
     * file with the same name already exists in {@code targetDir}.
     *
     * Format: {@code yyyy-MM-dd_HH.mm.ss.png}
     * Collision: {@code yyyy-MM-dd_HH.mm.ss_2.png}, {@code _3.png}, …
     *
     * This method is called on the background save thread after the directory
     * has been created, so {@link Files#exists} checks are safe here.
     */
    private static String generateMcFilename(Path targetDir) {
        String base = LocalDateTime.now().format(MC_TIMESTAMP_FMT);
        // First candidate: plain timestamp
        if (!Files.exists(targetDir.resolve(base + ".png"))) {
            return base + ".png";
        }
        // Collision — append _2, _3, … until free
        int n = 2;
        while (n < 10_000) {   // safety cap
            String candidate = base + "_" + n + ".png";
            if (!Files.exists(targetDir.resolve(candidate))) return candidate;
            n++;
        }
        // Fallback: append nanoseconds (practically unreachable)
        return base + "_" + System.nanoTime() + ".png";
    }

    /**
     * Resolves the target directory path without performing any I/O.
     *
     * Hierarchy: {@code <baseSavePath> / [ruleFolderName] / [weeklyFolder]}
     */
    private Path resolveTargetDirectory(ModConfig config, String ruleFolderName) {
        Path base;
        String bsp = (config.baseSavePath == null) ? "" : config.baseSavePath.trim();
        if (bsp.isEmpty()) {
            base = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        } else {
            base = Paths.get(bsp);
        }

        if (ruleFolderName != null && !ruleFolderName.isBlank()) {
            base = base.resolve(ruleFolderName.trim());
        }

        if (config.weeklyRotationEnabled) {
            String weekFolder = WeekFolderNaming.currentWeekFolderName(LocalDateTime.now(), config);
            base = base.resolve(weekFolder);
        }

        return base;
    }
}
