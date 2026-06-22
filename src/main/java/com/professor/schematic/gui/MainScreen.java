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

    // ── Falling stars ─────────────────────────────────────────────────────────
    private static final int STAR_COUNT = 120;
    private final float[] starX   = new float[STAR_COUNT];
    private final float[] starY   = new float[STAR_COUNT];
    private final float[] starSpd = new float[STAR_COUNT];
    private final float[] starAlp = new float[STAR_COUNT];
    private final float[] starSz  = new float[STAR_COUNT];
    private final Random  rng     = new Random();
    private long tick = 0;

    // ── Save tab widgets ──────────────────────────────────────────────────────
    private TextFieldWidget saveNameField;
    private String saveStatus = "";
    private int saveStatusColor = 0xFFFFFFFF;
    private int saveStatusTimer = 0;

    // ── Respawn tab widgets ───────────────────────────────────────────────────
    private List<File>   schematicFiles  = new ArrayList<>();
    private int          selectedFile    = -1;
    private SchematicData loadedSchema   = null;
    private int          fileScrollOff   = 0;

    // Block replacements: original blockId → replacement text field
    private final List<String>            paletteIds    = new ArrayList<>();
    private final List<TextFieldWidget>   replaceFields = new ArrayList<>();
    private int paletteScrollOff = 0;

    private String pasteStatus = "";
    private int pasteStatusColor = 0xFFFFFFFF;
    private int pasteStatusTimer = 0;

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int PANEL_W = 460;
    private static final int PANEL_H = 300;

    public MainScreen() {
        super(Text.literal("Schematic Selector"));
        initStars();
        refreshFileList();
    }

    // ── Star init ─────────────────────────────────────────────────────────────
    private void initStars() {
        for (int i = 0; i < STAR_COUNT; i++) resetStar(i, true);
    }

    private void resetStar(int i, boolean randomY) {
        starX[i]   = rng.nextFloat();
        starY[i]   = randomY ? rng.nextFloat() : -0.02f;
        starSpd[i] = 0.001f + rng.nextFloat() * 0.003f;
        starAlp[i] = 0.4f + rng.nextFloat() * 0.6f;
        starSz[i]  = 1 + rng.nextInt(3);
    }

    // ── File list ─────────────────────────────────────────────────────────────
    private void refreshFileList() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "schematics");
        schematicFiles.clear();
        if (dir.exists()) {
            File[] files = dir.listFiles(f -> f.getName().endsWith(".litematic"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                schematicFiles.addAll(Arrays.asList(files));
            }
        }
    }

    // ── Init widgets ──────────────────────────────────────────────────────────
    @Override
    protected void init() {
        buildSaveWidgets();
        buildRespawnWidgets();
    }

    private int px() { return width  / 2 - PANEL_W / 2; }
    private int py() { return height / 2 - PANEL_H / 2; }

    private void buildSaveWidgets() {
        int cx = width / 2;
        int py = py();

        // Tab buttons
        this.addDrawableChild(ButtonWidget.builder(Text.literal("💾  حفظ"),
                b -> switchTab(TAB_SAVE))
                .dimensions(px() + 10, py + 32, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("🔄  رسبنت"),
                b -> switchTab(TAB_RESPAWN))
                .dimensions(px() + 120, py + 32, 110, 20).build());

        // Save name field
        saveNameField = new TextFieldWidget(textRenderer,
                cx - 110, py + 130, 220, 20, Text.literal("اسم الملف"));
        saveNameField.setMaxLength(64);
        saveNameField.setText("my_build");
        this.addSelectableChild(saveNameField);

        // Save button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("💾  Save"),
                b -> doSave())
                .dimensions(cx - 55, py + 158, 110, 22).build());
    }

    private void buildRespawnWidgets() {
        int px = px(), py = py();
        int cx = width / 2;

        // Refresh button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("↻"),
                b -> { refreshFileList(); selectedFile = -1; loadedSchema = null; clearPalette(); })
                .dimensions(px + PANEL_W - 30, py + 57, 20, 16).build());

        // Paste button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("⬇  Paste Here"),
                b -> doPaste())
                .dimensions(cx - 65, py + PANEL_H - 38, 130, 22).build());
    }

    private void switchTab(int tab) {
        activeTab = tab;
        clearPalette();
    }

    // ── Save ──────────────────────────────────────────────────────────────────
    private void doSave() {
        SelectionManager sel = SelectionManager.getInstance();
        if (!sel.hasSelection()) {
            saveStatus = "⚠  حدد Pos1 و Pos2 أولاً!";
            saveStatusColor = 0xFFFF8844;
            saveStatusTimer = 80;
            return;
        }
        String name = saveNameField.getText().trim();
        if (name.isEmpty()) name = "my_build";

        if (SchematicWriter.save(name)) {
            saveStatus = "✅  تم الحفظ: schematics/" + name + ".litematic";
            saveStatusColor = 0xFF44FF88;
        } else {
            saveStatus = "❌  خطأ في الحفظ!";
            saveStatusColor = 0xFFFF4444;
        }
        saveStatusTimer = 100;
    }

    // ── Paste ─────────────────────────────────────────────────────────────────
    private void doPaste() {
        if (loadedSchema == null) {
            pasteStatus = "⚠  اختر ملف أولاً!";
            pasteStatusColor = 0xFFFF8844;
            pasteStatusTimer = 80;
            return;
        }
        Map<String, String> replacements = new LinkedHashMap<>();
        for (int i = 0; i < paletteIds.size(); i++) {
            if (i < replaceFields.size()) {
                String val = replaceFields.get(i).getText().trim();
                if (!val.isEmpty()) replacements.put(paletteIds.get(i), val);
            }
        }
        int placed = SchematicPaster.paste(loadedSchema, replacements);
        if (placed >= 0) {
            pasteStatus = "✅  تم إنزال " + placed + " بلوكة";
            pasteStatusColor = 0xFF44FF88;
        } else {
            pasteStatus = "❌  فشل الإنزال!";
            pasteStatusColor = 0xFFFF4444;
        }
        pasteStatusTimer = 100;
    }

    // ── Palette list ──────────────────────────────────────────────────────────
    private void loadPalette() {
        clearPalette();
        if (loadedSchema == null) return;

        for (Map.Entry<String, Integer> e : loadedSchema.blockCounts.entrySet()) {
            paletteIds.add(e.getKey());
            TextFieldWidget f = new TextFieldWidget(textRenderer, 0, 0, 120, 14,
                    Text.literal("replacement"));
            f.setMaxLength(64);
            f.setText("");
            this.addSelectableChild(f);
            replaceFields.add(f);
        }
    }

    private void clearPalette() {
        for (TextFieldWidget f : replaceFields) this.remove(f);
        replaceFields.clear();
        paletteIds.clear();
        paletteScrollOff = 0;
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        tick++;

        int W = width, H = height;
        int px = px(), py = py();

        // ── Black background
        ctx.fill(0, 0, W, H, 0xFF000000);

        // ── Falling stars
        for (int i = 0; i < STAR_COUNT; i++) {
            starY[i] += starSpd[i];
            if (starY[i] > 1.05f) resetStar(i, false);

            int sx = (int)(starX[i] * W);
            int sy = (int)(starY[i] * H);
            int sz = (int) starSz[i];
            int sa = (int)(starAlp[i] * 220);

            // Trail
            int trailLen = 6 + (int)(starSpd[i] / 0.001f);
            for (int t = 1; t <= trailLen; t++) {
                int ta = sa * (trailLen - t) / trailLen / 3;
                int ty2 = sy - t * 2;
                if (ty2 >= 0) ctx.fill(sx, ty2, sx + sz, ty2 + sz,
                        (ta << 24) | 0xAADDFF);
            }
            // Core
            ctx.fill(sx, sy, sx + sz, sy + sz, (sa << 24) | 0xFFFFFF);
        }

        // ── Panel shadow
        ctx.fill(px + 4, py + 4, px + PANEL_W + 4, py + PANEL_H + 4, 0x55000000);

        // ── Panel background
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, 0xDD050512);

        // ── Animated border
        float glow = (float)((Math.sin(tick * 0.05f) + 1.0) / 2.0);
        int br = (int)(60 + glow * 100);
        int bb = (int)(200 + glow * 55);
        int borderCol = 0xFF000000 | (br << 16) | (10 << 8) | bb;
        ctx.fill(px,              py,              px + PANEL_W, py + 2,        borderCol);
        ctx.fill(px,              py + PANEL_H-2,  px + PANEL_W, py + PANEL_H,  borderCol);
        ctx.fill(px,              py,              px + 2,       py + PANEL_H,  borderCol);
        ctx.fill(px + PANEL_W-2,  py,              px + PANEL_W, py + PANEL_H,  borderCol);

        // ── Title
        float pulse = (float)((Math.sin(tick * 0.07f) + 1.0) / 2.0);
        int tr = (int)(140 + pulse * 115); int tg = (int)(40 + pulse * 60);
        int titleCol = 0xFF000000 | (tr << 16) | (tg << 8) | 255;
        String title = "✦  Schematic Selector  ✦";
        ctx.drawText(textRenderer, title,
                width/2 - textRenderer.getWidth(title)/2, py + 10, titleCol, false);

        // ── Tab bar background
        ctx.fill(px, py + 28, px + PANEL_W, py + 56, 0x44001133);

        // ── Active tab highlight
        int tabX = (activeTab == TAB_SAVE) ? px + 10 : px + 120;
        int tabW = (activeTab == TAB_SAVE) ? 100 : 110;
        ctx.fill(tabX - 2, py + 28, tabX + tabW + 2, py + 56, 0x6600AAFF);

        // ── Divider
        ctx.fill(px + 10, py + 56, px + PANEL_W - 10, py + 57, 0x88224477);

        // ── Tab content
        if (activeTab == TAB_SAVE)    renderSaveTab(ctx, mx, my, delta);
        else                           renderRespawnTab(ctx, mx, my, delta);

        super.render(ctx, mx, my, delta);
    }

    // ── Save tab render ───────────────────────────────────────────────────────
    private void renderSaveTab(DrawContext ctx, int mx, int my, float delta) {
        int cx = width / 2, py = py();
        SelectionManager sel = SelectionManager.getInstance();

        // Mode status
        String modeStr = sel.isSelectionMode()
                ? "§aوضع التحديد: §2مفعّل" : "§cوضع التحديد: §4معطّل";
        ctx.drawText(textRenderer, Text.literal(sel.isSelectionMode()
                ? "● وضع التحديد: مفعّل" : "○ وضع التحديد: معطّل"),
                cx - 80, py + 65,
                sel.isSelectionMode() ? 0xFF44FF88 : 0xFFFF6644, false);

        // Pos1
        String p1 = sel.getPos1() != null
                ? fmt(sel.getPos1()) : "لم يُحدد";
        ctx.drawText(textRenderer, "Pos1: " + p1, px() + 20, py + 80, 0xFF88CCFF, false);

        // Pos2
        String p2 = sel.getPos2() != null
                ? fmt(sel.getPos2()) : "لم يُحدد";
        ctx.drawText(textRenderer, "Pos2: " + p2, px() + 20, py + 92, 0xFF88CCFF, false);

        // Size
        if (sel.hasSelection()) {
            String sz = "الحجم: " + sel.getWidth() + " × " + sel.getHeight() + " × " + sel.getLength()
                    + "  (" + (sel.getWidth() * sel.getHeight() * sel.getLength()) + " بلوكة)";
            ctx.drawText(textRenderer, sz, cx - textRenderer.getWidth(sz)/2, py + 108, 0xFFAAAAAA, false);
        }

        // Keys hint
        ctx.drawText(textRenderer, "V = تفعيل التحديد  |  كليك يسار = Pos1  |  كليك يمين = Pos2",
                cx - textRenderer.getWidth("V = تفعيل التحديد  |  كليك يسار = Pos1  |  كليك يمين = Pos2")/2,
                py + 120, 0xFF556688, false);

        // Name label
        ctx.drawText(textRenderer, "اسم الملف:", px() + 20, py + 133, 0xFFCCCCCC, false);

        // Name field
        saveNameField.setX(width/2 - 110);
        saveNameField.setY(py + 130);
        saveNameField.render(ctx, mx, my, delta);

        // Status
        if (saveStatusTimer > 0) {
            saveStatusTimer--;
            ctx.drawText(textRenderer, saveStatus,
                    cx - textRenderer.getWidth(saveStatus)/2, py + 186, saveStatusColor, false);
        }
    }

    // ── Respawn tab render ────────────────────────────────────────────────────
    private void renderRespawnTab(DrawContext ctx, int mx, int my, float delta) {
        int px = px(), py = py();
        int cx = width / 2;

        // ── File list panel
        ctx.fill(px + 8, py + 60, px + 200, py + PANEL_H - 40, 0x55001122);
        ctx.drawText(textRenderer, "الملفات:", px + 10, py + 62, 0xFF8899BB, false);

        if (schematicFiles.isEmpty()) {
            ctx.drawText(textRenderer, "لا توجد ملفات",
                    px + 14, py + 78, 0xFF555566, false);
        } else {
            int visibleFiles = 10;
            for (int i = fileScrollOff; i < Math.min(schematicFiles.size(), fileScrollOff + visibleFiles); i++) {
                File f = schematicFiles.get(i);
                int fy = py + 74 + (i - fileScrollOff) * 14;
                boolean sel = (i == selectedFile);

                if (sel) ctx.fill(px + 9, fy - 1, px + 199, fy + 12, 0x66003366);

                String nm = f.getName().replace(".litematic", "");
                if (nm.length() > 18) nm = nm.substring(0, 16) + "..";
                ctx.drawText(textRenderer, (sel ? "▶ " : "  ") + nm,
                        px + 12, fy, sel ? 0xFF66CCFF : 0xFFAABBCC, false);
            }
        }

        // ── Palette / block list panel
        ctx.fill(px + 205, py + 60, px + PANEL_W - 8, py + PANEL_H - 40, 0x55001122);

        if (loadedSchema != null) {
            // Info
            ctx.drawText(textRenderer,
                    loadedSchema.name + "  (" + loadedSchema.width + "×" + loadedSchema.height + "×" + loadedSchema.length + ")",
                    px + 208, py + 62, 0xFF88CCFF, false);
            ctx.drawText(textRenderer, "البلوكة  →  استبدال", px + 208, py + 74, 0xFF556688, false);
            ctx.fill(px + 205, py + 84, px + PANEL_W - 8, py + 85, 0x55224466);

            int visibleBlocks = 11;
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(loadedSchema.blockCounts.entrySet());
            for (int i = paletteScrollOff; i < Math.min(entries.size(), paletteScrollOff + visibleBlocks); i++) {
                Map.Entry<String, Integer> e = entries.get(i);
                int by = py + 88 + (i - paletteScrollOff) * 15;

                // Block name (short)
                String bname = e.getKey().replace("minecraft:", "");
                if (bname.length() > 13) bname = bname.substring(0, 11) + "..";
                ctx.drawText(textRenderer, bname + " ×" + e.getValue(), px + 208, by, 0xFFCCDDEE, false);

                // Replacement field
                if (i < replaceFields.size()) {
                    TextFieldWidget f = replaceFields.get(i - paletteScrollOff +
                            Math.max(0, paletteScrollOff));
                    // Actually position them
                    if (i - paletteScrollOff < replaceFields.size()) {
                        replaceFields.get(i - paletteScrollOff).setX(px + 310);
                        replaceFields.get(i - paletteScrollOff).setY(by - 1);
                        replaceFields.get(i - paletteScrollOff).render(ctx, 0, 0, delta);
                    }
                }
            }
        } else {
            ctx.drawText(textRenderer, "← اختر ملفاً",
                    px + 230, py + 160, 0xFF334455, false);
        }

        // Paste status
        if (pasteStatusTimer > 0) {
            pasteStatusTimer--;
            ctx.drawText(textRenderer, pasteStatus,
                    cx - textRenderer.getWidth(pasteStatus)/2,
                    py + PANEL_H - 52, pasteStatusColor, false);
        }
    }

    // ── Mouse click ───────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (activeTab == TAB_RESPAWN) {
            int px = px(), py = py();

            // File list click
            if (mx >= px + 8 && mx <= px + 200 && my >= py + 74) {
                int clickedRow = ((int) my - py - 74) / 14;
                int fileIdx = fileScrollOff + clickedRow;
                if (fileIdx >= 0 && fileIdx < schematicFiles.size()) {
                    selectedFile = fileIdx;
                    clearPalette();
                    loadedSchema = SchematicReader.read(schematicFiles.get(fileIdx));
                    if (loadedSchema != null) loadPalette();
                    return true;
                }
            }
        }

        if (activeTab == TAB_SAVE && saveNameField != null) {
            saveNameField.mouseClicked(mx, my, button);
        }

        for (TextFieldWidget f : replaceFields) f.mouseClicked(mx, my, button);

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int px = px(), py = py();
        if (activeTab == TAB_RESPAWN) {
            if (mx >= px + 8 && mx <= px + 200) {
                fileScrollOff = Math.max(0, Math.min(
                        Math.max(0, schematicFiles.size() - 10),
                        fileScrollOff - (int) vAmt));
                return true;
            }
            if (mx >= px + 205) {
                paletteScrollOff = Math.max(0, Math.min(
                        Math.max(0, paletteIds.size() - 11),
                        paletteScrollOff - (int) vAmt));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (activeTab == TAB_SAVE && saveNameField != null && saveNameField.isFocused()) {
            if (saveNameField.keyPressed(key, scan, mod)) return true;
            if (key == 257) { doSave(); return true; }
        }
        for (TextFieldWidget f : replaceFields) {
            if (f.isFocused() && f.keyPressed(key, scan, mod)) return true;
        }
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean charTyped(char c, int mod) {
        if (activeTab == TAB_SAVE && saveNameField != null && saveNameField.isFocused())
            return saveNameField.charTyped(c, mod);
        for (TextFieldWidget f : replaceFields)
            if (f.isFocused() && f.charTyped(c, mod)) return true;
        return super.charTyped(c, mod);
    }

    @Override public boolean shouldPause()       { return false; }
    @Override public boolean shouldCloseOnEsc()  { return true; }

    private String fmt(BlockPos p) {
        return "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
    }
}
