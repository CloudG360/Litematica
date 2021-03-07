package fi.dy.masa.litematica.schematic.printer.registry;

import net.minecraft.block.state.IBlockState;

public interface IExtraClick {
    int getExtraClicks(IBlockState blockState);
}
