package net.pouyan.screenshotassistant.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Reads/writes {@link ModConfig} as a single JSON file at
 * ".minecraft/config/screenshotassistant.json".
 *
 * v0.5.0 migration: old folderMode/folderValue are converted to baseSavePath.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "screenshotassistant.json";

    private static ModConfig instance;

    private ConfigManager() {}

    public static synchronized ModConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static ModConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    applyDefaults(loaded);
                    migrateLegacyStorage(loaded);
                    migrateRules(loaded);
                    return loaded;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("[Screenshot Assistant] Could not read config, using defaults: " + e);
            }
        }
        return new ModConfig();
    }

    private static void applyDefaults(ModConfig c) {
        if (c.weekNamingMode == null)  c.weekNamingMode  = WeekNamingMode.DATE_RANGE;
        if (c.calendarType  == null)   c.calendarType    = CalendarType.GREGORIAN;
        if (c.modLanguage   == null)   c.modLanguage     = ModLanguage.ENGLISH;
        if (c.captureDelayMs < 0)      c.captureDelayMs  = 0;
        if (c.weekStartDayOfWeek < 1 || c.weekStartDayOfWeek > 7) c.weekStartDayOfWeek = 4;
        if (c.rules == null)           c.rules           = new ArrayList<>();
        if (c.baseSavePath == null)    c.baseSavePath    = "";
        // fixTooltipPosition defaults to true; Gson leaves it true when absent from JSON
    }

    /**
     * One-time migration from v0.4.x folderMode/folderValue → baseSavePath.
     * After migration, legacy fields are cleared so they don't appear in the
     * next save.
     */
    @SuppressWarnings("deprecation")
    private static void migrateLegacyStorage(ModConfig c) {
        // Only migrate if baseSavePath is still empty (not yet set) and legacy fields exist
        if (c.baseSavePath == null || c.baseSavePath.isEmpty()) {
            if (c.folderMode == FolderMode.ABSOLUTE
                    && c.folderValue != null && !c.folderValue.isBlank()) {
                // Migrate absolute path
                c.baseSavePath = c.folderValue.trim();
            }
            // SUBFOLDER is intentionally dropped — there is no equivalent.
            // DEFAULT → baseSavePath stays empty (same meaning).
        }
        // Clear legacy fields so they won't be written back
        c.folderMode  = null;
        c.folderValue = null;
    }

    @SuppressWarnings("deprecation")
    private static void migrateRules(ModConfig c) {
        for (RuleConfig rule : c.rules) {
            if (rule.keywordEntries == null) rule.keywordEntries = new ArrayList<>();
            if (rule.ruleFolderName == null) rule.ruleFolderName = "";
            if (!rule.keywordEntries.isEmpty()) continue;
            if (rule.keywords != null && !rule.keywords.isEmpty()) {
                WordPosition pos = (rule.position != null) ? rule.position : WordPosition.ANYWHERE;
                for (String word : rule.keywords) {
                    if (word != null && !word.isBlank())
                        rule.keywordEntries.add(new KeywordEntry(word.trim(), pos, rule.index));
                }
                rule.keywords.clear();
            }
        }
    }

    public static synchronized void save() {
        if (instance == null) return;
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            System.err.println("[Screenshot Assistant] Could not save config: " + e);
        }
    }
}
