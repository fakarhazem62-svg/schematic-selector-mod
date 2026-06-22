package com.professor.schematic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.io.*;
import java.util.*;

public class SchematicReader {

    /** Returns null on failure. */
    public static SchematicData read(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            NbtCompound root = NbtIo.readCompressed(fis, NbtSizeTracker.ofUnlimitedBytes());

            // Litematica wraps everything at root level
            int w = 0, h = 0, l = 0;
            List<BlockState> palette = new ArrayList<>();
            int[] indices = null;

            NbtCompound regions = root.getCompound("Regions");
            String regionKey = regions.getKeys().isEmpty() ? null : regions.getKeys().iterator().next();
            if (regionKey == null) return null;

            NbtCompound region = regions.getCompound(regionKey);
            NbtCompound size = region.getCompound("Size");
            w = Math.abs(size.getInt("x"));
            h = Math.abs(size.getInt("y"));
            l = Math.abs(size.getInt("z"));
            int total = w * h * l;

            // Read palette
            NbtList paletteList = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < paletteList.size(); i++) {
                NbtCompound entry = paletteList.getCompound(i);
                BlockState state = parseBlockState(entry);
                palette.add(state);
            }

            // Decode packed long array
            long[] packed = region.getLongArray("BlockStates");
            int bitsPerEntry = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            indices = unpackBits(packed, bitsPerEntry, total);

            // Count blocks
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (int idx : indices) {
                if (idx < palette.size()) {
                    BlockState state = palette.get(idx);
                    String id = Registries.BLOCK.getId(state.getBlock()).toString();
                    if (!id.equals("minecraft:air")) {
                        counts.merge(id, 1, Integer::sum);
                    }
                }
            }

            String name = file.getName().replace(".litematic", "");
            return new SchematicData(name, w, h, l, palette, indices, counts);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static BlockState parseBlockState(NbtCompound entry) {
        String name = entry.getString("Name");
        Block block = Registries.BLOCK.get(Identifier.of(name));
        if (block == null) return Blocks.AIR.getDefaultState();

        BlockState state = block.getDefaultState();
        if (!entry.contains("Properties")) return state;

        NbtCompound props = entry.getCompound("Properties");
        for (String key : props.getKeys()) {
            Property<?> prop = block.getStateManager().getProperty(key);
            if (prop != null) {
                state = applyProperty(state, prop, props.getString(key));
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(
            BlockState state, Property<T> prop, String value) {
        Optional<T> val = prop.parse(value);
        return val.map(v -> state.with(prop, v)).orElse(state);
    }

    private static int[] unpackBits(long[] packed, int bitsPerEntry, int count) {
        int[] result = new int[count];
        long mask = (1L << bitsPerEntry) - 1L;
        for (int i = 0; i < count; i++) {
            int bitStart = i * bitsPerEntry;
            int longIdx  = bitStart >> 6;
            int bitOff   = bitStart & 63;
            long val = (packed[longIdx] >>> bitOff) & mask;
            int bitsRead = 64 - bitOff;
            if (bitsRead < bitsPerEntry && longIdx + 1 < packed.length) {
                val |= (packed[longIdx + 1] << bitsRead) & mask;
            }
            result[i] = (int) val;
        }
        return result;
    }
}
