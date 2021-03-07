package fi.dy.masa.litematica.schematic.printer;

import com.github.lunatrius.core.util.math.BlockPosHelper;
import com.github.lunatrius.core.util.math.MBlockPos;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.Reference;
import fi.dy.masa.litematica.block.state.BlockStateHelper;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.reference.Constants;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.printer.nbtsync.NBTSync;
import fi.dy.masa.litematica.schematic.printer.nbtsync.SyncRegistry;
import fi.dy.masa.litematica.schematic.printer.registry.PlacementData;
import fi.dy.masa.litematica.schematic.printer.registry.PlacementRegistry;
import fi.dy.masa.litematica.schematic.util.BlockStateToItemStack;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fluids.BlockFluidBase;

import java.util.*;

public class SchematicPrinter {
    public static final SchematicPrinter INSTANCE = new SchematicPrinter();
    public static float partialTicks = 0;

    private final Minecraft minecraft = Minecraft.getMinecraft();

    private boolean isEnabled = true;
    private boolean isPrinting = false;

    private WorldSchematic schematicWorld = null;
    private LitematicaSchematic schematic = null;
    private byte[][][] timeout = null;
    private HashMap<BlockPos, Integer> syncBlacklist = new HashMap<BlockPos, Integer>();

    private int schemPosX;
    private int schemPosY;
    private int schemPosZ;

    public boolean isEnabled() {
        return this.isEnabled;
    }

