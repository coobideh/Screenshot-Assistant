package net.pouyan.screenshotassistant.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.pouyan.screenshotassistant.config.ConfigManager;
import net.pouyan.screenshotassistant.config.ModConfig;
import net.pouyan.screenshotassistant.lang.Lang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Screenshot Browser — shows all sub-folders inside the configured base save
 * path, along with per-folder stats (file count, creation date, last screenshot).
 *
 * Open via:
 *   • Keybind  F7  (configurable in Controls → Screenshot Assistant)
 *   • Button   📁 Screenshots  in the Rule Manager screen
 *
 * Clicking any row opens that folder in the OS file manager.
 *
 * v0.6.0: initial implementation.
 */
public class ScreenshotBrowserScreen extends Screen {

    // ── layout ────────────────────────────────────────────────────────────────
    private static final int ROW_H    = 28;
    private static final int HDR_H    = 16;   // column-header row height
    private static final int LIST_TOP = 44;   // top of the scrollable area
    private static final int BTN_BAR  = 36;

    // Column positions (relative to left edge of the row area, set in init)
    private static final int COL_COUNT   = 160; // offset from left for "Count"
    private static final int COL_CREATED = 240; // offset for "Created"
    private static final int COL_LAST    = 400; // offset for "Last Screenshot"

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm");

    // ── state ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final List<FolderInfo> folders = new ArrayList<>();
    private int scrollOffset = 0;
    private int rowW;   // width of the list area (clamped to screen)
    private int left;   // left edge of the list area

    // ── widgets ───────────────────────────────────────────────────────────────
    private ButtonWidget doneButton;

    // ─────────────────────────────────────────────────────────────────────────

    public ScreenshotBrowserScreen(Screen parent) {
        super(Lang.t("screenshotassistant.browser.title"));
        this.parent = parent;
    }

    // ── init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        rowW = Math.min(620, this.width - 32);
        left = this.width / 2 - rowW / 2;

        folders.clear();
        scanFolders();

