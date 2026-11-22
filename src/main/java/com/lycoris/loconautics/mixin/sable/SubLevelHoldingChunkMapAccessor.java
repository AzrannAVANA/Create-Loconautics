package com.lycoris.loconautics.mixin.sable;

import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public interface SubLevelHoldingChunkMapAccessor {

    @Accessor("loadedHoldingChunks")
    Long2ObjectMap<SubLevelHoldingChunk> loconautics$loadedHoldingChunks();
}