    public void setEnabled(final boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    public boolean togglePrinting() {
        this.isPrinting = !this.isPrinting && this.schematic != null;
        return this.isPrinting;
    }

    public boolean isPrinting() {
        return this.isPrinting;
    }

    public void setPrinting(final boolean isPrinting) {
        this.isPrinting = isPrinting;
    }

    public WorldSchematic getSchematicWorld() {
        return this.schematicWorld;
    }
    public LitematicaSchematic getSchematic() { return schematic; }

    public void setSchematic(final WorldSchematic schematicWorld, final LitematicaSchematic schematic) {

        schemPosX = Integer.MAX_VALUE;
        schemPosY = Integer.MAX_VALUE;
        schemPosZ = Integer.MAX_VALUE;

        for (BlockPos pos: schematic.getAreaPositions().values()) { // I have no idea if this works lmao
            if(pos.getX() < schemPosX) schemPosX = pos.getX();
            if(pos.getY() < schemPosY) schemPosY = pos.getY();
            if(pos.getZ() < schemPosZ) schemPosZ = pos.getZ();
        }

        this.isPrinting = false;
        this.schematicWorld = schematicWorld;
        this.schematic = schematic;
        refresh();
    }



    public void refresh() {
        if (this.schematic != null) {
            this.timeout = new byte[this.schematic.getTotalSize().getX()][this.schematic.getTotalSize().getY()][this.schematic.getTotalSize().getZ()];
        } else {
            this.timeout = null;
        }
        this.syncBlacklist.clear();
    }

    public boolean print(final WorldClient world, final EntityPlayerSP player) {
        final double dX = (player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks) - schemPosX;
        final double dY = (player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks) - schemPosY;
        final double dZ = (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks) - schemPosZ;
        final int x = (int) Math.floor(dX);
        final int y = (int) Math.floor(dY);
        final int z = (int) Math.floor(dZ);
        final int range = Configs.Generic.PLACE_DISTANCE.getIntegerValue();

        final int minX = Math.max(0, x - range);
        final int maxX = Math.min(this.schematic.getTotalSize().getX() - 1, x + range);
        int minY = Math.max(0, y - range);
        int maxY = Math.min(this.schematic.getTotalSize().getY() - 1, y + range);
        final int minZ = Math.max(0, z - range);
        final int maxZ = Math.min(this.schematic.getTotalSize().getZ() - 1, z + range);

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return false;
        }

        final int slot = player.inventory.currentItem;
        final boolean isSneaking = player.isSneaking();

        /*switch (schematic.layerMode) {
        case ALL: break;
        case SINGLE_LAYER:
            if (schematic.renderingLayer > maxY) {
                return false;
            }
            maxY = schematic.renderingLayer;
            //$FALL-THROUGH$
        case ALL_BELOW:
            if (schematic.renderingLayer < minY) {
                return false;
            }
            maxY = schematic.renderingLayer;
            break;
        }*/

        syncSneaking(player, true);

        final double blockReachDistance = this.minecraft.playerController.getBlockReachDistance() - 0.1;
        final double blockReachDistanceSq = blockReachDistance * blockReachDistance;
        for (final MBlockPos pos : BlockPosHelper.getAllInBoxXZY(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (pos.distanceSqToCenter(dX, dY, dZ) > blockReachDistanceSq) {
                continue;
            }

            try {
                if (placeBlock(world, player, pos)) {
                    return syncSlotAndSneaking(player, slot, isSneaking, true);
                }
            } catch (final Exception e) {
                Litematica.logger.error("Could not place block!", e);
                return syncSlotAndSneaking(player, slot, isSneaking, false);
            }
        }

        return syncSlotAndSneaking(player, slot, isSneaking, true);
    }

    private boolean syncSlotAndSneaking(final EntityPlayerSP player, final int slot, final boolean isSneaking, final boolean success) {
        player.inventory.currentItem = slot;
        syncSneaking(player, isSneaking);
        return success;
    }

    private boolean placeBlock(final WorldClient world, final EntityPlayerSP player, final BlockPos pos) {
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();
        if (this.timeout[x][y][z] > 0) {
            this.timeout[x][y][z]--;
            return false;
        }

        final int wx = this.schemPosX + x;
        final int wy = this.schemPosY + y;
        final int wz = this.schemPosZ + z;
        final BlockPos realPos = new BlockPos(wx, wy, wz);

        final IBlockState blockState = this.schematicWorld.getBlockState(pos);
        final IBlockState realBlockState = world.getBlockState(realPos);
        final Block realBlock = realBlockState.getBlock();

        if (BlockStateHelper.areBlockStatesEqual(blockState, realBlockState)) {
            // TODO: clean up this mess
            final NBTSync handler = SyncRegistry.INSTANCE.getHandler(realBlock);
            if (handler != null) {
                this.timeout[x][y][z] = (byte) Configs.Generic.TIMEOUT.getIntegerValue();

                Integer tries = this.syncBlacklist.get(realPos);
                if (tries == null) {
                    tries = 0;
                } else if (tries >= 10) {
                    return false;
                }

                Litematica.logger.trace("Trying to sync block at {} {}", realPos, tries);
                final boolean success = handler.execute(player, this.schematicWorld, pos, world, realPos);
                if (success) {
                    this.syncBlacklist.put(realPos, tries + 1);
                }

                return success;
            }

            return false;
        }

        if (Configs.Generic.DESTROY_BLOCKS.getBooleanValue() && !world.isAirBlock(realPos) && this.minecraft.playerController.isInCreativeMode()) {
            this.minecraft.playerController.clickBlock(realPos, EnumFacing.DOWN);

            this.timeout[x][y][z] = (byte) Configs.Generic.TIMEOUT.getIntegerValue();

            return !Configs.Generic.INSTANTLY_BREAK.getBooleanValue();
        }

        if (this.schematicWorld.isAirBlock(pos)) {
            return false;
        }

        if (!realBlock.isReplaceable(world, realPos)) {
            return false;
        }

        final ItemStack itemStack = BlockStateToItemStack.getItemStack(blockState, new RayTraceResult(player), this.schematicWorld, pos, player);
        if (itemStack.isEmpty()) {
            Litematica.logger.debug("{} is missing a mapping!", blockState);
            return false;
        }

        if (placeBlock(world, player, realPos, blockState, itemStack)) {
            this.timeout[x][y][z] = (byte) Configs.Generic.TIMEOUT.getIntegerValue();

            if (!Configs.Generic.INSTANTLY_PLACE.getBooleanValue()) {
                return true;
            }
        }

        return false;
    }

    private boolean isSolid(final World world, final BlockPos pos, final EnumFacing side) {
        final BlockPos offset = pos.offset(side);

        final IBlockState blockState = world.getBlockState(offset);
        final Block block = blockState.getBlock();

        if (block == null) {
            return false;
        }

        if (block.isAir(blockState, world, offset)) {
            return false;
        }

        if (block instanceof BlockFluidBase) {
            return false;
        }

        if (block.isReplaceable(world, offset)) {
            return false;
        }

        return true;
    }

    private List<EnumFacing> getSolidSides(final World world, final BlockPos pos) {
        if (!Configs.Generic.PLACE_ADJACENT.getBooleanValue()) {
            return Arrays.asList(EnumFacing.VALUES);
        }

        final List<EnumFacing> list = new ArrayList<EnumFacing>();

        for (final EnumFacing side : EnumFacing.VALUES) {
            if (isSolid(world, pos, side)) {
                list.add(side);
            }
        }

        return list;
    }

    private boolean placeBlock(final WorldClient world, final EntityPlayerSP player, final BlockPos pos, final IBlockState blockState, final ItemStack itemStack) {
        if (itemStack.getItem() instanceof ItemBucket) {
            return false;
        }

        final PlacementData data = PlacementRegistry.INSTANCE.getPlacementData(blockState, itemStack);
        if (data != null && !data.isValidPlayerFacing(blockState, player, pos, world)) {
            return false;
        }

        final List<EnumFacing> solidSides = getSolidSides(world, pos);

        if (solidSides.size() == 0) {
            return false;
        }

        final EnumFacing direction;
        final float offsetX;
        final float offsetY;
        final float offsetZ;
        final int extraClicks;

        if (data != null) {
            final List<EnumFacing> validDirections = data.getValidBlockFacings(solidSides, blockState);
            if (validDirections.size() == 0) {
                return false;
            }

            direction = validDirections.get(0);
            offsetX = data.getOffsetX(blockState);
            offsetY = data.getOffsetY(blockState);
            offsetZ = data.getOffsetZ(blockState);
            extraClicks = data.getExtraClicks(blockState);
        } else {
            direction = solidSides.get(0);
            offsetX = 0.5f;
            offsetY = 0.5f;
            offsetZ = 0.5f;
            extraClicks = 0;
        }

        if (!swapToItem(player.inventory, itemStack)) {
            return false;
        }

        return placeBlock(world, player, pos, direction, offsetX, offsetY, offsetZ, extraClicks);
    }

    private boolean placeBlock(final WorldClient world, final EntityPlayerSP player, final BlockPos pos, final EnumFacing direction, final float offsetX, final float offsetY, final float offsetZ, final int extraClicks) {
        final EnumHand hand = EnumHand.MAIN_HAND;
        final ItemStack itemStack = player.getHeldItem(hand);
        boolean success = false;

        if (!this.minecraft.playerController.isInCreativeMode() && !itemStack.isEmpty() && itemStack.getCount() <= extraClicks) {
            return false;
        }

        final BlockPos offset = pos.offset(direction);
        final EnumFacing side = direction.getOpposite();
        final Vec3d hitVec = new Vec3d(offset.getX() + offsetX, offset.getY() + offsetY, offset.getZ() + offsetZ);

        success = placeBlock(world, player, itemStack, offset, side, hitVec, hand);
        for (int i = 0; success && i < extraClicks; i++) {
            success = placeBlock(world, player, itemStack, offset, side, hitVec, hand);
        }

        if (itemStack.getCount() == 0 && success) {
            player.inventory.mainInventory.set(player.inventory.currentItem, ItemStack.EMPTY);
        }

        return success;
    }

    private boolean placeBlock(final WorldClient world, final EntityPlayerSP player, final ItemStack itemStack, final BlockPos pos, final EnumFacing side, final Vec3d hitVec, final EnumHand hand) {
        // FIXME: where did this event go?
        /*
        if (ForgeEventFactory.onPlayerInteract(player, Action.RIGHT_CLICK_BLOCK, world, pos, side, hitVec).isCanceled()) {
            return false;
        }
        */

        // FIXME: when an adjacent block is not required the blocks should be placed 1 block away from the actual position (because air is replaceable)
        final BlockPos actualPos = Configs.Generic.PLACE_ADJACENT.getBooleanValue() ? pos : pos.offset(side);
        final EnumActionResult result = this.minecraft.playerController.processRightClickBlock(player, world, actualPos, side, hitVec, hand);
        if ((result != EnumActionResult.SUCCESS)) {
            return false;
        }

        player.swingArm(hand);
        return true;
    }

    private void syncSneaking(final EntityPlayerSP player, final boolean isSneaking) {
        player.setSneaking(isSneaking);
        player.connection.sendPacket(new CPacketEntityAction(player, isSneaking ? CPacketEntityAction.Action.START_SNEAKING : CPacketEntityAction.Action.STOP_SNEAKING));
    }

    private boolean swapToItem(final InventoryPlayer inventory, final ItemStack itemStack) {
        return swapToItem(inventory, itemStack, true);
    }

    private boolean swapToItem(final InventoryPlayer inventory, final ItemStack itemStack, final boolean swapSlots) {
        final int slot = getInventorySlotWithItem(inventory, itemStack);

        if (this.minecraft.playerController.isInCreativeMode() && (slot < Constants.Inventory.InventoryOffset.HOTBAR || slot >= Constants.Inventory.InventoryOffset.HOTBAR + Constants.Inventory.Size.HOTBAR) && Configs.swapSlotsQueue.size() > 0) {
            inventory.currentItem = getNextSlot();
            inventory.setInventorySlotContents(inventory.currentItem, itemStack.copy());
            this.minecraft.playerController.sendSlotPacket(inventory.getStackInSlot(inventory.currentItem), Constants.Inventory.SlotOffset.HOTBAR + inventory.currentItem);
            return true;
        }

        if (slot >= Constants.Inventory.InventoryOffset.HOTBAR && slot < Constants.Inventory.InventoryOffset.HOTBAR + Constants.Inventory.Size.HOTBAR) {
            inventory.currentItem = slot;
            return true;
        } else if (swapSlots && slot >= Constants.Inventory.InventoryOffset.INVENTORY && slot < Constants.Inventory.InventoryOffset.INVENTORY + Constants.Inventory.Size.INVENTORY) {
            if (swapSlots(inventory, slot)) {
                return swapToItem(inventory, itemStack, false);
            }
        }

        return false;
    }

    private int getInventorySlotWithItem(final InventoryPlayer inventory, final ItemStack itemStack) {
        for (int i = 0; i < inventory.mainInventory.size(); i++) {
            if (inventory.mainInventory.get(i).isItemEqual(itemStack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean swapSlots(final InventoryPlayer inventory, final int from) {
        if (Configs.swapSlots.length > 0) {
            final int slot = getNextSlot();

            swapSlots(from, slot);
            return true;
        }

        return false;
    }

    private int getNextSlot() {
        final int slot = Configs.swapSlotsQueue.poll() % Constants.Inventory.Size.HOTBAR;
        Configs.swapSlotsQueue.offer(slot);
        return slot;
    }

    private boolean swapSlots(final int from, final int to) {
        return this.minecraft.playerController.windowClick(this.minecraft.player.inventoryContainer.windowId, from, to, ClickType.SWAP, this.minecraft.player) == ItemStack.EMPTY;
    }
}
