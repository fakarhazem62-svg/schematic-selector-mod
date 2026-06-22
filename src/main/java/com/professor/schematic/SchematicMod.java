package com.professor.schematic;

import com.professor.schematic.gui.MainScreen;
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

    public static KeyBinding toggleSelectionKey;
    public static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {

        // Toggle selection mode (default: V)
        toggleSelectionKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.schematicmod.toggle_selection",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.schematicmod.general"
        ));

        // Open main menu (default: M)
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.schematicmod.open_save",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.schematicmod.general"
        ));

        // ── Tick events ───────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (toggleSelectionKey.wasPressed()) {
                SelectionManager sel = SelectionManager.getInstance();
                sel.setSelectionMode(!sel.isSelectionMode());
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal(sel.isSelectionMode()
                                    ? "§a[Schematic] §fوضع التحديد: §aMON §7(كليك يسار = Pos1 | كليك يمين = Pos2)"
                                    : "§c[Schematic] §fوضع التحديد: §cOFF"),
                            true);
                }
            }

            while (openMenuKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new MainScreen());
                }
            }
        });

        // ── Attack (left-click) → Pos1 ───────────────────────────────────────
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient) return ActionResult.PASS;
            SelectionManager sel = SelectionManager.getInstance();
            if (!sel.isSelectionMode()) return ActionResult.PASS;
            sel.setPos1(pos);
            player.sendMessage(Text.literal(String.format(
                    "§b[Schematic] §fPos1: §e(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())), true);
            return ActionResult.FAIL;
        });

        // ── Use (right-click) → Pos2 ─────────────────────────────────────────
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient) return ActionResult.PASS;
            SelectionManager sel = SelectionManager.getInstance();
            if (!sel.isSelectionMode()) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            sel.setPos2(pos);
            player.sendMessage(Text.literal(String.format(
                    "§b[Schematic] §fPos2: §e(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())), true);
            return ActionResult.FAIL;
        });
    }
}
