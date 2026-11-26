package com.lycoris.loconautics.content.smokestack;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.boiler.BoilerShapes;
import com.lycoris.loconautics.content.boiler.BoilerAttach;
import com.lycoris.loconautics.content.boiler.slant.SlantShapes;
import com.lycoris.loconautics.content.boiler.slant.SlantSide;
import com.lycoris.loconautics.foundation.pick.OversizeShape;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SmokestackBlock extends Block implements IWrenchable, EntityBlock, OversizeShape {

    private static final int DEFAULT_MAX_HEIGHT = 6;

    public static final BooleanProperty STACKED = BooleanProperty.create("stacked");
    public static final BooleanProperty RAISED = BooleanProperty.create("raised");
    public static final BooleanProperty SUNK = BooleanProperty.create("sunk");

    private static final VoxelShape CAPPED_SHAPE = Shapes.or(
            Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0),
            Block.box(1.0, 9.0, 1.0, 15.0, 16.0, 15.0));
    private static final VoxelShape COLUMN_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
    private static final VoxelShape CAPPED_RAISED = CAPPED_SHAPE.move(0.0, BoilerAttach.BULGE, 0.0);
    private static final VoxelShape COLUMN_RAISED = COLUMN_SHAPE.move(0.0, BoilerAttach.BULGE, 0.0);

    public SmokestackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STACKED, false).setValue(RAISED, false)
                .setValue(SlantSide.SLANT, SlantSide.NONE).setValue(SUNK, false).setValue(BoilerAttach.LIFTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STACKED, RAISED, SUNK, BoilerAttach.LIFTED, SlantSide.SLANT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean column = state.getValue(STACKED);
        VoxelShape shape = state.getValue(RAISED)
                ? (column ? COLUMN_RAISED : CAPPED_RAISED)
                : (column ? COLUMN_SHAPE : CAPPED_SHAPE);
        SlantSide side = state.getValue(SlantSide.SLANT);
        if (side.isSlanted()) {
            boolean first = state.getValue(RAISED);
            VoxelShape raised = column ? COLUMN_RAISED : CAPPED_RAISED;
            return SlantShapes.tilt(raised, side, first ? SlantShapes.FIRST_BASE : SlantShapes.NEXT_BASE,
                    column ? (first ? SlantShapes.FIRST_REACH : SlantShapes.NEXT_REACH) : 1.0);
        }
        return BoilerShapes.raisedIf(shape, state.getValue(BoilerAttach.LIFTED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return fitted(defaultBlockState().setValue(SUNK, context.isSecondaryUseActive()),
                context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP || direction == Direction.DOWN) {
            return fitted(state, level, pos);
        }
        return state;
    }

    private static BlockState fitted(BlockState state, BlockGetter level, BlockPos pos) {
        SlantSide side = state.getValue(SlantSide.SLANT);
        if (side.isSlanted()) {
            BlockState above = level.getBlockState(stepUp(pos, side.lean()));
            boolean joins = isStack(above) && above.getValue(SlantSide.SLANT) == side;
            return state.setValue(STACKED, joins)
                    .setValue(RAISED, BoilerAttach.isHostAt(level, stepDown(pos, side.lean())));
        }
        BlockPos bottom = columnBottom(level, pos);
        return state.setValue(STACKED, joinsAbove(level, pos))
                .setValue(RAISED, raisedAt(level, pos) && !state.getValue(SUNK))
                .setValue(BoilerAttach.LIFTED, BoilerAttach.liftedOn(level, bottom, Direction.DOWN));
    }

    // the whole column sits out from a boiler when its bottom block stands on one
    private static boolean raisedAt(BlockGetter level, BlockPos pos) {
        return BoilerAttach.raisedOn(level, columnBottom(level, pos), Direction.DOWN);
    }

    private static boolean isStack(BlockState state) {
        return state.getBlock() instanceof SmokestackBlock;
    }

    private static boolean inColumn(BlockState state, SlantSide side) {
        return isStack(state) && state.getValue(SlantSide.SLANT) == side;
    }

    private static SlantSide sideAt(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isStack(state) ? state.getValue(SlantSide.SLANT) : SlantSide.NONE;
    }

    public static BlockPos stepUp(BlockPos pos, @Nullable Direction lean) {
        return lean == null ? pos.above() : pos.relative(lean).above();
    }

    public static BlockPos stepDown(BlockPos pos, @Nullable Direction lean) {
        return lean == null ? pos.below() : pos.relative(lean.getOpposite()).below();
    }

    public static int maxHeight() {
        return LoconauticsConfig.SERVER_SPEC.isLoaded() ? LoconauticsConfig.SMOKESTACK_MAX_HEIGHT.get() : DEFAULT_MAX_HEIGHT;
    }

    private static boolean joinsAbove(BlockGetter level, BlockPos pos) {
        return isStack(level.getBlockState(pos.above())) && heightBelow(level, pos) % maxHeight() != maxHeight() - 1;
    }

    private static int heightBelow(BlockGetter level, BlockPos pos) {
        int below = 0;
        while (isStack(level.getBlockState(pos.below(below + 1)))) {
            below++;
        }
        return below;
    }

    private static void refreshAbove(Level level, BlockPos pos, SlantSide side) {
        if (level.isClientSide) {
            return;
        }
        BlockPos at = stepUp(pos, side.lean());
        BlockState state = level.getBlockState(at);
        while (inColumn(state, side)) {
            BlockState next = fitted(state, level, at);
            if (next != state) {
                level.setBlock(at, next, Block.UPDATE_CLIENTS);
            }
            at = stepUp(at, side.lean());
            state = level.getBlockState(at);
        }
    }

    private static void refreshBelow(Level level, BlockPos pos, SlantSide side) {
        if (level.isClientSide || !side.isSlanted()) {
            return;
        }
        BlockPos at = stepDown(pos, side.lean());
        BlockState state = level.getBlockState(at);
        if (inColumn(state, side)) {
            level.setBlock(at, fitted(state, level, at), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!isStack(newState)) {
            SlantSide side = state.getValue(SlantSide.SLANT);
            refreshAbove(level, pos, side);
            refreshBelow(level, pos, side);
            refreshColumn(level, stepDown(pos, side.lean()));
            refreshColumn(level, stepUp(pos, side.lean()));
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmokestackBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof DyeItem dye) {
            if (!level.isClientSide) {
                int rgb = dye.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF;
                forEachInColumn(level, pos, be -> be.setColor(rgb));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        IPlacementHelper helper = PlacementHelpers.get(SmokestackPlacement.ID);
        if (helper.matchesItem(stack)) {
            return helper.getOffset(player, level, state, pos, hit)
                    .placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hit);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        refreshAbove(level, pos, state.getValue(SlantSide.SLANT));
        refreshBelow(level, pos, state.getValue(SlantSide.SLANT));
        adoptColumnColor(level, pos);
        refreshColumn(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        refreshColumn(level, pos);
    }

    private static BlockPos columnBottom(BlockGetter level, BlockPos pos) {
        SlantSide side = sideAt(level, pos);
        BlockPos at = pos;
        while (inColumn(level.getBlockState(stepDown(at, side.lean())), side)) {
            at = stepDown(at, side.lean());
        }
        return at;
    }

    private static void forEachInColumn(Level level, BlockPos pos, Consumer<SmokestackBlockEntity> action) {
        if (!isStack(level.getBlockState(pos))) {
            return;
        }
        SlantSide side = sideAt(level, pos);
        for (BlockPos at = columnBottom(level, pos); inColumn(level.getBlockState(at), side);
             at = stepUp(at, side.lean())) {
            if (level.getBlockEntity(at) instanceof SmokestackBlockEntity be) {
                action.accept(be);
            }
        }
    }

    private static void adoptColumnColor(Level level, BlockPos pos) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SmokestackBlockEntity self)) {
            return;
        }
        SlantSide side = sideAt(level, pos);
        for (BlockPos near : new BlockPos[] {stepDown(pos, side.lean()), stepUp(pos, side.lean())}) {
            if (inColumn(level.getBlockState(near), side)
                    && level.getBlockEntity(near) instanceof SmokestackBlockEntity be) {
                self.setColor(be.getColor());
                return;
            }
        }
    }

    public static void refreshColumn(Level level, BlockPos pos) {
        if (level.isClientSide || !isStack(level.getBlockState(pos))) {
            return;
        }
        SlantSide side = sideAt(level, pos);
        BlockPos bottom = columnBottom(level, pos);
        int signal = 0;
        BlockPos top = bottom;
        List<BlockPos> column = new ArrayList<>();
        for (BlockPos at = bottom; inColumn(level.getBlockState(at), side); at = stepUp(at, side.lean())) {
            if (!(level.getBlockEntity(at) instanceof SmokestackBlockEntity be)) {
                continue;
            }
            if (be.isBoilerDriven()) {
                return;
            }
            signal = Math.max(signal, level.getBestNeighborSignal(at));
            top = at;
            column.add(at);
        }
        for (BlockPos at : column) {
            if (level.getBlockEntity(at) instanceof SmokestackBlockEntity be) {
                be.setPower(at.equals(top) ? signal : 0);
            }
        }
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (!(be instanceof SmokestackBlockEntity stack)) return;
            if (lvl.isClientSide) {
                stack.clientTick();
            } else {
                stack.serverTick();
            }
        };
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