        if (doneButton != null) remove(doneButton);
        int barY = this.height - BTN_BAR + 6;
        doneButton = ButtonWidget.builder(
                        Lang.t("screenshotassistant.browser.done"),
                        b -> close())
                .dimensions(this.width / 2 - 50, barY, 100, 20).build();
        addDrawableChild(doneButton);
    }

    // ── folder scanning ───────────────────────────────────────────────────────

    private void scanFolders() {
        Path base = resolveBase();
        if (!Files.isDirectory(base)) return;

        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                FolderInfo info = buildInfo(dir);
                if (info != null) folders.add(info);
            });
        } catch (IOException ignored) {}

        folders.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
    }

    private Path resolveBase() {
        ModConfig config = ConfigManager.get();
        String bsp = (config.baseSavePath == null) ? "" : config.baseSavePath.trim();
        return bsp.isEmpty()
                ? FabricLoader.getInstance().getGameDir().resolve("screenshots")
                : Paths.get(bsp);
    }

    private FolderInfo buildInfo(Path dir) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
            String created = toDisplay(attrs.creationTime().toInstant());

            List<Path> pngs = new ArrayList<>();
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                        .forEach(pngs::add);
            } catch (IOException ignored) {}

            String lastShot = "—";
            if (!pngs.isEmpty()) {
                long latestMs = 0L;
                for (Path p : pngs) {
                    try {
                        long t = Files.getLastModifiedTime(p).toMillis();
                        if (t > latestMs) latestMs = t;
                    } catch (IOException ignored) {}
                }
                if (latestMs > 0) {
                    lastShot = toDisplay(Instant.ofEpochMilli(latestMs));
                }
            }

            return new FolderInfo(
                    dir.getFileName().toString(), dir,
                    pngs.size(), created, lastShot);
        } catch (IOException e) {
            return null;
        }
    }

    private static String toDisplay(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(FMT);
    }

    // ── input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double horizAmount, double vertAmount) {
        int contentH  = folders.size() * ROW_H;
        int visibleH  = Math.max(0, listBottom() - LIST_TOP);
        int maxScroll = Math.max(0, contentH - visibleH);
        scrollOffset  = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(vertAmount * ROW_H)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < folders.size(); i++) {
                int rowY = rowY(i);
                if (rowY + ROW_H - 2 < LIST_TOP || rowY > listBottom()) continue;
                if (mx >= left && mx <= left + rowW && my >= rowY && my <= rowY + ROW_H - 2) {
                    openInExplorer(folders.get(i).path);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void openInExplorer(Path path) {
        try {
            Files.createDirectories(path);
            Util.getOperatingSystem().open(path.toFile());
        } catch (Exception e) {
            System.err.println("[Screenshot Assistant] Cannot open folder: " + e);
        }
    }

    // ── rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        super.render(ctx, mx, my, delta);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);

        // Hint line
        ctx.drawCenteredTextWithShadow(textRenderer,
                Lang.t("screenshotassistant.browser.hint"),
                this.width / 2, 26, 0x888888);

        // Column headers
        int hdrY = LIST_TOP - HDR_H;
        ctx.drawText(textRenderer, Lang.t("screenshotassistant.browser.col.name"),
                left + 4, hdrY, 0x888888, false);
        ctx.drawText(textRenderer, Lang.t("screenshotassistant.browser.col.count"),
                left + COL_COUNT, hdrY, 0x888888, false);
        ctx.drawText(textRenderer, Lang.t("screenshotassistant.browser.col.created"),
                left + COL_CREATED, hdrY, 0x888888, false);
        ctx.drawText(textRenderer, Lang.t("screenshotassistant.browser.col.last"),
                left + COL_LAST, hdrY, 0x888888, false);

        // Divider under headers
        ctx.fill(left, LIST_TOP - 2, left + rowW, LIST_TOP - 1, 0x44FFFFFF);

        if (folders.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Lang.t("screenshotassistant.browser.empty"),
                    this.width / 2, LIST_TOP + 20, 0xAAAAAA);
            return;
        }

        for (int i = 0; i < folders.size(); i++) {
            int rowY = rowY(i);
            // Skip rows entirely outside the visible window
            if (rowY + ROW_H < LIST_TOP || rowY > listBottom()) continue;

            FolderInfo f = folders.get(i);
            boolean hovered = mx >= left && mx <= left + rowW
                    && my >= rowY && my <= rowY + ROW_H - 2;

            // Row background
            int bg = hovered ? 0x44FFAA00
                    : (i % 2 == 0 ? 0x18FFFFFF : 0x00000000);
            ctx.fill(left, rowY, left + rowW, rowY + ROW_H - 2, bg);

            int textY     = rowY + (ROW_H - 8) / 2;
            int nameColor = hovered ? 0xFFAA00 : 0xFFFFFF;

            // Clip folder name to fit the first column
            String nameStr = f.name;
            int maxNameW   = COL_COUNT - 10;
            while (nameStr.length() > 2
                    && textRenderer.getWidth(nameStr + "…") > maxNameW) {
                nameStr = nameStr.substring(0, nameStr.length() - 1);
            }
            if (!nameStr.equals(f.name)) nameStr = nameStr + "…";

            ctx.drawText(textRenderer, Text.literal(nameStr),
                    left + 4, textY, nameColor, hovered);
            ctx.drawText(textRenderer,
                    Text.literal(f.count + " \u25b8"),        // count + ▸
                    left + COL_COUNT, textY, 0xDDDDDD, false);
            ctx.drawText(textRenderer,
                    Text.literal(f.created),
                    left + COL_CREATED, textY, 0xBBBBBB, false);
            ctx.drawText(textRenderer,
                    Text.literal(f.lastShot),
                    left + COL_LAST, textY, 0xBBBBBB, false);
        }

        // Bottom divider above button bar
        ctx.fill(left, listBottom(), left + rowW, listBottom() + 1, 0x44FFFFFF);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int listBottom() { return this.height - BTN_BAR; }
    private int rowY(int i)  { return LIST_TOP + i * ROW_H - scrollOffset; }

    @Override
    public void close() {
        assert client != null;
        client.setScreen(parent);
    }

    // ── data ──────────────────────────────────────────────────────────────────

    private static final class FolderInfo {
        final String name;
        final Path   path;
        final int    count;
        final String created;
        final String lastShot;

        FolderInfo(String name, Path path, int count, String created, String lastShot) {
            this.name     = name;
            this.path     = path;
            this.count    = count;
            this.created  = created;
            this.lastShot = lastShot;
        }
    }
}
