package net.pouyan.screenshotassistant.gui;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.pouyan.screenshotassistant.config.CalendarType;
import net.pouyan.screenshotassistant.config.CaptureSound;
import net.pouyan.screenshotassistant.config.ConfigManager;
import net.pouyan.screenshotassistant.config.MessageColorMode;
import net.pouyan.screenshotassistant.config.ModConfig;
import net.pouyan.screenshotassistant.config.ModLanguage;
import net.pouyan.screenshotassistant.config.NotificationPosition;
import net.pouyan.screenshotassistant.config.WeekNamingMode;
import net.pouyan.screenshotassistant.lang.Lang;

import java.nio.file.Path;

public final class ClothConfigScreenFactory {

    private ClothConfigScreenFactory() {}

    public static Screen build(Screen parent) {
        ModConfig config = ConfigManager.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Lang.t("screenshotassistant.title"))
                .setSavingRunnable(() -> {
                    ConfigManager.save();
                    Lang.invalidate();
                });

        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory all = builder.getOrCreateCategory(
                Lang.t("screenshotassistant.settings.tab"));

        // ════════ UI & Language ════════
        all.addEntry(eb.startTextDescription(header("screenshotassistant.section.ui")).build());

        all.addEntry(eb.startEnumSelector(
                        Lang.t("screenshotassistant.option.modLanguage"),
                        ModLanguage.class, config.modLanguage)
                .setDefaultValue(ModLanguage.ENGLISH)
                .setEnumNameProvider(v ->
                        v == ModLanguage.PERSIAN ? Text.literal("فارسی") : Text.literal("English"))
                .setTooltip(Lang.t("screenshotassistant.option.modLanguage.tooltip"))
                .setSaveConsumer(v -> { config.modLanguage = v; Lang.invalidate(); })
                .build());

        // ════════ General ════════
        all.addEntry(eb.startTextDescription(header("screenshotassistant.section.general")).build());

        all.addEntry(eb.startIntField(
                        Lang.t("screenshotassistant.option.cooldownSeconds"),
                        config.cooldownSeconds)
                .setDefaultValue(1).setMin(0).setMax(3600)
                .setTooltip(Lang.t("screenshotassistant.option.cooldownSeconds.tooltip"))
                .setSaveConsumer(v -> config.cooldownSeconds = v)
                .build());

        all.addEntry(eb.startIntField(
                        Lang.t("screenshotassistant.option.captureDelayMs"),
                        config.captureDelayMs)
                .setDefaultValue(0).setMin(0).setMax(10000)
                .setTooltip(Lang.t("screenshotassistant.option.captureDelayMs.tooltip"))
                .setSaveConsumer(v -> config.captureDelayMs = v)
                .build());

        all.addEntry(eb.startBooleanToggle(
                        Lang.t("screenshotassistant.option.fixTooltipPosition"),
                        config.fixTooltipPosition)
                .setDefaultValue(true)
                .setTooltip(Lang.t("screenshotassistant.option.fixTooltipPosition.tooltip"))
                .setSaveConsumer(v -> config.fixTooltipPosition = v)
                .build());

        // ════════ Rapid-fire ════════
        all.addEntry(eb.startTextDescription(header("screenshotassistant.section.rapidfire")).build());

        all.addEntry(eb.startTextDescription(
                        Lang.t("screenshotassistant.section.rapidfire.hint"))
                .build());

        all.addEntry(eb.startBooleanToggle(
                        Lang.t("screenshotassistant.option.enableRapidFire"),
                        config.enableRapidFire)
                .setDefaultValue(true)
                .setTooltip(Lang.t("screenshotassistant.option.enableRapidFire.tooltip"))
                .setSaveConsumer(v -> config.enableRapidFire = v)
                .build());

        // Slider stores step index 1–40 (each step = 50 ms → range 50–2000 ms)
        int rapidFireStepIndex = Math.max(1, Math.min(40, config.rapidFireIntervalMs / 50));
        all.addEntry(eb.startIntSlider(
                        Lang.t("screenshotassistant.option.rapidFireIntervalMs"),
                        rapidFireStepIndex, 1, 40)
                .setDefaultValue(4)   // 4 × 50 = 200 ms
                .setTooltip(Lang.t("screenshotassistant.option.rapidFireIntervalMs.tooltip"))
                .setTextGetter(v -> Text.literal((v * 50) + " ms"))
                .setSaveConsumer(v -> config.rapidFireIntervalMs = v * 50)
                .build());

        // ════════ Storage & Naming ════════
        all.addEntry(eb.startTextDescription(header("screenshotassistant.section.storage")).build());

        all.addEntry(eb.startTextDescription(
                        Lang.t("screenshotassistant.section.storage.hint"))
                .build());

