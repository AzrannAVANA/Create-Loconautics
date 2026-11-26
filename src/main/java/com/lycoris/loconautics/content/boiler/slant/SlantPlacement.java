package com.lycoris.loconautics.content.boiler.slant;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.boiler.BoilerAttach;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.content.boiler.barrel.BarrelPipeConnector;
import com.lycoris.loconautics.content.smokestack.SmokestackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

// both sides
public final class SlantPlacement {
    public static final TagKey<Block> SLANTABLE = TagKey.create(Registries.BLOCK,
            LoconauticsConstants.id("slantable"));

    // blocks out from the center on both axes where the octagon turns slanted
    private static final double SLANT_FROM = 0.43;

    private SlantPlacement() {
    }

    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem item)) return;
        BlockState probe = item.getBlock().defaultBlockState();
        if (!probe.is(SLANTABLE) || !probe.hasProperty(SlantSide.SLANT)) return;
        Level level = event.getLevel();
        BlockHitResult hit = event.getHitVec();
        BlockPos host = hit.getBlockPos();
        if (stackOn(event, level, item, host)) return;
        Direction.Axis length = hostAxis(level.getBlockState(host));
        if (length == null) return;
        Direction lean = leanAt(length, hit.getLocation().subtract(Vec3.atLowerCornerOf(host)));
        if (lean == null) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!level.isClientSide) place(level, event.getEntity(), event.getHand(), stack, item, host, lean);
    }

    private static boolean stackOn(PlayerInteractEvent.RightClickBlock event, Level level, BlockItem item,
                                   BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        if (!(item.getBlock() instanceof SmokestackBlock) || !(state.getBlock() instanceof SmokestackBlock)
                || !state.getValue(SlantSide.SLANT).isSlanted()) {
            return false;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) return true;

        SlantSide side = state.getValue(SlantSide.SLANT);
        Direction lean = side.lean();
        BlockPos top = clicked;
        int height = 1;
        while (inColumn(level, SmokestackBlock.stepUp(top, lean), side)) {
            top = SmokestackBlock.stepUp(top, lean);
            height++;
        }
        for (BlockPos below = SmokestackBlock.stepDown(clicked, lean); inColumn(level, below, side);
             below = SmokestackBlock.stepDown(below, lean)) {
            height++;
        }
        BlockPos at = SmokestackBlock.stepUp(top, lean);
        if (height >= SmokestackBlock.maxHeight() || !level.getBlockState(at).canBeReplaced()) return true;

        BlockState next = item.getBlock().defaultBlockState().setValue(SlantSide.SLANT, side);
        level.setBlock(at, next, Block.UPDATE_ALL);
        item.getBlock().setPlacedBy(level, at, next, event.getEntity(), event.getItemStack());
        level.playSound(null, at, next.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 0.8f);
        if (!event.getEntity().getAbilities().instabuild) event.getItemStack().shrink(1);
        return true;
    }

    private static boolean inColumn(Level level, BlockPos pos, SlantSide side) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SmokestackBlock && state.getValue(SlantSide.SLANT) == side;
    }

    @Nullable
    public static Direction.Axis hostAxis(BlockState state) {
        if (!BoilerAttach.isHost(state)) return null;
        Direction.Axis axis = state.is(BoilerTags.BOILER_FIREBOX)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis()
                : BarrelPipeConnector.bodyAxis(state);
        return axis == null || axis == Direction.Axis.Y ? null : axis;
    }

    @Nullable
    private static Direction leanAt(Direction.Axis length, Vec3 local) {
        Direction.Axis across = length == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        double out = across.choose(local.x, local.y, local.z) - 0.5;
        double high = local.y - 0.5;
        if (Math.abs(out) < SLANT_FROM || high < SLANT_FROM) return null;
        return Direction.fromAxisAndDirection(across,
                out > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    private static void place(Level level, Player player, InteractionHand hand, ItemStack stack,
                              BlockItem item, BlockPos host, Direction lean) {
        BlockPos at = host.relative(lean).above();
        if (!level.getBlockState(at).canBeReplaced()) return;
        BlockHitResult onTop = new BlockHitResult(Vec3.atCenterOf(host.above()), Direction.UP, host, false);
        BlockState part = item.getBlock().getStateForPlacement(new BlockPlaceContext(player, hand, stack, onTop));
        if (part == null) return;
        part = part.setValue(SlantSide.SLANT, SlantSide.of(lean));
        for (Property<?> property : part.getProperties()) {
            if (property instanceof BooleanProperty flag && flag.getName().equals("raised")) {
                part = part.setValue(flag, true);
            }
        }

        level.setBlock(at, part, Block.UPDATE_ALL);
        item.getBlock().setPlacedBy(level, at, part, player, stack);
        level.playSound(null, at, part.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 0.8f);
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }
}
