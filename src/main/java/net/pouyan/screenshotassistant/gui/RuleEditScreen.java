package net.pouyan.screenshotassistant.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.pouyan.screenshotassistant.config.KeywordEntry;
import net.pouyan.screenshotassistant.config.RuleConfig;
import net.pouyan.screenshotassistant.config.WordPosition;
import net.pouyan.screenshotassistant.lang.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Form screen for creating or editing a single rule.
 *
 * Each keyword has its OWN position setting:
 *   [  keyword text  ] [ ANYWHERE ▶ ] [✕]
 *   [  Cuboid        ] [ LAST ▶     ] [✕]
 *
 * When position == INDEX a small index field appears inline:
 *   [  word          ] [ INDEX #2 ▶ ] [2] [✕]
 *
 * v0.5.0: Added "Rule folder name" field — screenshots triggered by this rule
 *         are saved into a subfolder with this name inside the base save path.
 *         Leave empty to save directly in the base folder.
 *
 * NOTE: Screen.title is final in Minecraft 1.21.x – it is set only in the
 * constructor via super(...) and is never reassigned inside init().
 */
public class RuleEditScreen extends Screen {

    // ── layout ───────────────────────────────────────────────────────────────
    private static final int FORM_W   = 260;
    private static final int LINE_H   = 26;
    private static final int TOP_Y    = 38;
    private static final int KW_WORD  = 130;
    private static final int KW_POS   = 90;
    private static final int KW_IDX   = 32;
    private static final int KW_REM   = 22;
    private static final int GAP      = 3;

    // ── state ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final RuleConfig editing;
    private final Consumer<RuleConfig> onSave;

    private String  workName;
    private boolean workMatchAll;
    private String  workTargetPlayer;
    private boolean workEnabled;
    private String  workRuleFolderName;
    private final List<KeywordEntry> workKeywords = new ArrayList<>();

    // ── widgets (rebuilt each init) ───────────────────────────────────────────
    private TextFieldWidget nameField;
    private TextFieldWidget targetPlayerField;
    private TextFieldWidget ruleFolderNameField;
    private ButtonWidget    matchModeButton;
    private ButtonWidget    enabledButton;
    // Tracked to prevent duplication when init() is called directly:
    private ButtonWidget    addKeywordButton;
    private ButtonWidget    saveButton;
    private ButtonWidget    cancelButton;

    private final List<TextFieldWidget> kwWordFields = new ArrayList<>();
    private final List<ButtonWidget>    kwPosButtons = new ArrayList<>();
    private final List<TextFieldWidget> kwIdxFields  = new ArrayList<>();
    private final List<ButtonWidget>    kwRemButtons = new ArrayList<>();

    // ── constructor ───────────────────────────────────────────────────────────
    public RuleEditScreen(Screen parent, RuleConfig editing, Consumer<RuleConfig> onSave) {
        // title is final in Screen — set once here, never reassigned in init()
        super(editing == null
                ? Lang.t("screenshotassistant.ruleedit.title.new")
                : Lang.t("screenshotassistant.ruleedit.title.edit"));
        this.parent  = parent;
        this.editing = editing;
        this.onSave  = onSave;

        if (editing != null) {
            workName           = editing.name;
            workMatchAll       = editing.matchAll;
            workTargetPlayer   = editing.targetPlayer;
            workEnabled        = editing.enabled;
            workRuleFolderName = (editing.ruleFolderName == null) ? "" : editing.ruleFolderName;
            for (KeywordEntry e : editing.keywordEntries) workKeywords.add(e.copy());
        } else {
            workName = ""; workMatchAll = false; workTargetPlayer = "";
            workEnabled = true; workRuleFolderName = "";
        }
    }

    // ── init ──────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        clearDynamic();

        int cx   = this.width / 2;
        int left = cx - FORM_W / 2;
        int row  = 0;

        // Rule name
        nameField = new TextFieldWidget(textRenderer,
                left, TOP_Y + row * LINE_H, FORM_W, 18,
                Lang.t("screenshotassistant.ruleedit.name"));
        nameField.setMaxLength(64);
        nameField.setText(workName);
        nameField.setChangedListener(s -> workName = s);
        addDrawableChild(nameField);
        row++;

        // Rule folder name (new in v0.5.0)
        ruleFolderNameField = new TextFieldWidget(textRenderer,
                left, TOP_Y + row * LINE_H, FORM_W, 18,
                Lang.t("screenshotassistant.ruleedit.ruleFolderName"));
        ruleFolderNameField.setMaxLength(64);
        ruleFolderNameField.setText(workRuleFolderName);
        ruleFolderNameField.setSuggestion(workRuleFolderName.isBlank()
                ? Lang.str("screenshotassistant.ruleedit.ruleFolderName.placeholder") : null);
        ruleFolderNameField.setChangedListener(s -> {
            workRuleFolderName = s;
            ruleFolderNameField.setSuggestion(s.isBlank()
                    ? Lang.str("screenshotassistant.ruleedit.ruleFolderName.placeholder") : null);
        });
        addDrawableChild(ruleFolderNameField);
        row++;

        // Keywords header + Add button
        int kwHeaderY = TOP_Y + row * LINE_H;
        addKeywordButton = ButtonWidget.builder(
                        Lang.t("screenshotassistant.ruleedit.keywords.add"),
                        b -> { workKeywords.add(new KeywordEntry("", WordPosition.ANYWHERE, 0)); init(); })
                .dimensions(left + FORM_W - 70, kwHeaderY - 1, 70, 18)
                .build();
        addDrawableChild(addKeywordButton);
        row++;

        // Per-keyword rows
        for (int i = 0; i < workKeywords.size(); i++) {
            final int idx = i;
            KeywordEntry entry = workKeywords.get(i);
            int rowY = TOP_Y + row * LINE_H;

            // word field
            TextFieldWidget wf = new TextFieldWidget(textRenderer,
                    left, rowY, KW_WORD, 18, Lang.t("screenshotassistant.ruleedit.keyword"));
            wf.setMaxLength(128);
            wf.setText(entry.word);
            wf.setChangedListener(s -> workKeywords.get(idx).word = s);
            kwWordFields.add(wf);
            addDrawableChild(wf);

            // position button
            int posX = left + KW_WORD + GAP;
            ButtonWidget pb = ButtonWidget.builder(posLabel(entry), b -> {
                KeywordEntry e = workKeywords.get(idx);
                e.position = nextPos(e.position);
                b.setMessage(posLabel(e));
                refreshIdxField(idx);
            }).dimensions(posX, rowY, KW_POS, 18).build();
            kwPosButtons.add(pb);
            addDrawableChild(pb);

            // index field
            int idxX = posX + KW_POS + GAP;
            TextFieldWidget idf = new TextFieldWidget(textRenderer,
                    idxX, rowY, KW_IDX, 18, Lang.t("screenshotassistant.ruleedit.index"));
            idf.setMaxLength(3);
            idf.setText(String.valueOf(entry.index));
            idf.setChangedListener(s -> {
                try { workKeywords.get(idx).index = Math.max(0, Integer.parseInt(s.trim())); }
                catch (NumberFormatException ignored) {}
            });
            idf.setEditable(entry.position == WordPosition.INDEX);
            kwIdxFields.add(idf);
            addDrawableChild(idf);

            // remove button
            int remX = idxX + KW_IDX + GAP;
            ButtonWidget rb = ButtonWidget.builder(Text.literal("✕"),
                    b -> { workKeywords.remove(idx); init(); })
                    .dimensions(remX, rowY, KW_REM, 18).build();
            kwRemButtons.add(rb);
            addDrawableChild(rb);

            row++;
        }

        // Fixed controls below keyword list
        int fy = TOP_Y + row * LINE_H + 4;

        matchModeButton = ButtonWidget.builder(matchLabel(),
                b -> { workMatchAll = !workMatchAll; b.setMessage(matchLabel()); })
                .dimensions(left, fy, FORM_W, 20).build();
        addDrawableChild(matchModeButton);
        fy += LINE_H;

        targetPlayerField = new TextFieldWidget(textRenderer,
                left, fy, FORM_W, 18, Lang.t("screenshotassistant.ruleedit.targetPlayer"));
        targetPlayerField.setMaxLength(64);
        targetPlayerField.setText(workTargetPlayer);
        targetPlayerField.setChangedListener(s -> workTargetPlayer = s);
        addDrawableChild(targetPlayerField);
        fy += LINE_H;

        enabledButton = ButtonWidget.builder(enabledLabel(),
                b -> { workEnabled = !workEnabled; b.setMessage(enabledLabel()); })
                .dimensions(left, fy, FORM_W, 20).build();
        addDrawableChild(enabledButton);
        fy += LINE_H + 8;

        // Save / Cancel — tracked to prevent duplication on rebuild
        saveButton = ButtonWidget.builder(
                        Lang.t("screenshotassistant.ruleedit.save"), b -> saveAndClose())
                .dimensions(left, fy, FORM_W / 2 - 4, 20).build();
        cancelButton = ButtonWidget.builder(
                        Lang.t("screenshotassistant.ruleedit.cancel"), b -> cancel())
                .dimensions(cx + 2, fy, FORM_W / 2 - 4, 20).build();
        addDrawableChild(saveButton);
        addDrawableChild(cancelButton);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void clearDynamic() {
        // Keyword-row widgets
        kwWordFields.forEach(this::remove); kwWordFields.clear();
        kwPosButtons.forEach(this::remove); kwPosButtons.clear();
        kwIdxFields.forEach(this::remove);  kwIdxFields.clear();
        kwRemButtons.forEach(this::remove); kwRemButtons.clear();

        // Static widgets — remove if already added (handles direct init() calls)
        if (nameField           != null) { remove(nameField);           nameField           = null; }
        if (ruleFolderNameField != null) { remove(ruleFolderNameField); ruleFolderNameField = null; }
        if (targetPlayerField   != null) { remove(targetPlayerField);   targetPlayerField   = null; }
        if (matchModeButton     != null) { remove(matchModeButton);     matchModeButton     = null; }
        if (enabledButton       != null) { remove(enabledButton);       enabledButton       = null; }
        if (addKeywordButton    != null) { remove(addKeywordButton);    addKeywordButton    = null; }
        if (saveButton          != null) { remove(saveButton);          saveButton          = null; }
        if (cancelButton        != null) { remove(cancelButton);        cancelButton        = null; }
    }

    private void refreshIdxField(int idx) {
        if (idx < kwIdxFields.size())
            kwIdxFields.get(idx).setEditable(workKeywords.get(idx).position == WordPosition.INDEX);
    }

    private Text posLabel(KeywordEntry e) {
        return switch (e.position) {
            case FIRST    -> Lang.t("screenshotassistant.ruleedit.position.first");
            case LAST     -> Lang.t("screenshotassistant.ruleedit.position.last");
            case INDEX    -> Lang.t("screenshotassistant.ruleedit.position.index", e.index);
            case ANYWHERE -> Lang.t("screenshotassistant.ruleedit.position.anywhere");
        };
    }

    private WordPosition nextPos(WordPosition cur) {
        WordPosition[] v = WordPosition.values();
        return v[(cur.ordinal() + 1) % v.length];
    }

    private Text matchLabel() {
        return workMatchAll
                ? Lang.t("screenshotassistant.ruleedit.matchmode.and")
                : Lang.t("screenshotassistant.ruleedit.matchmode.or");
    }

    private Text enabledLabel() {
        return workEnabled
                ? Lang.t("screenshotassistant.ruleedit.enabled.on")
                : Lang.t("screenshotassistant.ruleedit.enabled.off");
    }

    private void saveAndClose() {
        RuleConfig r = (editing != null) ? editing.copy() : new RuleConfig();
        r.name           = workName.isBlank() ? "Rule" : workName.trim();
        r.matchAll       = workMatchAll;
        r.targetPlayer   = workTargetPlayer.trim();
        r.enabled        = workEnabled;
        r.ruleFolderName = workRuleFolderName.trim();
        r.keywordEntries.clear();
        for (KeywordEntry e : workKeywords)
            if (e.word != null && !e.word.isBlank()) r.keywordEntries.add(e.copy());
        onSave.accept(r);
        assert client != null;
        client.setScreen(parent);
    }

    private void cancel() { assert client != null; client.setScreen(parent); }

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);

        int left = this.width / 2 - FORM_W / 2;

        drawLabel(ctx, "screenshotassistant.ruleedit.name",           left - 4, TOP_Y);
        drawLabel(ctx, "screenshotassistant.ruleedit.ruleFolderName", left - 4, TOP_Y + LINE_H);

        // Keywords section label
        int kwHeaderY = TOP_Y + 2 * LINE_H;
        ctx.drawText(textRenderer, Lang.t("screenshotassistant.ruleedit.keywords.section"),
                left, kwHeaderY + 4, 0xFFFFAA00, false);

        // Labels for fixed controls
        int fy = TOP_Y + (workKeywords.size() + 3) * LINE_H + 4;
        drawLabel(ctx, "screenshotassistant.ruleedit.matchmode",    left - 4, fy); fy += LINE_H;
        drawLabel(ctx, "screenshotassistant.ruleedit.targetPlayer", left - 4, fy);
    }

    private void drawLabel(DrawContext ctx, String key, int x, int y) {
        Text label = Lang.t(key);
        ctx.drawText(textRenderer, label, x - textRenderer.getWidth(label) - 4, y + 5, 0xCCCCCC, false);
    }

    @Override public void close() { cancel(); }
}
