package com.lycoris.loconautics.trainphysics.portal;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.IItemHandler;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class PortalTwinInteraction {

    private static boolean forwarding;

    private PortalTwinInteraction() {
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (forwarding || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PortalTwins.MasterBlock target = PortalTwins.masterBlock(level, event.getPos());
        if (target == null) {
            return;
        }
        event.setCanceled(true);
        if (target.level().getBlockState(target.pos()).getBlock() instanceof SeatBlock) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        BlockHitResult hit = event.getHitVec();
        Vec3 shift = Vec3.atLowerCornerOf(target.pos().subtract(event.getPos()));
        BlockHitResult moved = new BlockHitResult(hit.getLocation().add(shift), hit.getDirection(), target.pos(),
                hit.isInside());
        // useitemon fires again for the masters block, if that is a twin too it loops forever. found out the hard way
        forwarding = true;
        try {
            event.setCancellationResult(player.gameMode.useItemOn(player, target.level(),
                    player.getItemInHand(event.getHand()), event.getHand(), moved));
        } finally {
            forwarding = false;
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (forwarding || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PortalTwins.MasterBlock target = PortalTwins.masterBlock(level, event.getPos());
        if (target == null) {
            return;
        }
        event.setCanceled(true);
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel masterLevel = target.level();
        BlockPos masterPos = target.pos();
        BlockState state = masterLevel.getBlockState(masterPos);
        if (state.isAir() || !breakAllowed(masterLevel, masterPos, state, player)) {
            return;
        }
        if (player.isCreative()) {
            masterLevel.destroyBlock(masterPos, false, player);
            return;
        }
        ItemStack tool = player.getMainHandItem();
        BlockEntity be = masterLevel.getBlockEntity(masterPos);
        popContentsAtPlayerSide(masterLevel, masterPos, level, event.getPos());
        if (state.canHarvestBlock(masterLevel, masterPos, player)) {
            for (ItemStack drop : Block.getDrops(state, masterLevel, masterPos, be, player, tool)) {
                Block.popResource(level, event.getPos(), drop);
            }
        }
        tool.mineBlock(masterLevel, state, masterPos, player);
        masterLevel.destroyBlock(masterPos, false, player);
    }

    private static boolean breakAllowed(ServerLevel masterLevel, BlockPos masterPos, BlockState state,
                                        ServerPlayer player) {
        forwarding = true;
        try {
            return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(masterLevel, masterPos, state, player))
                    .isCanceled();
        } finally {
            forwarding = false;
        }
    }

    private static void popContentsAtPlayerSide(ServerLevel masterLevel, BlockPos masterPos, ServerLevel level, BlockPos at) {
        IItemHandler handler = masterLevel.getCapability(Capabilities.ItemHandler.BLOCK, masterPos, null);
        if (handler == null) {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack held = handler.getStackInSlot(slot);
            if (held.isEmpty()) {
                continue;
            }
            ItemStack taken = handler.extractItem(slot, held.getCount(), false);
            if (!taken.isEmpty()) {
                Block.popResource(level, at, taken);
            }
        }
    }
}
