package com.professor.schematic;

import net.minecraft.block.BlockState;
import java.util.List;
import java.util.Map;

/** Holds a loaded schematic in memory. */
public class SchematicData {
    public final String name;
    public final int width, height, length;
    public final List<BlockState> palette;   // index → BlockState
    public final int[] indices;              // x + z*w + y*w*l → palette index
    public final Map<String, Integer> blockCounts; // blockId → count

    public SchematicData(String name, int w, int h, int l,
                         List<BlockState> palette, int[] indices,
                         Map<String, Integer> blockCounts) {
        this.name = name;
        this.width = w;
        this.height = h;
        this.length = l;
        this.palette = palette;
        this.indices = indices;
        this.blockCounts = blockCounts;
    }

    public int totalBlocks() { return width * height * length; }
}
