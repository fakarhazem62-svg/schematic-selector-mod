package com.professor.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.util.*;

/**
 * Saves selections as Sponge Schematic v2 (.schematic)
 * Compatible with WorldEdit, FAWE, and most modern schematic tools.
 */
public class SchematicWriter {

    public static boolean save(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;

        SelectionManager sel = SelectionManager.getInstance();
        if (!sel.hasSelection()) return false;

        BlockPos min = sel.getMin();
        int w = sel.getWidth();
        int h = sel.getHeight();
        int l = sel.getLength();

        // Build palette: blockState string → palette index
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0); // air always index 0

        // Collect all block states and assign palette indices
        int totalBlocks = w * h * l;
        int[] paletteIndices = new int[totalBlocks];

        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    BlockPos worldPos = min.add(x, y, z);
                    BlockState state = client.world.getBlockState(worldPos);
                    String stateStr = blockStateToString(state);

                    palette.computeIfAbsent(stateStr, k -> palette.size());
                    paletteIndices[idx++] = palette.get(stateStr);
                }
            }
        }

        // Encode BlockData as varint byte array
        byte[] blockData = encodeVarintArray(paletteIndices);

        // Build Palette NBT compound
        NbtCompound paletteNbt = new NbtCompound();
        for (Map.Entry<String, Integer> entry : palette.entrySet()) {
            paletteNbt.putInt(entry.getKey(), entry.getValue());
        }

        // Build root compound (Sponge Schematic v2)
        NbtCompound root = new NbtCompound();
        root.putShort("Version", (short) 2);
        root.putInt("DataVersion", 3953); // Minecraft 1.21.1
        root.putShort("Width", (short) w);
        root.putShort("Height", (short) h);
        root.putShort("Length", (short) l);
        root.put("Offset", new NbtIntArray(new int[]{0, 0, 0}));
        root.putInt("PaletteMax", palette.size());
        root.put("Palette", paletteNbt);
        root.put("BlockData", new NbtByteArray(blockData));
        root.put("BlockEntities", new NbtList());
        root.put("Entities", new NbtList());

        // Wrap in "Schematic" key (required by Sponge format)
        NbtCompound wrapper = new NbtCompound();
        wrapper.put("Schematic", root);

        // Save to .schematic file
        File schematicsDir = new File(client.runDirectory, "schematics");
        if (!schematicsDir.exists()) schematicsDir.mkdirs();

        String safeName = name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (safeName.isEmpty()) safeName = "schematic";
        File outFile = new File(schematicsDir, safeName + ".schem");

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            NbtIo.writeCompressed(wrapper, fos);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Converts a BlockState to its string representation.
     * Example: "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
     */
    private static String blockStateToString(BlockState state) {
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        Collection<Property<?>> props = state.getProperties();
        if (props.isEmpty()) return blockId;

        StringBuilder sb = new StringBuilder(blockId).append('[');
        boolean first = true;
        for (Property<?> prop : props) {
            if (!first) sb.append(',');
            first = false;
            sb.append(prop.getName()).append('=').append(getPropValue(state, prop));
        }
        sb.append(']');
        return sb.toString();
    }

    private static <T extends Comparable<T>> String getPropValue(BlockState state, Property<T> prop) {
        return prop.name(state.get(prop));
    }

    /**
     * Encodes an int array as varints into a byte array.
     */
    private static byte[] encodeVarintArray(int[] values) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (int value : values) {
            while ((value & ~0x7F) != 0) {
                buf.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            buf.write(value);
        }
        return buf.toByteArray();
    }
}
