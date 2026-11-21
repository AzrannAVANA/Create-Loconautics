package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BogeyPlotChanges {

    private static final Set<UUID> MARKED = ConcurrentHashMap.newKeySet();

    private BogeyPlotChanges() {
    }

    public static void placed(ServerLevel level, BlockPos plotPos) {
        if (!(Sable.HELPER.getContaining(level, plotPos) instanceof ServerSubLevel sub)) {
            return;
        }
        SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
        LoconauticsConstants.TRACE.info("[bogeyplace] bogey placed at {} in body {}, train {}", plotPos,
                sub.getUniqueId(), train == null ? "none, queued to become one" : train.id());
        if (train == null) {
            AssembledBogeyTrains.queue(level, sub);
            return;
        }
        SubLevelSplitWatcher.forgetIgnored(train.id(), plotPos);
        MARKED.add(train.id());
    }

    public static void removed(ServerLevel level, BlockPos plotPos) {
        WheelColliders.clearPadsAround(level, plotPos);
    }

    static List<UUID> takeMarked() {
        List<UUID> taken = new ArrayList<>(MARKED);
        MARKED.removeAll(taken);
        return taken;
    }
}
