package net.pouyan.screenshotassistant;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.pouyan.screenshotassistant.config.ConfigManager;
import net.pouyan.screenshotassistant.config.ModConfig;
import net.pouyan.screenshotassistant.config.RuleConfig;
import net.pouyan.screenshotassistant.gui.RuleManagerScreen;
import net.pouyan.screenshotassistant.gui.ScreenshotBrowserScreen;
import net.pouyan.screenshotassistant.logic.ChatMatcher;
import net.pouyan.screenshotassistant.logic.CooldownManager;
import net.pouyan.screenshotassistant.logic.ScreenshotManager;
import net.pouyan.screenshotassistant.visual.ScreenshotEffect;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Screenshot Assistant | Client-side Fabric entry point.
 *
 * Keybindings:
 *   F6 – Open Rule Manager
 *   F7 – Open Screenshot Browser
 *
 * Capture delay:
 *   When config.captureDelayMs > 0 the screenshot is queued and fired on the
 *   next game tick that falls after the deadline.
 *
 * Cooldown / rapid-fire:
 *   Normal mode  → cooldownSeconds enforced globally.
 *   Rapid-fire   → minimum gap reduced to rapidFireIntervalMs (default 200 ms)
 *                  to allow rapid back-to-back rule matches to all fire.
 *
 * HUD rendering:
 *   Stacked screenshot notifications are rendered via {@link HudRenderCallback}.
 */
public class ScreenshotAssistantClient implements ClientModInitializer {

    private static KeyBinding openConfigKey;
    private static KeyBinding openBrowserKey;

    private final CooldownManager   cooldownManager   = new CooldownManager();
    private final ScreenshotManager screenshotManager = new ScreenshotManager();

    private final List<PendingShot> pendingScreenshots = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        ConfigManager.get();

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.screenshotassistant.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "key.categories.screenshotassistant"
        ));

        openBrowserKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.screenshotassistant.open_browser",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                "key.categories.screenshotassistant"
        ));

        // ── Tick ──────────────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.wasPressed()) {
                if (client.currentScreen == null)
                    client.setScreen(new RuleManagerScreen(null));
            }
            while (openBrowserKey.wasPressed()) {
                if (client.currentScreen == null)
                    client.setScreen(new ScreenshotBrowserScreen(null));
            }

            if (!pendingScreenshots.isEmpty()) {
                long now = System.currentTimeMillis();
                Iterator<PendingShot> iter = pendingScreenshots.iterator();
                while (iter.hasNext()) {
                    PendingShot shot = iter.next();
                    if (now >= shot.fireAtMs) {
                        screenshotManager.takeRuleScreenshot(shot.ruleFolderName);
                        iter.remove();
                    }
                }
            }

            ScreenshotEffect.tick();
        });

        // ── HUD: stacked notifications ────────────────────────────────────────
        HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
                ScreenshotEffect.renderStackedNotifications(drawContext));

        // ── Chat listeners ────────────────────────────────────────────────────
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                handleIncomingLine(message.getString(), sender != null ? sender.getName() : null));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handleIncomingLine(message.getString(), null);
        });
    }

    private void handleIncomingLine(String rawText, String sender) {
        ModConfig config = ConfigManager.get();
        for (RuleConfig rule : config.rules) {
            if (ChatMatcher.matches(rule, rawText, sender)) {
                // Pass rapid-fire config to the cooldown manager
                if (cooldownManager.tryTrigger(
                        config.cooldownSeconds,
                        config.enableRapidFire,
                        config.rapidFireIntervalMs)) {
                    int delay = Math.max(0, config.captureDelayMs);
                    if (delay == 0) {
                        screenshotManager.takeRuleScreenshot(rule.ruleFolderName);
                    } else {
                        pendingScreenshots.add(new PendingShot(
                                System.currentTimeMillis() + delay,
                                rule.ruleFolderName));
                    }
                }
                return;
            }
        }
    }

    private static final class PendingShot {
        final long   fireAtMs;
        final String ruleFolderName;

        PendingShot(long fireAtMs, String ruleFolderName) {
            this.fireAtMs       = fireAtMs;
            this.ruleFolderName = (ruleFolderName == null) ? "" : ruleFolderName;
        }
    }
}
