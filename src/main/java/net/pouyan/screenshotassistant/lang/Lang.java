package net.pouyan.screenshotassistant.lang;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.text.Text;
import net.pouyan.screenshotassistant.config.ConfigManager;
import net.pouyan.screenshotassistant.config.ModLanguage;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight translation helper that reads the mod's own lang JSON files
 * from classpath resources, bypassing Minecraft's current game language.
 *
 * Usage:
 *   Lang.t("screenshotassistant.title")                      → Text
 *   Lang.t("screenshotassistant.ruleedit.position.index", 2) → Text
 *   Lang.str("key")                                          → raw String
 *
 * Call {@link #invalidate()} after changing {@link ModLanguage} in config.
 */
public final class Lang {

    private static final Gson GSON = new Gson();
    private static Map<String, String> cache = new HashMap<>();
    private static ModLanguage lastLoaded = null;

    private Lang() {}

    public static Text t(String key) {
        ensureLoaded();
        return Text.literal(cache.getOrDefault(key, key));
    }

    public static Text t(String key, Object... args) {
        ensureLoaded();
        String template = cache.getOrDefault(key, key);
        try {
            return Text.literal(String.format(template, args));
        } catch (Exception e) {
            return Text.literal(template);
        }
    }

    public static String str(String key) {
        ensureLoaded();
        return cache.getOrDefault(key, key);
    }

    public static void invalidate() {
        lastLoaded = null;
    }

    private static void ensureLoaded() {
        ModLanguage current = ConfigManager.get().modLanguage;
        if (current == null) current = ModLanguage.ENGLISH;
        if (current == lastLoaded && !cache.isEmpty()) return;

        String file = (current == ModLanguage.PERSIAN) ? "fa_ir.json" : "en_us.json";
        try {
            InputStream is = Lang.class.getResourceAsStream(
                    "/assets/screenshotassistant/lang/" + file);
            if (is != null) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> loaded = GSON.fromJson(
                        new InputStreamReader(is, StandardCharsets.UTF_8), type);
                if (loaded != null) {
                    cache      = loaded;
                    lastLoaded = current;
                    return;
                }
            }
        } catch (Exception ignored) {}

        lastLoaded = current; // avoid infinite retry
    }
}
