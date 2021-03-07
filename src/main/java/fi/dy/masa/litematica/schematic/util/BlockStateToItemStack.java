package fi.dy.masa.litematica.schematic.util;

import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.world.WorldSchematic;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

@MethodsReturnNonnullByDefault
public class BlockStateToItemStack {
    public static ItemStack getItemStack(final IBlockState blockState, final RayTraceResult rayTraceResult, final WorldSchematic world, final BlockPos pos, final EntityPlayer player) {
        final Block block = blockState.getBlock();

        try {
            final ItemStack itemStack = block.getPickBlock(blockState, rayTraceResult, world, pos, player);
            if (!itemStack.isEmpty()) {
                return itemStack;
            }
        } catch (final Exception e) {
            Litematica.logger.debug("Could not get the pick block for: {}", blockState, e);
        }

        return ItemStack.EMPTY;
    }
}
