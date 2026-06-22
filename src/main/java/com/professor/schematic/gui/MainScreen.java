package com.professor.schematic.gui;

import com.professor.schematic.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.util.*;

@Environment(EnvType.CLIENT)
public class MainScreen extends Screen {

    // ── Tabs ─────────────────────────────────────────────────────────────────
    private static final int TAB_SAVE    = 0;
    private static final int TAB_RESPAWN = 1;
    private int activeTab = TAB_SAVE;

    // ── Stars ─────────────────────────────────────────────────────────────────
    private static final int STAR_COUNT = 100;
    private final float[] sX   = new float[STAR_COUNT];
    private final float[] sY   = new float[STAR_COUNT];
    private final float[] sSpd = new float[STAR_COUNT];
    private final float[] sAlp = new float[STAR_COUNT];
    private final float[] sSz  = new float[STAR_COUNT];
    private final Random  rng  = new Random();
    private long tick = 0;

    // ── Colors (purple theme) ─────────────────────────────────────────────────
    private static final int COL_BG          = 0xFF04000D;   // near-black purple
    private static final int COL_PANEL       = 0xEA07001C;   // deep purple panel
    private static final int COL_PANEL_INNER = 0x44220040;   // inner sections
    private static final int COL_BORDER      = 0xFF9400FF;   // bright violet
    private static final int COL_BORDER_DIM  = 0xFF5500AA;   // dimmer violet
    private static final int COL_TITLE       = 0xFFDD88FF;   // lavender title
    private static final int COL_LABEL       = 0xFFBB99EE;   // purple-white label
    private static final int COL_VALUE       = 0xFFEEDDFF;   // bright label
    private static final int COL_MUTED       = 0xFF7755AA;   // muted purple
    private static final int COL_SEL_ROW     = 0x663300AA;   // selected row
    private static final int COL_OK          = 0xFF66FF99;   // green success
    private static final int COL_ERR         = 0xFFFF5555;   // red error
    private static final int COL_WARN        = 0xFFFFAA44;   // orange warning
    private static final int COL_TAB_ACTIVE  = 0x995500CC;   // active tab bg
    private static final int COL_TAB_IDLE    = 0x33440066;   // idle tab bg

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PW = 470;   // panel width
    private static final int PH = 310;   // panel height

    // ── Save tab ──────────────────────────────────────────────────────────────
    private TextFieldWidget saveNameField;
    private String saveMsg      = "";
    private int    saveMsgColor = COL_OK;
    private int    saveMsgTimer = 0;

    // ── Respawn tab ───────────────────────────────────────────────────────────
    private List<File>             fileList     = new ArrayList<>();
    private int                    selFile      = -1;
    private SchematicData          loaded       = null;
    private int                    fileScroll   = 0;

    private final List<String>          blockIds   = new ArrayList<>();
    private final List<TextFieldWidget> repFields  = new ArrayList<>();
    private int palScroll = 0;

    private String pasteMsg      = "";
    private int    pasteMsgColor = COL_OK;
    private int    pasteMsgTimer = 0;

    private long lastRefreshMs = 0;

    // ─────────────────────────────────────────────────────────────────────────
    public MainScreen() {
        super(Text.literal("Schematic Selector"));
        initStars();
        refreshFiles();
    }

    // ── Stars ─────────────────────────────────────────────────────────────────
    private void initStars() {
        for (int i = 0; i < STAR_COUNT; i++) resetStar(i, true);
    }
    private void resetStar(int i, boolean randomY) {
        sX[i]   = rng.nextFloat();
        sY[i]   = randomY ? rng.nextFloat() : -0.02f;
        sSpd[i] = 0.0008f + rng.nextFloat() * 0.0025f;
        sAlp[i] = 0.5f + rng.nextFloat() * 0.5f;
        sSz[i]  = 1 + rng.nextInt(3);
    }

