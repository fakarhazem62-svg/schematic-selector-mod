package com.professor.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchematicWriter {

    /**
     * Saves the current selection as a vanilla Minecraft Structure NBT file (.nbt).
     * Compatible with structure blocks and most schematic tools.
     *
     * @param name file name (without extension)
     * @return true on success
     */
    public static boolean save(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;

        SelectionManager sel = SelectionManager.getInstance();
        if (!sel.hasSelection()) return false;

        BlockPos min = sel.getMin();
        BlockPos max = sel.getMax();
        int w = sel.getWidth();
        int h = sel.getHeight();
        int l = sel.getLength();

        // Build palette and block list
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        NbtList blockList = new NbtList();

        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    BlockPos worldPos = min.add(x, y, z);
                    BlockState state = client.world.getBlockState(worldPos);

                    int idx = paletteIndex.computeIfAbsent(state, s -> {
                        palette.add(s);
                        return palette.size() - 1;
                    });

                    NbtCompound entry = new NbtCompound();
                    NbtList posList = new NbtList();
                    posList.add(NbtInt.of(x));
                    posList.add(NbtInt.of(y));
                    posList.add(NbtInt.of(z));
                    entry.put("pos", posList);
                    entry.putInt("state", idx);
                    blockList.add(entry);
                }
            }
        }

        // Build palette NBT
        NbtList paletteNbt = new NbtList();
        for (BlockState state : palette) {
            paletteNbt.add(BlockState.CODEC
                    .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, state)
                    .result()
                    .orElseGet(NbtCompound::new));
        }

        // Build size NBT
        NbtList sizeNbt = new NbtList();
        sizeNbt.add(NbtInt.of(w));
        sizeNbt.add(NbtInt.of(h));
        sizeNbt.add(NbtInt.of(l));

        // Root compound
        NbtCompound root = new NbtCompound();
        root.put("size", sizeNbt);
        root.put("palette", paletteNbt);
        root.put("blocks", blockList);
        root.put("entities", new NbtList());
        root.putInt("DataVersion", 3953); // 1.21.1

        // Save to file
        File schematicsDir = new File(client.runDirectory, "schematics");
        if (!schematicsDir.exists()) schematicsDir.mkdirs();

        String safeName = name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (safeName.isEmpty()) safeName = "schematic";
        File outFile = new File(schematicsDir, safeName + ".nbt");

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            NbtIo.writeCompressed(root, fos);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
