package fi.dy.masa.litematica.schematic.printer.registry;

import net.minecraft.block.state.IBlockState;

public interface IOffset {
    float getOffset(IBlockState blockState);
}
