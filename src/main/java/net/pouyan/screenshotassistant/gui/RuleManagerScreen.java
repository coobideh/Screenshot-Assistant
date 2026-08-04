package net.pouyan.screenshotassistant.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.pouyan.screenshotassistant.config.ConfigManager;
import net.pouyan.screenshotassistant.config.ModConfig;
import net.pouyan.screenshotassistant.config.RuleConfig;
import net.pouyan.screenshotassistant.lang.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable list of rules with Add / Browse / Settings / Done.
 *
 * v0.6.0: Added "📁 Screenshots" button that opens the ScreenshotBrowserScreen —
 *         the same panel reachable via the F7 keybind from the game world.
 *         Button bar now has four evenly spaced buttons.
 */
public class RuleManagerScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH  = 300;
    private static final int LIST_TOP   = 36;
    private static final int BUTTON_BAR = 36;

    // Each button is 95 px wide, with 4 px gaps → total span = 4*95 + 3*4 = 392 px
    private static final int BTN_W    = 95;
    private static final int BTN_GAP  = 4;
    private static final int BTN_SPAN = 4 * BTN_W + 3 * BTN_GAP; // 392

    private final Screen parent;
    private final List<RuleConfig> workingRules = new ArrayList<>();
    private int scrollOffset = 0;

    // ── Rule-row widgets (cleared in rebuild) ─────────────────────────────────
    private final List<ButtonWidget> editButtons   = new ArrayList<>();
    private final List<ButtonWidget> removeButtons = new ArrayList<>();
    private final List<ButtonWidget> toggleButtons = new ArrayList<>();

    // ── Fixed bottom-bar widgets (tracked to avoid duplication) ───────────────
    private ButtonWidget addRuleButton;
    private ButtonWidget browseButton;
    private ButtonWidget settingsButton;
    private ButtonWidget doneButton;

    public RuleManagerScreen(Screen parent) {
        super(Lang.t("screenshotassistant.rulemanager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Remove fixed bottom buttons if they already exist (handles both resize
        // events AND direct init() calls from syncAndRebuild).
        removeFixedButtons();

        rebuild();

        int barY    = this.height - BUTTON_BAR + 6;
        int startX  = this.width / 2 - BTN_SPAN / 2;

        addRuleButton = ButtonWidget.builder(
                        Lang.t("screenshotassistant.rulemanager.add"), b -> openEdit(null))
                .dimensions(startX, barY, BTN_W, 20).build();

        browseButton = ButtonWidget.builder(
                        Lang.t("screenshotassistant.rulemanager.browse"),
                        b -> { assert client != null; client.setScreen(new ScreenshotBrowserScreen(this)); })
                .dimensions(startX + BTN_W + BTN_GAP, barY, BTN_W, 20).build();

        settingsButton = ButtonWidget.builder(
                        Lang.t("screenshotassistant.rulemanager.settings"),
                        b -> { assert client != null; client.setScreen(ClothConfigScreenFactory.build(this)); })
                .dimensions(startX + 2 * (BTN_W + BTN_GAP), barY, BTN_W, 20).build();

        doneButton = ButtonWidget.builder(
                        Lang.t("screenshotassistant.rulemanager.done"), b -> done())
                .dimensions(startX + 3 * (BTN_W + BTN_GAP), barY, BTN_W, 20).build();

        addDrawableChild(addRuleButton);
        addDrawableChild(browseButton);
        addDrawableChild(settingsButton);
        addDrawableChild(doneButton);
    }

    /** Removes the four fixed buttons if they were previously registered. */
    private void removeFixedButtons() {
        if (addRuleButton  != null) { remove(addRuleButton);  addRuleButton  = null; }
        if (browseButton   != null) { remove(browseButton);   browseButton   = null; }
        if (settingsButton != null) { remove(settingsButton); settingsButton = null; }
        if (doneButton     != null) { remove(doneButton);     doneButton     = null; }
    }

    private void rebuild() {
        editButtons.forEach(this::remove);
        removeButtons.forEach(this::remove);
        toggleButtons.forEach(this::remove);
        editButtons.clear(); removeButtons.clear(); toggleButtons.clear();

        workingRules.clear();
        workingRules.addAll(ConfigManager.get().rules);

        int centerX  = this.width / 2;
        int rowLeft  = centerX - ROW_WIDTH / 2;
        int listTopY = LIST_TOP - scrollOffset;

        for (int i = 0; i < workingRules.size(); i++) {
            final int idx  = i;
            RuleConfig rule = workingRules.get(i);
            int rowY = listTopY + i * ROW_HEIGHT;

            ButtonWidget toggle = ButtonWidget.builder(
                    rule.enabled ? Lang.t("screenshotassistant.rulemanager.enabled")
                                 : Lang.t("screenshotassistant.rulemanager.disabled"),
                    b -> { workingRules.get(idx).enabled = !workingRules.get(idx).enabled; syncAndRebuild(); })
                    .dimensions(rowLeft, rowY + 2, 38, 18).build();

            ButtonWidget edit = ButtonWidget.builder(
                    Lang.t("screenshotassistant.rulemanager.edit"), b -> openEdit(idx))
                    .dimensions(centerX + ROW_WIDTH / 2 - 82, rowY + 2, 40, 18).build();

            ButtonWidget remove = ButtonWidget.builder(
                    Lang.t("screenshotassistant.rulemanager.remove"),
                    b -> { workingRules.remove(idx); save(); init(); })
                    .dimensions(centerX + ROW_WIDTH / 2 - 40, rowY + 2, 40, 18).build();

            toggleButtons.add(toggle); editButtons.add(edit); removeButtons.add(remove);
            addDrawableChild(toggle); addDrawableChild(edit); addDrawableChild(remove);
        }
    }

    private int listBottom() { return this.height - BUTTON_BAR; }
    private void done() { assert client != null; client.setScreen(parent); }

    private void save() {
        ModConfig config = ConfigManager.get();
        config.rules.clear();
        config.rules.addAll(workingRules);
        ConfigManager.save();
    }

    private void syncAndRebuild() { save(); init(); }

    private void openEdit(Integer index) {
        assert client != null;
        RuleConfig toEdit = (index == null) ? null : workingRules.get(index).copy();
        client.setScreen(new RuleEditScreen(this, toEdit, savedRule -> {
            if (index == null) workingRules.add(savedRule);
            else workingRules.set(index, savedRule);
            save();
        }));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        int contentHeight = workingRules.size() * ROW_HEIGHT;
        int visibleHeight = Math.max(0, listBottom() - LIST_TOP);
        int maxScroll     = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(vertAmount * ROW_HEIGHT)));
        rebuild();
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);

        if (workingRules.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Lang.t("screenshotassistant.rulemanager.empty"),
                    this.width / 2, LIST_TOP + 10, 0xAAAAAA);
        } else {
            int centerX  = this.width / 2;
            int rowLeft  = centerX - ROW_WIDTH / 2;
            int listTopY = LIST_TOP - scrollOffset;
            for (int i = 0; i < workingRules.size(); i++) {
                int rowY = listTopY + i * ROW_HEIGHT;
                if (rowY < LIST_TOP - ROW_HEIGHT || rowY > listBottom()) continue;
                RuleConfig rule = workingRules.get(i);
                context.drawText(textRenderer, Text.literal(rule.name), rowLeft + 44, rowY + 6, 0xFFFFFF, true);
                String badge = rule.matchAll ? "[AND]" : "[OR]";
                int bc = rule.matchAll ? 0xFFAAAA00 : 0xFF00AAFF;
                context.drawText(textRenderer, Text.literal(badge),
                        rowLeft + 44 + textRenderer.getWidth(rule.name) + 6, rowY + 6, bc, false);
            }
        }
    }

    @Override public void close() { done(); }
}