        all.addEntry(eb.startStrField(
                        Lang.t("screenshotassistant.option.baseSavePath"),
                        config.baseSavePath == null ? "" : config.baseSavePath)
                .setDefaultValue("")
                .setTooltip(Lang.t("screenshotassistant.option.baseSavePath.tooltip"))
                .setSaveConsumer(v -> config.baseSavePath = v.trim())
                .build());

        // ════════ Weekly Rotation ════════
        all.addEntry(eb.startTextDescription(header("screenshotassistant.section.weekly")).build());

        all.addEntry(eb.startBooleanToggle(
                        Lang.t("screenshotassistant.option.weeklyRotationEnabled"),
                        config.weeklyRotationEnabled)
                .setDefaultValue(false)
                .setTooltip(Lang.t("screenshotassistant.option.weeklyRotationEnabled.tooltip"))
                .setSaveConsumer(v -> config.weeklyRotationEnabled = v)
                .build());

        all.addEntry(eb.startEnumSelector(
                        Lang.t("screenshotassistant.option.weekNamingMode"),
                        WeekNamingMode.class, config.weekNamingMode)
                .setDefaultValue(WeekNamingMode.DATE_RANGE)
                .setTooltip(Lang.t("screenshotassistant.option.weekNamingMode.tooltip"))
                .setSaveConsumer(v -> config.weekNamingMode = v)
                .build());

        all.addEntry(eb.startEnumSelector(
                        Lang.t("screenshotassistant.option.calendarType"),
                        CalendarType.class, config.calendarType)
                .setDefaultValue(CalendarType.GREGORIAN)
                .setTooltip(Lang.t("screenshotassistant.option.calendarType.tooltip"))
                .setSaveConsumer(v -> config.calendarType = v)
                .build());

        all.addEntry(eb.startIntSlider(
                        Lang.t("screenshotassistant.option.weekStartDay"),
                        config.weekStartDayOfWeek, 1, 7)
                .setDefaultValue(4)
                .setTooltip(Lang.t("screenshotassistant.option.weekStartDay.tooltip"))
                .setTextGetter(v -> Lang.t("screenshotassistant.option.weekStartDay.value." + v))
                .setSaveConsumer(v -> config.weekStartDayOfWeek = v)
                .build());

        all.addEntry(eb.startIntSlider(
                        Lang.t("screenshotassistant.option.weekStartHour"),
                        config.weekStartHour, 0, 23)
                .setDefaultValue(0)
                .setTooltip(Lang.t("screenshotassistant.option.weekStartHour.tooltip"))
                .setTextGetter(v -> Text.literal(String.format("%02d:00", v)))
                .setSaveConsumer(v -> config.weekStartHour = v)
                .build());

        // ════════ Visual Effects ════════
        all.addEntry(eb.startTextDescription(header("screenshotassistant.section.effects")).build());

        all.addEntry(eb.startTextDescription(
                        Lang.t("screenshotassistant.section.effects.hint"))
                .build());

        // ── Sound ──
        all.addEntry(eb.startBooleanToggle(
                        Lang.t("screenshotassistant.option.enableCaptureSound"),
                        config.enableCaptureSound)
                .setDefaultValue(false)
                .setTooltip(Lang.t("screenshotassistant.option.enableCaptureSound.tooltip"))
                .setSaveConsumer(v -> config.enableCaptureSound = v)
                .build());

        all.addEntry(eb.startEnumSelector(
                        Lang.t("screenshotassistant.option.captureSound"),
                        CaptureSound.class, config.captureSound)
                .setDefaultValue(CaptureSound.CAMERA)
                .setTooltip(Lang.t("screenshotassistant.option.captureSound.tooltip"))
                .setSaveConsumer(v -> config.captureSound = v)
                .build());

        all.addEntry(eb.startIntSlider(
                        Lang.t("screenshotassistant.option.captureSoundVolume"),
                        Math.max(0, Math.min(100, config.captureSoundVolume)), 0, 100)
                .setDefaultValue(100)
                .setTooltip(Lang.t("screenshotassistant.option.captureSoundVolume.tooltip"))
                .setTextGetter(v -> Text.literal(v + "%"))
                .setSaveConsumer(v -> config.captureSoundVolume = v)
                .build());

        // ── HUD message ──
        all.addEntry(eb.startBooleanToggle(
                        Lang.t("screenshotassistant.option.enableRainbowMessage"),
                        config.enableRainbowMessage)
                .setDefaultValue(false)
                .setTooltip(Lang.t("screenshotassistant.option.enableRainbowMessage.tooltip"))
                .setSaveConsumer(v -> config.enableRainbowMessage = v)
                .build());

        all.addEntry(eb.startEnumSelector(
                        Lang.t("screenshotassistant.option.messageColorMode"),
                        MessageColorMode.class, config.messageColorMode)
                .setDefaultValue(MessageColorMode.RAINBOW)
                .setTooltip(Lang.t("screenshotassistant.option.messageColorMode.tooltip"))
                .setSaveConsumer(v -> config.messageColorMode = v)
                .build());

