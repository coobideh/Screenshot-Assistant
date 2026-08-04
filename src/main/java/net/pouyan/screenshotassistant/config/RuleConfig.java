package net.pouyan.screenshotassistant.config;

import java.util.ArrayList;
import java.util.List;

/**
 * A single chat-watching rule.
 *
 * Each keyword now has its OWN position setting (see {@link KeywordEntry}).
 *
 * v0.5.0: Added {@link #ruleFolderName} — when non-empty, screenshots triggered
 * by this rule are saved into a subfolder of that name under the base save path.
 *
 * --- Migration ---
 * Old configs that used the flat {@code keywords} list + single {@code position}
 * are automatically upgraded by {@link ConfigManager} on first load.
 */
public class RuleConfig {

    public String  name    = "New rule";
    public boolean enabled = true;

    /**
     * Per-keyword entries (word + its own position + optional index).
     * This is the canonical field; the legacy fields below are kept only for
     * reading old config files and are migrated on first load.
     */
    public List<KeywordEntry> keywordEntries = new ArrayList<>();

    // ---- Legacy fields (migration only) ------------------------------------
    @Deprecated public List<String> keywords = new ArrayList<>();
    @Deprecated public WordPosition  position = WordPosition.ANYWHERE;
    @Deprecated public int           index    = 0;
    // -------------------------------------------------------------------------

    /**
     * false = OR  – screenshot if ANY keyword matches (default)
     * true  = AND – screenshot only if ALL keywords are found
     */
    public boolean matchAll = false;

    /** Empty/blank means "any player". */
    public String targetPlayer = "";

    /**
     * Optional subfolder name for screenshots triggered by this rule.
     * Empty/blank = save directly in the base save path (no extra subfolder).
     * Non-empty   = screenshots go into {@code <baseSavePath>/<ruleFolderName>/}.
     *
     * Example: ruleFolderName = "PvP" → all screenshots from this rule go to
     * {@code .minecraft/screenshots/PvP/} (or your configured base path).
     */
    public String ruleFolderName = "";

    public RuleConfig() {}

    public RuleConfig copy() {
        RuleConfig c = new RuleConfig();
        c.name           = this.name;
        c.enabled        = this.enabled;
        c.matchAll       = this.matchAll;
        c.targetPlayer   = this.targetPlayer;
        c.ruleFolderName = this.ruleFolderName;
        for (KeywordEntry e : this.keywordEntries) c.keywordEntries.add(e.copy());
        return c;
    }
}