    // ── File list ─────────────────────────────────────────────────────────────
    private void refreshFiles() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "schematics");
        List<File> fresh = new ArrayList<>();
        if (dir.exists()) {
            File[] arr = dir.listFiles(f -> f.getName().endsWith(".litematic"));
            if (arr != null) {
                Arrays.sort(arr, Comparator.comparing(File::getName));
                fresh.addAll(Arrays.asList(arr));
            }
        }
        // Only update if the list actually changed (avoid layout flicker)
        if (!fresh.equals(fileList)) {
            fileList = fresh;
            if (selFile >= fileList.size()) selFile = -1;
        }
        lastRefreshMs = System.currentTimeMillis();
    }

    // ── Helper: panel origin ──────────────────────────────────────────────────
    private int px() { return width  / 2 - PW / 2; }
    private int py() { return height / 2 - PH / 2; }

    // ── Init widgets ──────────────────────────────────────────────────────────
    @Override
    protected void init() {
        int cx = width / 2;
        int py = py();

        // Tab buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("  Save  "),
                b -> switchTab(TAB_SAVE))
                .dimensions(px() + 8, py + 34, 90, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("  Respawn  "),
                b -> switchTab(TAB_RESPAWN))
                .dimensions(px() + 104, py + 34, 100, 18).build());

        // Save name field
        saveNameField = new TextFieldWidget(textRenderer,
                cx - 120, py + 138, 240, 18, Text.literal("Schematic name"));
        saveNameField.setMaxLength(64);
        saveNameField.setText("my_build");
        addSelectableChild(saveNameField);

        // Save button
        addDrawableChild(ButtonWidget.builder(Text.literal("💾  Save Schematic"),
                b -> doSave())
                .dimensions(cx - 75, py + 164, 150, 20).build());

        // Paste button
        addDrawableChild(ButtonWidget.builder(Text.literal("⬇  Paste Here"),
                b -> doPaste())
                .dimensions(cx - 60, py + PH - 36, 120, 20).build());
    }

    private void switchTab(int t) {
        activeTab = t;
        clearPalette();
    }

    // ── Save ──────────────────────────────────────────────────────────────────
    private void doSave() {
        SelectionManager sel = SelectionManager.getInstance();
        if (!sel.hasSelection()) {
            flash(true, "Select Pos1 and Pos2 first  (press V to enable selection mode)", COL_WARN);
            return;
        }
        String name = saveNameField.getText().trim();
        if (name.isEmpty()) name = "my_build";

        if (SchematicWriter.save(name)) {
            flash(true, "Saved:  schematics/" + name + ".litematic", COL_OK);
        } else {
            flash(true, "Error saving schematic!", COL_ERR);
        }
        refreshFiles();
    }

    private void flash(boolean isSave, String msg, int color) {
        if (isSave) { saveMsg = msg; saveMsgColor = color; saveMsgTimer = 100; }
        else        { pasteMsg = msg; pasteMsgColor = color; pasteMsgTimer = 100; }
    }

    // ── Paste ─────────────────────────────────────────────────────────────────
    private void doPaste() {
        if (loaded == null) {
            flash(false, "Select a schematic from the list first!", COL_WARN);
            return;
        }
        Map<String, String> reps = new LinkedHashMap<>();
        for (int i = 0; i < blockIds.size() && i < repFields.size(); i++) {
            String v = repFields.get(i).getText().trim();
            if (!v.isEmpty()) reps.put(blockIds.get(i), v);
        }
        int n = SchematicPaster.paste(loaded, reps);
        flash(false, n >= 0 ? "Placed " + n + " blocks at your position!" : "Paste failed!", n >= 0 ? COL_OK : COL_ERR);
    }

    // ── Palette ───────────────────────────────────────────────────────────────
    private void loadPalette() {
        clearPalette();
        if (loaded == null) return;
        for (String id : loaded.blockCounts.keySet()) {
            blockIds.add(id);
            TextFieldWidget f = new TextFieldWidget(textRenderer, 0, 0, 110, 12,
                    Text.literal("replacement"));
            f.setMaxLength(64);
            addSelectableChild(f);
            repFields.add(f);
        }
    }

    private void clearPalette() {
        repFields.forEach(this::remove);
        repFields.clear();
        blockIds.clear();
        palScroll = 0;
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        tick++;

        // Auto-refresh files every 2 seconds in Respawn tab
        if (activeTab == TAB_RESPAWN && System.currentTimeMillis() - lastRefreshMs > 2000) {
            refreshFiles();
        }

        int W = width, H = height;
        int px = px(), py = py();

        // ── Full-screen black background
        ctx.fill(0, 0, W, H, COL_BG);

        // ── Stars
        for (int i = 0; i < STAR_COUNT; i++) {
            sY[i] += sSpd[i];
            if (sY[i] > 1.05f) resetStar(i, false);

            int sx = (int)(sX[i] * W);
            int sy = (int)(sY[i] * H);
            int sz = (int) sSz[i];
            int sa = (int)(sAlp[i] * 255);

            // Trail (white-blue tint)
            for (int t = 1; t <= 8; t++) {
                int ta = sa * (8 - t) / 8 / 4;
                int ty = sy - t * 2;
                if (ty >= 0) ctx.fill(sx, ty, sx + sz, ty + sz, (ta << 24) | 0xCCAAFF);
            }
            // Star core (bright white with purple tint)
            ctx.fill(sx, sy, sx + sz, sy + sz, (sa << 24) | 0xFFEEFF);
        }

        // ── Panel shadow
        ctx.fill(px + 5, py + 5, px + PW + 5, py + PH + 5, 0x66000000);

        // ── Panel body
        ctx.fill(px, py, px + PW, py + PH, COL_PANEL);

        // ── Animated glow border
        float glow = (float)((Math.sin(tick * 0.06) + 1.0) / 2.0);
        int bCol = blendColor(COL_BORDER_DIM, COL_BORDER, glow);
        drawBorder(ctx, px, py, PW, PH, 2, bCol);

        // ── Title bar background
        ctx.fill(px, py, px + PW, py + 30, 0x88110022);

        // ── Title
        String titleStr = "✦  SCHEMATIC SELECTOR  ✦";
        int titleX = width / 2 - textRenderer.getWidth(titleStr) / 2;
        // Glow shadow
        ctx.drawText(textRenderer, titleStr, titleX + 1, py + 10, 0x66CC00FF, false);
        ctx.drawText(textRenderer, titleStr, titleX, py + 9, COL_TITLE, false);

        // ── Tab bar
        ctx.fill(px, py + 30, px + PW, py + 56, 0x44110033);

        // Active tab indicator
        int tBx = (activeTab == TAB_SAVE) ? px + 8 : px + 104;
        int tBw = (activeTab == TAB_SAVE) ? 90 : 100;
        ctx.fill(tBx - 1, py + 30, tBx + tBw + 1, py + 56, COL_TAB_ACTIVE);
        drawBorder(ctx, tBx - 1, py + 30, tBw + 2, 26, 1, COL_BORDER);

        // Tab divider line
        ctx.fill(px + 8, py + 55, px + PW - 8, py + 57, COL_BORDER_DIM);

        // ── Tab content
        if (activeTab == TAB_SAVE) renderSaveTab(ctx, mx, my, delta);
        else                        renderRespawnTab(ctx, mx, my, delta);

        super.render(ctx, mx, my, delta);
    }

    // ── Save tab ──────────────────────────────────────────────────────────────
    private void renderSaveTab(DrawContext ctx, int mx, int my, float delta) {
        int px = px(), py = py(), cx = width / 2;
        SelectionManager sel = SelectionManager.getInstance();

        // Selection mode badge
        boolean on = sel.isSelectionMode();
        String modeLabel = on ? "● SELECTION  ON" : "○ SELECTION  OFF";
        int modeCol = on ? COL_OK : COL_ERR;
        ctx.fill(px + 10, py + 63, px + 200, py + 78, on ? 0x33006622 : 0x33330011);
        drawBorder(ctx, px + 10, py + 63, 190, 15, 1, modeCol);
        ctx.drawText(textRenderer, modeLabel, px + 14, py + 67, modeCol, false);

        // Key hints inside badge
        if (on) {
            ctx.drawText(textRenderer, "Left-click = Pos1     Right-click = Pos2",
                    px + 210, py + 67, COL_MUTED, false);
        } else {
            ctx.drawText(textRenderer, "Press  V  to enable selection mode",
                    px + 210, py + 67, COL_MUTED, false);
        }

        // Pos1 / Pos2
        drawLabeledPos(ctx, px + 12, py + 88, "Pos 1", sel.getPos1());
        drawLabeledPos(ctx, px + 12, py + 103, "Pos 2", sel.getPos2());

        // Size
        if (sel.hasSelection()) {
            int vol = sel.getWidth() * sel.getHeight() * sel.getLength();
            String szStr = "Size:  " + sel.getWidth() + " x " + sel.getHeight() + " x " + sel.getLength()
                    + "   (" + vol + " blocks)";
            ctx.drawText(textRenderer, szStr,
                    cx - textRenderer.getWidth(szStr) / 2, py + 120, COL_MUTED, false);
        }

        // Divider
        ctx.fill(px + 20, py + 132, px + PW - 20, py + 133, COL_BORDER_DIM);

        // Name field label
        ctx.drawText(textRenderer, "FILE NAME", px + 12, py + 141, COL_LABEL, false);

        // Name field
        saveNameField.setX(cx - 120);
        saveNameField.setY(py + 138);
        saveNameField.render(ctx, mx, my, delta);

        // Hint
        ctx.drawText(textRenderer, "Saves to:  .minecraft/schematics/<name>.litematic",
                cx - textRenderer.getWidth("Saves to:  .minecraft/schematics/<name>.litematic") / 2,
                py + 190, COL_MUTED, false);

        // Status message
        if (saveMsgTimer-- > 0) {
            ctx.drawText(textRenderer, saveMsg,
                    cx - textRenderer.getWidth(saveMsg) / 2, py + 205, saveMsgColor, false);
        }

        // Keybinding reminder at bottom
        ctx.fill(px, py + PH - 22, px + PW, py + PH, 0x44110033);
        String keys = "V = Toggle Selection   |   M = Open / Close Menu";
        ctx.drawText(textRenderer, keys,
                cx - textRenderer.getWidth(keys) / 2, py + PH - 14, COL_MUTED, false);
    }

    // ── Respawn tab ───────────────────────────────────────────────────────────
    private void renderRespawnTab(DrawContext ctx, int mx, int my, float delta) {
        int px = px(), py = py(), cx = width / 2;

        // Left panel: file list
        int listX1 = px + 8, listX2 = px + 200;
        int listY1 = py + 60, listY2 = py + PH - 38;

        ctx.fill(listX1, listY1, listX2, listY2, COL_PANEL_INNER);
        drawBorder(ctx, listX1, listY1, listX2 - listX1, listY2 - listY1, 1, COL_BORDER_DIM);
        ctx.drawText(textRenderer, "SCHEMATICS", listX1 + 4, listY1 + 4, COL_LABEL, false);

        // Auto-refresh indicator
        long ms = System.currentTimeMillis() - lastRefreshMs;
        String refreshStr = "↻  " + (ms < 1000 ? "just now" : (ms / 1000) + "s ago");
        ctx.drawText(textRenderer, refreshStr,
                listX2 - textRenderer.getWidth(refreshStr) - 4, listY1 + 4, COL_MUTED, false);

        ctx.fill(listX1, listY1 + 15, listX2, listY1 + 16, COL_BORDER_DIM);

        if (fileList.isEmpty()) {
            ctx.drawText(textRenderer, "No .litematic files found",
                    listX1 + 6, listY1 + 22, COL_MUTED, false);
            ctx.drawText(textRenderer, "in schematics/ folder",
                    listX1 + 6, listY1 + 34, COL_MUTED, false);
        } else {
            int rows = (listY2 - listY1 - 18) / 14;
            for (int i = fileScroll; i < Math.min(fileList.size(), fileScroll + rows); i++) {
                File f = fileList.get(i);
                int ry = listY1 + 18 + (i - fileScroll) * 14;
                boolean isSel = (i == selFile);

                if (isSel) {
                    ctx.fill(listX1 + 1, ry - 1, listX2 - 1, ry + 13, COL_SEL_ROW);
                    ctx.fill(listX1 + 1, ry - 1, listX1 + 3, ry + 13, COL_BORDER);
                }

                String nm = f.getName().replace(".litematic", "");
                if (textRenderer.getWidth(nm) > listX2 - listX1 - 16)
                    nm = truncate(nm, listX2 - listX1 - 20);
                ctx.drawText(textRenderer, nm, listX1 + 8, ry + 1,
                        isSel ? 0xFFFFEEFF : COL_VALUE, false);
            }
        }

        // Right panel: palette + replacements
        int palX1 = px + 206, palX2 = px + PW - 8;
        int palY1 = py + 60, palY2 = py + PH - 38;

        ctx.fill(palX1, palY1, palX2, palY2, COL_PANEL_INNER);
        drawBorder(ctx, palX1, palY1, palX2 - palX1, palY2 - palY1, 1, COL_BORDER_DIM);

        if (loaded == null) {
            ctx.drawText(textRenderer, "BLOCK PALETTE", palX1 + 4, palY1 + 4, COL_LABEL, false);
            ctx.fill(palX1, palY1 + 15, palX2, palY1 + 16, COL_BORDER_DIM);
            ctx.drawText(textRenderer, "← Select a file", palX1 + 30, palY1 + 80, COL_MUTED, false);
            ctx.drawText(textRenderer, "to see its blocks", palX1 + 20, palY1 + 94, COL_MUTED, false);
        } else {
            // Header
            String szStr = loaded.width + "×" + loaded.height + "×" + loaded.length;
            ctx.drawText(textRenderer, loaded.name, palX1 + 4, palY1 + 4, 0xFFEEDDFF, false);
            ctx.drawText(textRenderer, szStr, palX2 - textRenderer.getWidth(szStr) - 4, palY1 + 4, COL_MUTED, false);
            ctx.fill(palX1, palY1 + 15, palX2, palY1 + 16, COL_BORDER_DIM);

            // Column headers
            ctx.drawText(textRenderer, "Block", palX1 + 4, palY1 + 19, COL_LABEL, false);
            ctx.drawText(textRenderer, "Count", palX1 + 112, palY1 + 19, COL_LABEL, false);
            ctx.drawText(textRenderer, "Replace with...", palX1 + 155, palY1 + 19, COL_LABEL, false);
            ctx.fill(palX1, palY1 + 28, palX2, palY1 + 29, COL_BORDER_DIM);

            List<Map.Entry<String, Integer>> entries = new ArrayList<>(loaded.blockCounts.entrySet());
            int rowH  = 14;
            int rows  = (palY2 - palY1 - 32) / rowH;
            int visStart = palScroll;

            for (int i = visStart; i < Math.min(entries.size(), visStart + rows); i++) {
                Map.Entry<String, Integer> e = entries.get(i);
                int ry = palY1 + 30 + (i - visStart) * rowH;

                // Alternating row tint
                if ((i % 2) == 0) ctx.fill(palX1 + 1, ry, palX2 - 1, ry + rowH, 0x11BB88FF);

                // Block name
                String bname = e.getKey().replace("minecraft:", "");
                if (textRenderer.getWidth(bname) > 100) bname = truncate(bname, 100);
                ctx.drawText(textRenderer, bname, palX1 + 4, ry + 2, COL_VALUE, false);

                // Count
                ctx.drawText(textRenderer, String.valueOf(e.getValue()), palX1 + 112, ry + 2, COL_MUTED, false);

                // Replace field
                int fi = i - visStart;
                if (fi < repFields.size()) {
                    repFields.get(fi).setX(palX1 + 152);
                    repFields.get(fi).setY(ry + 1);
                    repFields.get(fi).setHeight(rowH - 1);
                    repFields.get(fi).render(ctx, 0, 0, delta);
                }
            }
        }

        // Paste status
        if (pasteMsgTimer-- > 0) {
            ctx.drawText(textRenderer, pasteMsg,
                    cx - textRenderer.getWidth(pasteMsg) / 2, py + PH - 52, pasteMsgColor, false);
        }

        // Bottom bar
        ctx.fill(px, py + PH - 22, px + PW, py + PH, 0x44110033);
        String hint = "Files auto-refresh every 2 s  —  no game restart needed";
        ctx.drawText(textRenderer, hint,
                cx - textRenderer.getWidth(hint) / 2, py + PH - 14, COL_MUTED, false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drawLabeledPos(DrawContext ctx, int x, int y, String label, BlockPos pos) {
        ctx.drawText(textRenderer, label + ":", x, y, COL_LABEL, false);
        String val = pos != null
                ? String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())
                : "not set";
        ctx.drawText(textRenderer, val, x + 38, y, pos != null ? COL_VALUE : COL_MUTED, false);
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int t, int col) {
        ctx.fill(x,         y,         x + w,     y + t,     col);
        ctx.fill(x,         y + h - t, x + w,     y + h,     col);
        ctx.fill(x,         y,         x + t,     y + h,     col);
        ctx.fill(x + w - t, y,         x + w,     y + h,     col);
    }

    private String truncate(String s, int maxW) {
        while (s.length() > 3 && textRenderer.getWidth(s + "..") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    private int blendColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int)(ar + (br - ar) * t);
        int g = (int)(ag + (bg - ag) * t);
        int bl = (int)(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (activeTab == TAB_RESPAWN) {
            int px = px(), py = py();
            int listX1 = px + 8, listX2 = px + 200;
            int listY1 = py + 60 + 18;

            // File row click
            if (mx >= listX1 && mx <= listX2 && my >= listY1) {
                int rows = (py + PH - 38 - listY1) / 14;
                int clicked = ((int) my - listY1) / 14;
                int idx = fileScroll + clicked;
                if (idx >= 0 && idx < fileList.size() && clicked < rows) {
                    selFile = idx;
                    clearPalette();
                    loaded = SchematicReader.read(fileList.get(idx));
                    if (loaded != null) loadPalette();
                    return true;
                }
            }
        }

        if (activeTab == TAB_SAVE && saveNameField != null)
            saveNameField.mouseClicked(mx, my, button);

        for (TextFieldWidget f : repFields) f.mouseClicked(mx, my, button);

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int px = px(), py = py();
        if (activeTab == TAB_RESPAWN) {
            if (mx <= px + 200)
                fileScroll = clamp(fileScroll - (int) vAmt, 0, Math.max(0, fileList.size() - 10));
            else
                palScroll = clamp(palScroll - (int) vAmt, 0, Math.max(0, blockIds.size() - 11));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (activeTab == TAB_SAVE && saveNameField != null && saveNameField.isFocused()) {
            if (saveNameField.keyPressed(key, scan, mod)) return true;
            if (key == 257) { doSave(); return true; }
        }
        for (TextFieldWidget f : repFields)
            if (f.isFocused() && f.keyPressed(key, scan, mod)) return true;
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char c, int mod) {
        if (activeTab == TAB_SAVE && saveNameField != null && saveNameField.isFocused())
            return saveNameField.charTyped(c, mod);
        for (TextFieldWidget f : repFields)
            if (f.isFocused() && f.charTyped(c, mod)) return true;
        return super.charTyped(c, mod);
    }

    @Override public boolean shouldPause()       { return false; }
    @Override public boolean shouldCloseOnEsc()  { return true; }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
