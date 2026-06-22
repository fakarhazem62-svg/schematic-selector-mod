package com.professor.schematic;

import net.minecraft.util.math.BlockPos;

public class SelectionManager {

    private static final SelectionManager INSTANCE = new SelectionManager();

    private BlockPos pos1 = null;
    private BlockPos pos2 = null;
    private boolean selectionMode = false;

    private SelectionManager() {}

    public static SelectionManager getInstance() {
        return INSTANCE;
    }

    public boolean isSelectionMode() { return selectionMode; }
    public void setSelectionMode(boolean v) { selectionMode = v; }

    public BlockPos getPos1() { return pos1; }
    public BlockPos getPos2() { return pos2; }

    public void setPos1(BlockPos pos) { this.pos1 = pos; }
    public void setPos2(BlockPos pos) { this.pos2 = pos; }

    public boolean hasSelection() { return pos1 != null && pos2 != null; }

    public BlockPos getMin() {
        if (!hasSelection()) return null;
        return new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );
    }

    public BlockPos getMax() {
        if (!hasSelection()) return null;
        return new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );
    }

    public int getWidth()  { return hasSelection() ? getMax().getX() - getMin().getX() + 1 : 0; }
    public int getHeight() { return hasSelection() ? getMax().getY() - getMin().getY() + 1 : 0; }
    public int getLength() { return hasSelection() ? getMax().getZ() - getMin().getZ() + 1 : 0; }
}
