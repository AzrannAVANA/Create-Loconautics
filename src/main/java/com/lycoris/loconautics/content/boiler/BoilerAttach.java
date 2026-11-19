package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.function.UnaryOperator;

public final class BoilerAttach {
    public static final TagKey<Block> HOSTS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            LoconauticsConstants.id("boiler_hosts"));

    public static final TagKey<Block> FLUSH_HOSTS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            LoconauticsConstants.id("boiler_hosts_flush"));

    public static final BooleanProperty RAISED = BooleanProperty.create("raised");

    // how far a part sits out from its host, in blocks
    public static final double BULGE = 0.5;

    private BoilerAttach() {
    }

    public static boolean isHost(BlockState state) {
        return state.is(HOSTS);
    }

    public static boolean isHostAt(BlockGetter level, BlockPos pos) {
        return isHost(level.getBlockState(pos));
    }

    // face is the side of the part that touches the host
    public static boolean raisedOn(BlockGetter level, BlockPos part, Direction face) {
        return raisedAt(level, part.relative(face));
    }

    public static boolean raisedAt(BlockGetter level, BlockPos host) {
        BlockState state = level.getBlockState(host);
        return isHost(state) && !state.is(FLUSH_HOSTS);
    }

    // a placement helper ghost and its placed block both take their raised and joined state from the neighbors
    public static UnaryOperator<BlockState> settled(LevelAccessor level, BlockPos target) {
        return state -> Block.updateFromNeighbourShapes(state, level, target);
    }
}
