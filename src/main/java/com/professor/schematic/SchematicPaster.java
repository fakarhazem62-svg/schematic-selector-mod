package com.professor.schematic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public class SchematicPaster {

    /**
     * Pastes the schematic at the player's current position.
     * @param data      the loaded schematic
     * @param replacements  map of original blockId → replacement blockId
     * @return number of blocks placed, or -1 on error
     */
    public static int paste(SchematicData data, Map<String, String> replacements) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return -1;

        BlockPos origin = client.player.getBlockPos();
        int placed = 0;

        for (int y = 0; y < data.height; y++) {
            for (int z = 0; z < data.length; z++) {
                for (int x = 0; x < data.width; x++) {
                    int idx = x + z * data.width + y * data.width * data.length;
                    if (idx >= data.indices.length) continue;

                    int paletteIdx = data.indices[idx];
                    if (paletteIdx >= data.palette.size()) continue;

                    BlockState original = data.palette.get(paletteIdx);
                    String blockId = Registries.BLOCK.getId(original.getBlock()).toString();

                    // Skip air
                    if (blockId.equals("minecraft:air")) continue;

                    // Apply block replacement
                    BlockState finalState = original;
                    if (replacements.containsKey(blockId)) {
                        String newId = replacements.get(blockId).trim();
                        if (!newId.isEmpty()) {
                            Block newBlock = Registries.BLOCK.get(Identifier.of(newId));
                            if (newBlock != null && newBlock != Blocks.AIR) {
                                finalState = newBlock.getDefaultState();
                            }
                        }
                    }

                    BlockPos target = origin.add(x, y, z);

                    // Place block (works in single player; creative on server)
                    try {
                        client.world.setBlockState(target, finalState,
                                Block.NOTIFY_ALL | Block.FORCE_STATE);
                        placed++;
                    } catch (Exception ignored) {}
                }
            }
        }
        return placed;
    }
}
