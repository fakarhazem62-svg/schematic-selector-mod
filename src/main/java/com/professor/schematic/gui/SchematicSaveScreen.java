package com.professor.schematic.gui;

import com.professor.schematic.SchematicWriter;
import com.professor.schematic.SelectionManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public class SchematicSaveScreen extends Screen {

    private TextFieldWidget nameField;
    private Text statusMessage = null;
    private int statusColor = 0xFFFFFFFF;
    private int statusTimer = 0;

    public SchematicSaveScreen() {
        super(Text.literal("Save Schematic"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Text field for file name
        nameField = new TextFieldWidget(this.textRenderer, cx - 110, cy - 10, 220, 20,
                Text.literal("Schematic name"));
        nameField.setMaxLength(64);
        nameField.setText("my_schematic");
        nameField.setFocused(true);
        this.addSelectableChild(nameField);

        // Save button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Save"),
                btn -> doSave()
        ).dimensions(cx - 55, cy + 20, 50, 20).build());

        // Cancel button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel"),
                btn -> this.close()
        ).dimensions(cx + 5, cy + 20, 50, 20).build());
    }

    private void doSave() {
        SelectionManager sel = SelectionManager.getInstance();
        if (!sel.hasSelection()) {
            statusMessage = Text.literal("No selection! Set Pos1 and Pos2 first.");
            statusColor = 0xFFFF4444;
            statusTimer = 80;
            return;
        }

        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "my_schematic";

        boolean ok = SchematicWriter.save(name);
        if (ok) {
            statusMessage = Text.literal("Saved to schematics/" + name + ".nbt");
            statusColor = 0xFF44FF44;
            statusTimer = 100;
        } else {
            statusMessage = Text.literal("Error saving schematic!");
            statusColor = 0xFFFF4444;
            statusTimer = 80;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Background overlay
        this.renderBackground(ctx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Panel
        int pw = 280, ph = 160;
        int px = cx - pw / 2, py = cy - ph / 2;
        ctx.fill(px, py, px + pw, py + ph, 0xDD050510);
        ctx.fill(px, py, px + pw, py + 2, 0xFF8844CC);
        ctx.fill(px, py + ph - 2, px + pw, py + ph, 0xFF8844CC);
        ctx.fill(px, py, px + 2, py + ph, 0xFF8844CC);
        ctx.fill(px + pw - 2, py, px + pw, py + ph, 0xFF8844CC);

        // Title
        String title = "Save Schematic";
        ctx.drawText(this.textRenderer, title, cx - this.textRenderer.getWidth(title) / 2, py + 12, 0xFFCCAAFF, false);

        // Selection info
        SelectionManager sel = SelectionManager.getInstance();
        if (sel.hasSelection()) {
            BlockPos p1 = sel.getPos1();
            BlockPos p2 = sel.getPos2();
            String info = String.format("Pos1: (%d,%d,%d)  Pos2: (%d,%d,%d)",
                    p1.getX(), p1.getY(), p1.getZ(),
                    p2.getX(), p2.getY(), p2.getZ());
            String size = String.format("Size: %d x %d x %d  (%d blocks)",
                    sel.getWidth(), sel.getHeight(), sel.getLength(),
                    sel.getWidth() * sel.getHeight() * sel.getLength());
            ctx.drawText(this.textRenderer, info, cx - this.textRenderer.getWidth(info) / 2, py + 28, 0xFF88BBFF, false);
            ctx.drawText(this.textRenderer, size, cx - this.textRenderer.getWidth(size) / 2, py + 40, 0xFFAAAAAA, false);
        } else {
            String noSel = "No selection made yet!";
            ctx.drawText(this.textRenderer, noSel, cx - this.textRenderer.getWidth(noSel) / 2, py + 28, 0xFFFF8844, false);
        }

        // Label above text field
        ctx.drawText(this.textRenderer, "File name:", px + 30, cy - 22, 0xFFCCCCCC, false);

        // Text field
        nameField.render(ctx, mouseX, mouseY, delta);

        // Status message
        if (statusTimer > 0) {
            statusTimer--;
            ctx.drawText(this.textRenderer, statusMessage,
                    cx - this.textRenderer.getWidth(statusMessage.getString()) / 2,
                    cy + 48, statusColor, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField.isFocused() && nameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad Enter
            doSave();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return nameField.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
