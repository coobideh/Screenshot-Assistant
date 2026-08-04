package net.pouyan.screenshotassistant.logic;

import net.pouyan.screenshotassistant.config.KeywordEntry;
import net.pouyan.screenshotassistant.config.RuleConfig;
import net.pouyan.screenshotassistant.config.WordPosition;

import java.util.List;
import java.util.Locale;

/**
 * Normalizes a raw chat line and checks it against a {@link RuleConfig}.
 *
 * Matching rules:
 *  - Case-insensitive always.
 *  - TWO matching strategies are used (both are tried for every keyword entry):
 *
 *    Strategy A – NORMALIZED (existing behaviour):
 *      Minecraft §-colour codes and punctuation (except underscore) are stripped
 *      before comparison, so "[BBC]", "BBC!", "bbc" all match "bbc".
 *      Underscore is preserved — "cuboid_og" stays as one token.
 *      Single-word keyword → exact word match within message tokens.
 *      Multi-word keyword  → phrase substring match on the normalised message.
 *
 *    Strategy B – LITERAL (new):
 *      Only colour codes and invisible Unicode are stripped; ALL punctuation is
 *      kept.  The keyword (lowercased) is searched as a case-insensitive literal
 *      substring of the colour-stripped message.  This makes keywords that
 *      contain special characters (`:`, `-`, `...`, `!!`, `_map_`, etc.) work
 *      exactly as the user typed them.
 *      Strategy B is activated when the keyword, after stripping colour codes
 *      but BEFORE stripping punctuation, differs from its fully-normalised form
 *      (i.e. the keyword actually contains significant special characters).
 *
 *  A keyword entry matches if EITHER strategy succeeds.
 *
 *  Fix for "empty keyword after normalization" bug:
 *    A keyword like "---" or "..." previously normalised to "" and was silently
 *    skipped (the rule never fired).  Now Strategy B catches it.
 *
 * OR  (matchAll=false): screenshot if ANY keyword entry matches.
 * AND (matchAll=true) : screenshot only if ALL keyword entries match.
 *                       Returns false (not vacuous-true) when no valid entries
 *                       exist so an "all blank" rule never fires.
 */
public final class ChatMatcher {

    private ChatMatcher() {}

