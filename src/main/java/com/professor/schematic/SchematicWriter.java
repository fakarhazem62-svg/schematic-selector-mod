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
 * Saves selections as Litematica Schematic (.litematic) v6.
 * Compatible with Litematica mod — works entirely client-side.
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
        int total = w * h * l;

        // ── Build palette ────────────────────────────────────────────────────
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> paletteMap = new LinkedHashMap<>();

        // Air is always index 0
        BlockState airState = net.minecraft.block.Blocks.AIR.getDefaultState();
        palette.add(airState);
        paletteMap.put(airState, 0);

        // Collect states in x/z/y order (Litematica order: x inner, z mid, y outer)
        int[] indices = new int[total];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    BlockState state = client.world.getBlockState(min.add(x, y, z));
                    int i = paletteMap.computeIfAbsent(state, s -> {
                        palette.add(s);
                        return palette.size() - 1;
                    });
                    indices[idx++] = i;
                }
            }
        }

        // ── Pack block states into long array ────────────────────────────────
        int bitsPerEntry = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long[] packedData = packBits(indices, bitsPerEntry);

        // ── Build palette NBT list ───────────────────────────────────────────
        NbtList paletteNbt = new NbtList();
        for (BlockState state : palette) {
            paletteNbt.add(blockStateToNbt(state));
        }

        // ── Build region compound ────────────────────────────────────────────
        NbtCompound position = new NbtCompound();
        position.putInt("x", 0);
        position.putInt("y", 0);
        position.putInt("z", 0);

        NbtCompound size = new NbtCompound();
        size.putInt("x", w);
        size.putInt("y", h);
        size.putInt("z", l);

        NbtCompound region = new NbtCompound();
        region.put("Position", position);
        region.put("Size", size);
        region.put("BlockStatePalette", paletteNbt);
        region.put("BlockStates", new NbtLongArray(packedData));
        region.put("Entities", new NbtList());
        region.put("TileEntities", new NbtList());
        region.put("PendingBlockTicks", new NbtList());
        region.put("PendingFluidTicks", new NbtList());

        NbtCompound regions = new NbtCompound();
        regions.put("main", region);

        // ── Build metadata ───────────────────────────────────────────────────
        NbtCompound enclosingSize = new NbtCompound();
        enclosingSize.putInt("x", w);
        enclosingSize.putInt("y", h);
        enclosingSize.putInt("z", l);

        long now = System.currentTimeMillis();
        NbtCompound metadata = new NbtCompound();
        metadata.put("EnclosingSize", enclosingSize);
        metadata.putString("Author", "SchematicSelector");
        metadata.putString("Description", "");
        metadata.putString("Name", name);
        metadata.putLong("TimeCreated", now);
        metadata.putLong("TimeModified", now);
        metadata.putInt("RegionCount", 1);

        // ── Build root compound ──────────────────────────────────────────────
        NbtCompound root = new NbtCompound();
        root.putInt("Version", 6);
        root.putInt("MinecraftDataVersion", 3953); // 1.21.1
        root.put("Metadata", metadata);
        root.put("Regions", regions);

        // ── Save file ────────────────────────────────────────────────────────
        File dir = new File(client.runDirectory, "schematics");
        if (!dir.exists()) dir.mkdirs();

        String safeName = name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (safeName.isEmpty()) safeName = "schematic";
        File outFile = new File(dir, safeName + ".litematic");

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            NbtIo.writeCompressed(root, fos);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Converts a BlockState to its Litematica palette NBT entry. */
    private static NbtCompound blockStateToNbt(BlockState state) {
        NbtCompound entry = new NbtCompound();
        entry.putString("Name", Registries.BLOCK.getId(state.getBlock()).toString());

        Collection<Property<?>> props = state.getProperties();
        if (!props.isEmpty()) {
            NbtCompound propsNbt = new NbtCompound();
            for (Property<?> prop : props) {
                propsNbt.putString(prop.getName(), getPropValue(state, prop));
            }
            entry.put("Properties", propsNbt);
        }
        return entry;
    }

    private static <T extends Comparable<T>> String getPropValue(BlockState state, Property<T> prop) {
        return prop.name(state.get(prop));
    }

    /**
     * Packs int indices into a long array (Litematica bit-packing).
     * Indices span across long boundaries.
     */
    private static long[] packBits(int[] indices, int bitsPerEntry) {
        int totalBits = indices.length * bitsPerEntry;
        long[] longs = new long[(totalBits + 63) / 64];
        long mask = (1L << bitsPerEntry) - 1L;

        for (int i = 0; i < indices.length; i++) {
            int bitStart = i * bitsPerEntry;
            int longIdx  = bitStart >> 6;        // / 64
            int bitOff   = bitStart & 63;        // % 64

            longs[longIdx] |= ((long) indices[i] & mask) << bitOff;

            int bitsWritten = 64 - bitOff;
            if (bitsWritten < bitsPerEntry && longIdx + 1 < longs.length) {
                longs[longIdx + 1] |= ((long) indices[i] & mask) >> bitsWritten;
            }
        }
        return longs;
    }
}
