package com.professor.schematic;

import com.professor.schematic.gui.SchematicSaveScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public class SchematicMod implements ClientModInitializer {

    // Key: toggle selection mode (configurable in Controls settings)
    public static KeyBinding toggleSelectionKey;
    // Key: open save screen (M by default)
    public static KeyBinding openSaveKey;

    @Override
    public void onInitializeClient() {

        // ── Register key bindings ────────────────────────────────────────────
        toggleSelectionKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.schematicmod.toggle_selection",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,          // Default: V — change in Options > Controls
                "category.schematicmod.general"
        ));

        openSaveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.schematicmod.open_save",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,          // Default: M
                "category.schematicmod.general"
        ));

        // ── Tick: handle key presses ─────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            // Toggle selection mode
            while (toggleSelectionKey.wasPressed()) {
                SelectionManager sel = SelectionManager.getInstance();
                sel.setSelectionMode(!sel.isSelectionMode());

                if (client.player != null) {
                    if (sel.isSelectionMode()) {
                        client.player.sendMessage(
                                Text.literal("§a[Schematic] §fSelection Mode: §aON §7(Left-click = Pos1, Right-click = Pos2)"),
                                true);
                    } else {
                        client.player.sendMessage(
                                Text.literal("§c[Schematic] §fSelection Mode: §cOFF"),
                                true);
                    }
                }
            }

            // Open save screen
            while (openSaveKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new SchematicSaveScreen());
                }
            }
        });

        // ── Attack block (left-click) → Set Pos1 ────────────────────────────
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient) return ActionResult.PASS;

            SelectionManager sel = SelectionManager.getInstance();
            if (!sel.isSelectionMode()) return ActionResult.PASS;

            sel.setPos1(pos);
            player.sendMessage(
                    Text.literal(String.format("§b[Schematic] §fPos1 set: §e(%d, %d, %d)",
                            pos.getX(), pos.getY(), pos.getZ())),
                    true);

            // Cancel the actual block attack so we don't break the block
            return ActionResult.FAIL;
        });

        // ── Use block (right-click) → Set Pos2 ──────────────────────────────
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient) return ActionResult.PASS;

            SelectionManager sel = SelectionManager.getInstance();
            if (!sel.isSelectionMode()) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            sel.setPos2(pos);
            player.sendMessage(
                    Text.literal(String.format("§b[Schematic] §fPos2 set: §e(%d, %d, %d)",
                            pos.getX(), pos.getY(), pos.getZ())),
                    true);

            return ActionResult.FAIL;
        });
    }
}
