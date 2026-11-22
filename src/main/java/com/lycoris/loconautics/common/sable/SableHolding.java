package com.lycoris.loconautics.common.sable;

import com.lycoris.loconautics.mixin.sable.SubLevelHoldingChunkMapAccessor;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class SableHolding {

    private SableHolding() {
    }

    @Nullable
    public static ChunkPos filedUnder(SubLevelHoldingChunkMap map, UUID subId) {
        for (SubLevelHoldingChunk chunk : ((SubLevelHoldingChunkMapAccessor) map).loconautics$loadedHoldingChunks().values()) {
            for (HoldingSubLevel held : chunk.getLoadedHoldingSubLevels()) {
                if (held.data().uuid().equals(subId)) {
                    return chunk.getChunkPos();
                }
            }
        }
        return null;
    }
}