    public static boolean matches(RuleConfig rule, String rawMessage, String sender) {
        if (!rule.enabled) return false;
        if (rule.keywordEntries == null || rule.keywordEntries.isEmpty()) return false;

        // Player filter — only applied when we actually know the sender.
        if (rule.targetPlayer != null && !rule.targetPlayer.isBlank()) {
            if (sender != null && !sender.isBlank()) {
                if (!sender.trim().equalsIgnoreCase(rule.targetPlayer.trim())) return false;
            }
            // For GAME messages (sender == null), also check if the targetPlayer
            // name appears inside the message text so the filter is not silently
            // skipped for system/server messages.
            else if (sender == null || sender.isBlank()) {
                String normTarget = normalizeStr(rule.targetPlayer);
                if (!normTarget.isEmpty() && !normalizeStr(rawMessage).contains(normTarget)) return false;
            }
        }

        // Body-only data (message text alone) — this is what FIRST / LAST /
        // INDEX position checks must use, so word positions always refer to
        // the actual chat message and are never shifted by anything else.
        String bodyNormMsg = normalizeStr(rawMessage);
        String[] bodyWords = bodyNormMsg.isEmpty() ? new String[0] : bodyNormMsg.split("\\s+");
        String bodyLiteMsg = stripColorOnly(rawMessage);

        // Sender-prefixed data — used ONLY for ANYWHERE matching, so a keyword
        // can still match the sender's name (e.g. "CuboiD_OG") even when it's
        // not inside the message body itself. Prepending it must never affect
        // FIRST/LAST/INDEX, which is why it's kept separate from the body data.
        String searchText = (sender != null && !sender.isBlank())
                ? sender.trim() + " " + rawMessage
                : rawMessage;
        String fullNormMsg = normalizeStr(searchText);
        String[] fullWords = fullNormMsg.isEmpty() ? new String[0] : fullNormMsg.split("\\s+");
        String fullLiteMsg = stripColorOnly(searchText);

        // Need at least one token or non-empty literal message
        if (bodyWords.length == 0 && bodyLiteMsg.isBlank() && fullLiteMsg.isBlank()) return false;

        MatchContext ctx = new MatchContext(bodyWords, bodyNormMsg, bodyLiteMsg,
                                             fullWords, fullNormMsg, fullLiteMsg);

        return rule.matchAll
                ? matchAll(rule.keywordEntries, ctx)
                : matchAny(rule.keywordEntries, ctx);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Bundles the two parallel views of the incoming line (body-only vs sender+body). */
    private record MatchContext(String[] bodyWords, String bodyNormMsg, String bodyLiteMsg,
                                 String[] fullWords, String fullNormMsg, String fullLiteMsg) {}

    private static boolean matchAny(List<KeywordEntry> entries, MatchContext ctx) {
        for (KeywordEntry e : entries) {
            if (entryMatches(e, ctx)) return true;
        }
        return false;
    }

    private static boolean matchAll(List<KeywordEntry> entries, MatchContext ctx) {
        boolean anyValid = false;
        for (KeywordEntry e : entries) {
            if (e == null || e.word == null || e.word.isBlank()) continue;
            anyValid = true;
            if (!entryMatches(e, ctx)) return false;
        }
        // If no valid (non-blank) entries exist, do NOT fire vacuously.
        return anyValid;
    }

    /**
     * Returns true if the keyword entry matches via EITHER strategy.
     * FIRST / LAST / INDEX are checked against the message body only;
     * ANYWHERE is checked against the sender-prefixed text as well.
     */
    private static boolean entryMatches(KeywordEntry e, MatchContext ctx) {
        if (e == null || e.word == null || e.word.isBlank()) return false;

        WordPosition pos   = e.position != null ? e.position : WordPosition.ANYWHERE;
        int          index = e.index;
        boolean      usesSender = pos == WordPosition.ANYWHERE;

        String[] words   = usesSender ? ctx.fullWords()   : ctx.bodyWords();
        String   normMsg = usesSender ? ctx.fullNormMsg() : ctx.bodyNormMsg();
        String   liteMsg = usesSender ? ctx.fullLiteMsg() : ctx.bodyLiteMsg();

        // ── Strategy A: full normalisation ───────────────────────────────────
        String kwNorm = normalizeStr(e.word);
        if (!kwNorm.isEmpty()) {
            if (keywordFoundAt(kwNorm, words, normMsg, pos, index)) return true;
        }

        // ── Strategy B: literal colour-stripped match ─────────────────────────
        // Only try when the keyword actually has punctuation that would be lost
        // by full normalisation (or when full-norm produced an empty string).
        String kwLite = stripColorOnly(e.word);
        if (!kwLite.isEmpty() && !kwLite.equals(kwNorm)) {
            if (keywordFoundLiteral(kwLite, liteMsg, pos)) return true;
        }

        return false;
    }

    /**
     * Strategy A — normalised match.
     *
     * @param kw        Normalised keyword (may contain spaces for multi-word phrases).
     * @param words     Tokenised normalised message.
     * @param normMsg   Full normalised message string (for phrase/substring search).
     */
    private static boolean keywordFoundAt(String kw, String[] words, String normMsg,
                                          WordPosition position, int index) {
        boolean isPhrase = kw.contains(" ");

        switch (position) {
            case FIRST:
                if (isPhrase) return normMsg.startsWith(kw);
                return words.length > 0 && words[0].equals(kw);

            case LAST:
                if (isPhrase) return normMsg.endsWith(kw);
                return words.length > 0 && words[words.length - 1].equals(kw);

            case INDEX:
                if (isPhrase) return false;
                return index >= 0 && index < words.length && words[index].equals(kw);

            default: // ANYWHERE
                if (isPhrase) {
                    return (" " + normMsg + " ").contains(" " + kw + " ");
                }
                for (String w : words) if (w.equals(kw)) return true;
                return false;
        }
    }

    /**
     * Strategy B — literal colour-stripped match.
     *
     * The keyword is treated as a literal string (punctuation preserved,
     * lowercase) and searched inside the colour-stripped message.
     *
     * Position semantics:
     *   FIRST   — message starts with keyword (ignoring leading whitespace)
     *   LAST    — message ends with keyword   (ignoring trailing whitespace)
     *   INDEX   — not applicable; always returns false
     *   ANYWHERE— keyword appears anywhere as a substring
     */
    private static boolean keywordFoundLiteral(String kw, String liteMsg,
                                               WordPosition position) {
        if (kw.isEmpty() || liteMsg.isEmpty()) return false;
        switch (position) {
            case FIRST:
                return liteMsg.startsWith(kw);
            case LAST:
                return liteMsg.endsWith(kw);
            case INDEX:
                return false; // INDEX is token-based; doesn't apply to literal mode
            default: // ANYWHERE
                return liteMsg.contains(kw);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Normalisation helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * STRATEGY A normalisation:
     * Strip colour codes, remove punctuation, NFKD, lowercase, collapse spaces.
     */
    private static String normalizeStr(String s) {
        return stripFormatting(s).toLowerCase(Locale.ROOT).trim().replaceAll("\\s{2,}", " ");
    }

    /**
     * STRATEGY B normalisation:
     * Strip only §/&-colour codes and invisible Unicode characters.
     * Apply NFKD normalisation (fullwidth → ASCII, etc.) and remove combining marks.
     * Punctuation is KEPT so the user's special characters survive.
     * Result is lowercased and trimmed.
     */
    private static String stripColorOnly(String s) {
        // 1. §X and &X Minecraft format codes
        String r = s.replaceAll("[§&][0-9a-fA-Fk-orK-OR]", "");
        r = r.replace("\u00a7", "");

        // 2. Zero-width / invisible characters
        r = r.replaceAll("[\u200B-\u200F\u00AD\uFEFF\u2060\u2062-\u2064]", "");

        // 3+4. NFKD + combining marks (converts ＢＢＣ→BBC, styled chars, etc.)
        r = java.text.Normalizer.normalize(r, java.text.Normalizer.Form.NFKD);
        r = r.replaceAll("\\p{M}", "");

        // Lowercase, trim, collapse spaces — but DO NOT strip punctuation
        return r.toLowerCase(Locale.ROOT).trim().replaceAll("\\s{2,}", " ");
    }

    /**
     * Full formatting strip used by Strategy A:
     *
     *  1. §X / &X  — Minecraft legacy colour/format codes.
     *  2. Zero-width & invisible Unicode characters.
     *  3. NFKD Unicode normalisation → strips diacritics and converts fullwidth
     *     or styled Unicode letters (ＢＢＣ, 𝐁𝐁𝐂, etc.) to their ASCII base.
     *  4. Combining marks removed after NFKD.
     *  5. Punctuation → space (Persian included; underscore preserved for names).
     */
    private static String stripFormatting(String s) {
        // 1. §X and &X Minecraft format codes
        String r = s.replaceAll("[§&][0-9a-fA-Fk-orK-OR]", "");
        r = r.replace("\u00a7", "");

        // 2. Zero-width / invisible characters
        r = r.replaceAll("[\u200B-\u200F\u00AD\uFEFF\u2060\u2062-\u2064]", "");

        // 3+4. NFKD normalisation + remove combining marks
        r = java.text.Normalizer.normalize(r, java.text.Normalizer.Form.NFKD);
        r = r.replaceAll("\\p{M}", "");

        // 5. Punctuation → space (underscore excluded — used in player names)
        return r.replaceAll("[\\p{Punct}&&[^_]\u060C\u061B\u061F\u066A-\u066D\u06D4]", " ");
    }
}