        // ── Cinematic subtitle ──
        all.addEntry(eb.startBooleanToggle(
                        Lang.t("screenshotassistant.option.enableCinematicSubtitle"),
                        config.enableCinematicSubtitle)
                .setDefaultValue(false)
                .setTooltip(Lang.t("screenshotassistant.option.enableCinematicSubtitle.tooltip"))
                .setSaveConsumer(v -> config.enableCinematicSubtitle = v)
                .build());

        // ── Shared subtitle / notification settings ──
        all.addEntry(eb.startStrField(
                        Lang.t("screenshotassistant.option.subtitleText"),
                        config.subtitleText == null ? "" : config.subtitleText)
                .setDefaultValue("")
                .setTooltip(Lang.t("screenshotassistant.option.subtitleText.tooltip"))
                .setSaveConsumer(v -> config.subtitleText = v.trim())
                .build());

        all.addEntry(eb.startEnumSelector(
                        Lang.t("screenshotassistant.option.subtitleColorMode"),
                        MessageColorMode.class,
                        config.subtitleColorMode != null ? config.subtitleColorMode : MessageColorMode.GOLD)
                .setDefaultValue(MessageColorMode.GOLD)
                .setTooltip(Lang.t("screenshotassistant.option.subtitleColorMode.tooltip"))
                .setSaveConsumer(v -> config.subtitleColorMode = v)
                .build());

        all.addEntry(eb.startBooleanToggle(
                        Lang.t("screenshotassistant.option.showScreenshotNumberInNotification"),
                        config.showScreenshotNumberInNotification)
                .setDefaultValue(false)
                .setTooltip(Lang.t("screenshotassistant.option.showScreenshotNumberInNotification.tooltip"))
                .setSaveConsumer(v -> config.showScreenshotNumberInNotification = v)
                .build());

        // ════════ Stacked Notifications ════════
        all.addEntry(eb.startTextDescription(header("screenshotassistant.section.stacked")).build());

        all.addEntry(eb.startTextDescription(
                        Lang.t("screenshotassistant.section.stacked.hint"))
                .build());

        all.addEntry(eb.startBooleanToggle(
                        Lang.t("screenshotassistant.option.enableStackedNotifications"),
                        config.enableStackedNotifications)
                .setDefaultValue(false)
                .setTooltip(Lang.t("screenshotassistant.option.enableStackedNotifications.tooltip"))
                .setSaveConsumer(v -> config.enableStackedNotifications = v)
                .build());

        all.addEntry(eb.startIntSlider(
                        Lang.t("screenshotassistant.option.maxStackedNotifications"),
                        Math.max(1, Math.min(10, config.maxStackedNotifications)), 1, 10)
                .setDefaultValue(3)
                .setTooltip(Lang.t("screenshotassistant.option.maxStackedNotifications.tooltip"))
                .setSaveConsumer(v -> config.maxStackedNotifications = v)
                .build());

        all.addEntry(eb.startEnumSelector(
                        Lang.t("screenshotassistant.option.notificationPosition"),
                        NotificationPosition.class,
                        config.notificationPosition != null
                                ? config.notificationPosition
                                : NotificationPosition.BOTTOM_CENTER)
                .setDefaultValue(NotificationPosition.BOTTOM_CENTER)
                .setTooltip(Lang.t("screenshotassistant.option.notificationPosition.tooltip"))
                .setSaveConsumer(v -> config.notificationPosition = v)
                .build());

        // ════════ Import / Export Config ════════
        all.addEntry(eb.startTextDescription(header("screenshotassistant.section.importexport")).build());

        Path cfgDir = FabricLoader.getInstance().getConfigDir();
        String cfgPathStr = cfgDir.resolve("screenshotassistant.json").toAbsolutePath().toString();
        all.addEntry(eb.startTextDescription(
                        Lang.t("screenshotassistant.option.configFolderLabel"))
                .build());
        all.addEntry(eb.startTextDescription(
                        Text.literal("  \u2192 " + cfgPathStr))
                .build());

        all.addEntry(eb.startTextDescription(
                        Lang.t("screenshotassistant.option.importexport.hint"))
                .build());

        all.addEntry(eb.startBooleanToggle(
                        Lang.t("screenshotassistant.option.openConfigFolder"),
                        false)
                .setDefaultValue(false)
                .setTooltip(Lang.t("screenshotassistant.option.openConfigFolder.tooltip"))
                .setSaveConsumer(v -> {
                    if (v) {
                        try {
                            Util.getOperatingSystem().open(cfgDir.toFile());
                        } catch (Exception e) {
                            System.err.println("[Screenshot Assistant] Could not open config folder: " + e);
                        }
                    }
                })
                .build());

        return builder.build();
    }

    private static Text header(String key) {
        return Text.literal("─── " + Lang.str(key) + " ───");
    }
}
