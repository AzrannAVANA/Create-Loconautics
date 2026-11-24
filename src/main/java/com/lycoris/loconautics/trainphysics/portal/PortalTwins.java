package com.lycoris.loconautics.trainphysics.portal;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PortalTwins {

    private enum Role { TWIN, STANDBY, LINGER }

    private record Copy(UUID bodyId, UUID masterId, UUID carId, Role role, ResourceKey<Level> masterDim,
                        ResourceKey<Level> dim, SableTeleportation.PlotSlot slot, BoundingBox3i box, long since) {

        Copy as(Role next, ResourceKey<Level> nextMasterDim, long at) {
            return new Copy(bodyId, masterId, carId, next, nextMasterDim, dim, slot, box, at);
        }
    }

    private record Attempts(UUID carId, int count) {}

    private record Spawned(ServerSubLevel body, SableTeleportation.PlotSlot slot, BoundingBox3i box) {}

    private record Snapshot(Vector3d position, Quaterniond orientation, Vector3d linear, Vector3d angular) {}

    private static final class Feedback {
        final Vector3d linear = new Vector3d();
        final Vector3d angular = new Vector3d();
    }

    private static final class Mirror {
        final Vector3d linear = new Vector3d();
        final Vector3d angular = new Vector3d();
        boolean armed;
    }

    public record MasterBlock(ServerLevel level, BlockPos pos, ServerSubLevel master) {}

    // moving flag, so copycats do not pop their item
    private static final int TWIN_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON;

    private static final Map<UUID, Copy> COPIES = new HashMap<>();
    private static final Map<UUID, UUID> TWIN_OF = new HashMap<>();
    private static final Set<SubLevelContacts.Pair> OWN_PAIRS = new HashSet<>();
    private static final Map<UUID, PortalFrame> FRAMES = new HashMap<>();
    private static final Set<UUID> TWIN_IDS = new HashSet<>();
    private static final Map<UUID, Snapshot[]> SNAPSHOTS = new HashMap<>();
    private static final Map<UUID, Feedback> FEEDBACK = new HashMap<>();
    private static final Map<UUID, Mirror> MIRRORS = new HashMap<>();
    private static final Map<UUID, Long> RENEWED = new HashMap<>();
    private static final Map<UUID, Attempts> ATTEMPTS = new HashMap<>();
    private static final Set<ResourceKey<Level>> TWIN_DIMS = new HashSet<>();
    private static final Set<UUID> STANDBY_REFUSED = new HashSet<>();
    private static final Set<String> REFUSALS_LOGGED = new HashSet<>();

    private static final PortalFrame IDENTITY = new PortalFrame(null, null, null, null, Vec3.ZERO, Vec3.ZERO,
            Vec3.ZERO, 0.0);

    private static final String TWIN_TAG = "loconautics_portal_shadow";

    // null serialization pointer on a body built in memory and sables holding map never lets it go. why.
    private static final SubLevelLoadingTicketType<Unit> HELD_BY_PORTAL =
            SubLevelLoadingTicketType.create(LoconauticsConstants.id("portal_shadow"), Unit.CODEC);

    private static final int MAX_ATTEMPTS = 3;
    private static final long GRACE_TICKS = 10;
    private static final long REAP_INTERVAL = 20;

    private static final long SLOW_CLONE_MS = 40;
    private static final long STANDBY_TICKS = 15;
    private static final long LINGER_TICKS = 15;

    private static final double REBOUND = 1.0;
    private static final double ANGULAR_REBOUND = 0.5;

    private static final double DEAD_BAND = 1.0e-4;

    private static long tick;

    private PortalTwins() {
    }

    public static void register() {
        SableEventPlatform.INSTANCE.onPhysicsTick(PortalTwins::onPhysicsTick);
        SableEventPlatform.INSTANCE.onPostPhysicsTick(PortalTwins::onPostPhysicsTick);
    }

    public static boolean isTwin(ServerSubLevel sub) {
        if (TWIN_IDS.contains(sub.getUniqueId())) {
            return true;
        }
        CompoundTag userData = sub.getUserDataTag();
        return userData != null && userData.getBoolean(TWIN_TAG);
    }

    public static boolean isTwin(UUID subLevelId) {
        return TWIN_IDS.contains(subLevelId);
    }

    public static UUID masterOf(UUID bodyId) {
        Copy copy = COPIES.get(bodyId);
        return copy == null ? null : copy.masterId();
    }

    public static UUID twinOf(UUID masterId) {
        return TWIN_OF.get(masterId);
    }

    private static Copy twinCopy(UUID masterId) {
        UUID bodyId = TWIN_OF.get(masterId);
        return bodyId == null ? null : COPIES.get(bodyId);
    }

    private static int attemptsOf(UUID masterId) {
        Attempts attempts = ATTEMPTS.get(masterId);
        return attempts == null ? 0 : attempts.count();
    }

    private static int countAttempt(UUID masterId, UUID carId) {
        Attempts next = new Attempts(carId, attemptsOf(masterId) + 1);
        ATTEMPTS.put(masterId, next);
        return next.count();
    }

    private static void releaseContacts(UUID bodyId) {
        SubLevelContacts.releaseAll(bodyId);
        OWN_PAIRS.removeIf(pair -> pair.first().equals(bodyId) || pair.second().equals(bodyId));
    }

    public static ServerSubLevel ensure(ServerSubLevel master, PortalFrame frame, UUID carId) {
        UUID masterId = master.getUniqueId();
        MinecraftServer server = master.getLevel().getServer();
        ServerLevel twinLevel = server.getLevel(frame.farDim());
        if (twinLevel == null) {
            return refused(masterId, "no level loaded for " + frame.farDim().location());
        }
        Copy existing = twinCopy(masterId);
        if (existing != null) {
            ServerSubLevel live = existing.dim().equals(frame.farDim())
                    && existing.masterDim().equals(master.getLevel().dimension())
                    ? SubLevelData.resolve(twinLevel, existing.bodyId()) : null;
            if (live != null) {
                FRAMES.put(masterId, frame);
                RENEWED.put(masterId, tick);
                return live;
            }
            drop(server, masterId);
            if (countAttempt(masterId, carId) >= MAX_ATTEMPTS) {
                return refused(masterId, "the twin in " + existing.dim().location() + " stopped resolving and "
                        + "that was attempt " + MAX_ATTEMPTS);
            }
        }
        if (attemptsOf(masterId) >= MAX_ATTEMPTS) {
            return refused(masterId, "gave up after " + MAX_ATTEMPTS + " failed spawns");
        }
        ServerSubLevel twin = spawn(master, frame, twinLevel, carId);
        if (twin == null) {
            countAttempt(masterId, carId);
            return null;
        }
        FRAMES.put(masterId, frame);
        RENEWED.put(masterId, tick);
        SableTrain car = SableTrainRegistry.bySubLevel(masterId);
        if (car != null) {
            SableTrainSyncPacket.announceTwin(car);
        }
        return twin;
    }

    public static void syncContacts(MinecraftServer server, List<UUID> chain) {
        Map<UUID, List<Copy>> byCar = new HashMap<>();
        for (Copy copy : COPIES.values()) {
            if (copy.role() == Role.TWIN && chain.contains(copy.carId())) {
                byCar.computeIfAbsent(copy.carId(), id -> new ArrayList<>()).add(copy);
            }
        }
        Set<SubLevelContacts.Pair> wanted = new HashSet<>();
        for (int i = 0; i < chain.size(); i++) {
            List<Copy> own = byCar.getOrDefault(chain.get(i), List.of());
            addPairs(wanted, own, own);
            if (i + 1 < chain.size()) {
                addPairs(wanted, own, byCar.getOrDefault(chain.get(i + 1), List.of()));
            }
        }
        for (SubLevelContacts.Pair pair : List.copyOf(OWN_PAIRS)) {
            if (!wanted.contains(pair) && (inChain(pair.first(), chain) || inChain(pair.second(), chain))) {
                SubLevelContacts.enable(pair.first(), pair.second());
                OWN_PAIRS.remove(pair);
            }
        }
        for (SubLevelContacts.Pair pair : wanted) {
            if (OWN_PAIRS.contains(pair) && SubLevelContacts.isDisabled(pair.first(), pair.second())) {
                continue;
            }
            Copy a = COPIES.get(pair.first());
            ServerLevel level = server.getLevel(a.dim());
            if (level != null && SubLevelContacts.disable(SubLevelData.resolve(level, pair.first()),
                    SubLevelData.resolve(level, pair.second()))) {
                OWN_PAIRS.add(pair);
            }
        }
    }

    // a car and its neighbours, the same pairs home turns off for coupled cars and their wheels
    private static void addPairs(Set<SubLevelContacts.Pair> out, List<Copy> a, List<Copy> b) {
        for (Copy x : a) {
            for (Copy y : b) {
                if (!x.bodyId().equals(y.bodyId()) && x.dim().equals(y.dim())) {
                    out.add(SubLevelContacts.Pair.of(x.bodyId(), y.bodyId()));
                }
            }
        }
    }

    private static boolean inChain(UUID bodyId, List<UUID> chain) {
        Copy copy = COPIES.get(bodyId);
        return copy == null || chain.contains(copy.carId());
    }

    private static <T> T refused(UUID masterId, String why) {
        if (REFUSALS_LOGGED.add(masterId + why)) {
            LoconauticsConstants.LOGGER.warn("[portal] twin refused for {}: {}", PortalTrace.id(masterId), why);
        }
        PortalTrace.log("refused master={} why={}", PortalTrace.id(masterId), why);
        return null;
    }

    public static void endStraddle(MinecraftServer server, UUID carId) {
        for (Copy copy : List.copyOf(COPIES.values())) {
            if (copy.role() == Role.TWIN && copy.carId().equals(carId)) {
                drop(server, copy.masterId());
            }
        }
        ATTEMPTS.values().removeIf(attempts -> attempts.carId().equals(carId));
        STANDBY_REFUSED.remove(carId);
        for (Copy copy : List.copyOf(COPIES.values())) {
            if (copy.role() == Role.STANDBY && copy.carId().equals(carId)) {
                dropCopy(server, copy);
            }
        }
    }

    public static void renew(UUID carId) {
        for (Copy copy : COPIES.values()) {
            if (copy.role() == Role.TWIN && copy.carId().equals(carId)) {
                RENEWED.put(copy.masterId(), tick);
            }
        }
    }

    public static void clearAll() {
        COPIES.clear();
        TWIN_OF.clear();
        FRAMES.clear();
        TWIN_IDS.clear();
        SNAPSHOTS.clear();
        FEEDBACK.clear();
        MIRRORS.clear();
        RENEWED.clear();
        ATTEMPTS.clear();
        TWIN_DIMS.clear();
        STANDBY_REFUSED.clear();
        REFUSALS_LOGGED.clear();
        OWN_PAIRS.clear();
    }

    private static Copy standbyOf(UUID masterId) {
        for (Copy copy : COPIES.values()) {
            if (copy.role() == Role.STANDBY && copy.masterId().equals(masterId)) {
                return copy;
            }
        }
        return null;
    }

    public static UUID ensureStandby(ServerSubLevel master, UUID carId) {
        UUID masterId = master.getUniqueId();
        if (!TWIN_OF.containsKey(masterId) || STANDBY_REFUSED.contains(masterId)) {
            return null;
        }
        Copy standby = standbyOf(masterId);
        if (standby != null) {
            return standby.bodyId();
        }
        Spawned made = cloneBody(master, new Vector3d(master.logicalPose().position()),
                new Quaterniond(master.logicalPose().orientation()), master.getLevel());
        if (made == null) {
            STANDBY_REFUSED.add(masterId);
            PortalTrace.log("standby refused master={}", PortalTrace.id(masterId));
            return null;
        }
        UUID bodyId = made.body().getUniqueId();
        PortalTrace.log("standby made master={} body={} slot={},{}", PortalTrace.id(masterId), PortalTrace.id(bodyId),
                made.slot().x(), made.slot().z());
        ResourceKey<Level> dim = master.getLevel().dimension();
        COPIES.put(bodyId, new Copy(bodyId, masterId, carId, Role.STANDBY, dim, dim, made.slot(), made.box(), tick));
        // the sync that makes it collider free runs next tick, one physics step in between would push it against its master
        PortalColliders.apply(master.getLevel(), bodyId, PortalCut.everything(bodyId));
        return bodyId;
    }

    // true while the standby is still young, so the clients have not had time to receive and mesh it
    public static boolean standbyPending(UUID masterId) {
        Copy standby = standbyOf(masterId);
        return standby != null && tick - standby.since() < STANDBY_TICKS;
    }

    // the landing car sits on top of its old twin, which must not push it
    public static void softenTwin(MinecraftServer server, UUID masterId) {
        Copy twin = twinCopy(masterId);
        ServerLevel level = twin == null ? null : server.getLevel(twin.dim());
        PortalTrace.log("soften master={} twin={} level={}", PortalTrace.id(masterId),
                twin == null ? "none" : PortalTrace.id(twin.bodyId()), level != null);
        if (level != null) {
            PortalColliders.apply(level, twin.bodyId(), PortalCut.everything(twin.bodyId()));
        }
    }

    // the old twin keeps drawing the far half until the landed car reaches the clients, the standby becomes the new twin
    public static Set<UUID> handedOver(MinecraftServer server, UUID masterId, PortalFrame turned) {
        UUID oldId = TWIN_OF.remove(masterId);
        Copy old = oldId == null ? null : COPIES.get(oldId);
        Copy standby = standbyOf(masterId);
        RENEWED.remove(masterId);
        FRAMES.remove(masterId);
        SNAPSHOTS.remove(masterId);
        FEEDBACK.remove(masterId);
        MIRRORS.remove(masterId);
        ATTEMPTS.remove(masterId);
        STANDBY_REFUSED.remove(masterId);
        for (Copy plate : List.copyOf(COPIES.values())) {
            if (plate.role() == Role.TWIN && plate.carId().equals(masterId) && !plate.masterId().equals(masterId)) {
                drop(server, plate.masterId());
            }
        }
        Set<UUID> kept = new HashSet<>();
        PortalTrace.log("handedOver master={} oldTwin={} standby={}", PortalTrace.id(masterId),
                old == null ? "none" : PortalTrace.id(old.bodyId()),
                standby == null ? "none" : PortalTrace.id(standby.bodyId()));
        if (old != null) {
            releaseContacts(old.bodyId());
            COPIES.put(old.bodyId(), old.as(Role.LINGER, old.dim(), tick));
            kept.add(old.bodyId());
        }
        if (standby != null) {
            COPIES.put(standby.bodyId(), standby.as(Role.TWIN, turned.nearDim(), tick));
            TWIN_OF.put(masterId, standby.bodyId());
            FRAMES.put(masterId, turned);
            RENEWED.put(masterId, tick);
            kept.add(standby.bodyId());
            SableTrain car = SableTrainRegistry.bySubLevel(masterId);
            if (car != null) {
                SableTrainSyncPacket.announceTwin(car);
            }
        }
        return kept;
    }

    public static void dropAll(MinecraftServer server) {
        for (UUID masterId : List.copyOf(TWIN_OF.keySet())) {
            drop(server, masterId);
        }
        for (Copy copy : List.copyOf(COPIES.values())) {
            dropCopy(server, copy);
        }
    }

    private static void dropCopy(MinecraftServer server, Copy copy) {
        PortalTrace.log("dropCopy body={} master={} role={} by={}", PortalTrace.id(copy.bodyId()),
                PortalTrace.id(copy.masterId()), copy.role(), caller());
        COPIES.remove(copy.bodyId());
        if (copy.role() == Role.TWIN) {
            TWIN_OF.remove(copy.masterId(), copy.bodyId());
        }
        SableTrainSyncPacket.broadcastRemoval(copy.bodyId());
        releaseContacts(copy.bodyId());
        PortalEntry.release(copy.bodyId());
        clear(server.getLevel(copy.dim()), copy.bodyId(), copy.slot(), copy.box());
    }

    public static void retain(MinecraftServer server, UUID carId, Collection<UUID> liveMasters) {
        for (Copy copy : List.copyOf(COPIES.values())) {
            if (copy.role() == Role.TWIN && copy.carId().equals(carId) && !liveMasters.contains(copy.masterId())) {
                drop(server, copy.masterId());
            }
        }
    }

    public static void drop(MinecraftServer server, UUID masterId) {
        UUID bodyId = TWIN_OF.remove(masterId);
        RENEWED.remove(masterId);
        FRAMES.remove(masterId);
        SNAPSHOTS.remove(masterId);
        FEEDBACK.remove(masterId);
        MIRRORS.remove(masterId);
        if (bodyId == null) {
            return;
        }
        Copy twin = COPIES.remove(bodyId);
        PortalTrace.log("drop master={} twin={} by={}", PortalTrace.id(masterId), PortalTrace.id(bodyId), caller());
        SableTrainSyncPacket.broadcastRemoval(bodyId);
        releaseContacts(bodyId);
        PortalColliders.clear(bodyId);
        if (twin != null) {
            clear(server.getLevel(twin.dim()), bodyId, twin.slot(), twin.box());
        }
    }

    private static String caller() {
        StackTraceElement[] frames = new Throwable().getStackTrace();
        return frames.length > 2 ? frames[2].getMethodName() : "unknown";
    }

    private static void status(MinecraftServer server) {
        for (Copy twin : List.copyOf(COPIES.values())) {
            if (twin.role() == Role.TWIN) {
                ServerLevel masterLevel = server.getLevel(twin.masterDim());
                ServerLevel twinLevel = server.getLevel(twin.dim());
                ServerSubLevel master = masterLevel == null ? null : SubLevelData.resolve(masterLevel, twin.masterId());
                ServerSubLevel body = twinLevel == null ? null : SubLevelData.resolve(twinLevel, twin.bodyId());
                PortalFrame frame = FRAMES.get(twin.masterId());
                String error = master != null && body != null && frame != null ? PortalTrace.f(
                        frame.toFar(master.logicalPose().position()).distance(body.logicalPose().position())) : "n/a";
                BoundingBox3ic box = body == null ? null : body.getPlot().getBoundingBox();
                PortalTrace.log("twin master={} car={} plate={} {} to {} body={} resolves={} poseError={} plotVolume={} "
                                + "blockEntities={} renewedAgo={}", PortalTrace.id(twin.masterId()),
                        PortalTrace.id(twin.carId()), !twin.masterId().equals(twin.carId()),
                        twin.masterDim().location(), twin.dim().location(), PortalTrace.id(twin.bodyId()),
                        body != null, error, box == null ? -1 : box.volume(),
                        body == null ? -1 : SubLevelBlocks.blockEntities(body).size(),
                        tick - RENEWED.getOrDefault(twin.masterId(), tick));
                continue;
            }
            ServerLevel level = server.getLevel(twin.dim());
            ServerSubLevel body = level == null ? null : SubLevelData.resolve(level, twin.bodyId());
            PortalTrace.log("follower body={} master={} role={} dim={} resolves={} age={}",
                    PortalTrace.id(twin.bodyId()), PortalTrace.id(twin.masterId()), twin.role(),
                    twin.dim().location(), body != null, tick - twin.since());
        }
    }

    public static String debugCounts() {
        return "twins=" + TWIN_OF.size() + " copies=" + COPIES.size() + " mirrors=" + MIRRORS.size()
                + " snapshots=" + SNAPSHOTS.size();
    }

    public static void sweep(MinecraftServer server) {
        tick++;
        if (PortalTrace.on() && tick % 20 == 0) {
            status(server);
        }
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(RENEWED.entrySet())) {
            if (tick - entry.getValue() > GRACE_TICKS) {
                drop(server, entry.getKey());
            }
        }
        for (Copy copy : List.copyOf(COPIES.values())) {
            boolean expired = switch (copy.role()) {
                case LINGER -> tick - copy.since() > LINGER_TICKS;
                case STANDBY -> !TWIN_OF.containsKey(copy.masterId());
                case TWIN -> false;
            };
            if (expired) {
                dropCopy(server, copy);
            }
        }
        if (tick % REAP_INTERVAL == 0) {
            reapUnclaimed(server);
        }
    }

    private static int substep(SubLevelPhysicsSystem system) {
        int n = Math.max(1, system.getConfig().substepsPerTick);
        int k = (int) Math.round(system.getPartialPhysicsTick() * n) - 1;
        return Math.max(0, Math.min(n - 1, k));
    }

    public static void onPhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        if (COPIES.isEmpty()) {
            return;
        }
        ServerLevel level = system.getLevel();
        PhysicsPipeline pipeline = system.getPipeline();
        int k = substep(system);
        Vector3d gravity = DimensionPhysicsData.getGravity(level);
        for (Copy copy : List.copyOf(COPIES.values())) {
            if (copy.role() == Role.TWIN) {
                if (copy.masterDim().equals(level.dimension())) {
                    applyFeedback(pipeline, level, copy);
                }
                if (copy.dim().equals(level.dimension())) {
                    mirror(pipeline, level, copy, k, timeStep, gravity);
                }
            } else if (copy.dim().equals(level.dimension())) {
                follow(pipeline, level, copy, timeStep, gravity);
            }
        }
    }

    public static void onPostPhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        if (TWIN_OF.isEmpty()) {
            return;
        }
        ServerLevel level = system.getLevel();
        PhysicsPipeline pipeline = system.getPipeline();
        int k = substep(system);
        int n = Math.max(1, system.getConfig().substepsPerTick);
        double damping = 1.0 / (1.0 + DimensionPhysicsData.getUniversalDrag(level) * timeStep);
        for (Copy copy : List.copyOf(COPIES.values())) {
            if (copy.role() != Role.TWIN) {
                continue;
            }
            if (copy.masterDim().equals(level.dimension())) {
                snapshot(pipeline, level, copy, k, n);
            }
            if (copy.dim().equals(level.dimension())) {
                measure(pipeline, level, copy, damping);
            }
        }
    }

    private static void snapshot(PhysicsPipeline pipeline, ServerLevel level, Copy twin, int k, int n) {
        ServerSubLevel master = SubLevelData.resolve(level, twin.masterId());
        if (master == null) {
            return;
        }
        Snapshot[] ring = SNAPSHOTS.get(twin.masterId());
        if (ring == null || ring.length != n) {
            ring = new Snapshot[n];
            SNAPSHOTS.put(twin.masterId(), ring);
        }
        ring[k] = new Snapshot(new Vector3d(master.logicalPose().position()),
                new Quaterniond(master.logicalPose().orientation()),
                pipeline.getLinearVelocity(master, new Vector3d()), pipeline.getAngularVelocity(master, new Vector3d()));
    }

    private static void mirror(PhysicsPipeline pipeline, ServerLevel level, Copy twin, int k, double dt,
                               Vector3d gravity) {
        PortalFrame frame = FRAMES.get(twin.masterId());
        ServerLevel masterLevel = level.getServer().getLevel(twin.masterDim());
        ServerSubLevel master = masterLevel == null ? null : SubLevelData.resolve(masterLevel, twin.masterId());
        ServerSubLevel body = SubLevelData.resolve(level, twin.bodyId());
        if (frame == null || master == null || body == null) {
            return;
        }
        Snapshot[] ring = SNAPSHOTS.get(twin.masterId());
        Snapshot at = ring != null && k < ring.length ? ring[k] : null;
        if (at == null) {
            PhysicsPipeline masterPipeline = pipelineOf(masterLevel);
            at = new Snapshot(new Vector3d(master.logicalPose().position()),
                    new Quaterniond(master.logicalPose().orientation()),
                    masterPipeline == null ? new Vector3d() : masterPipeline.getLinearVelocity(master, new Vector3d()),
                    masterPipeline == null ? new Vector3d() : masterPipeline.getAngularVelocity(master, new Vector3d()));
        }
        Vector3d linear = new Vector3d(at.linear());
        Vector3d angular = new Vector3d(at.angular());
        Feedback pending = FEEDBACK.get(twin.masterId());
        if (pending != null) {
            linear.add(pending.linear);
            angular.add(pending.angular);
        }
        Vector3d linearFar = frame.dirToFar(linear);
        Vector3d angularFar = frame.dirToFar(angular);
        place(pipeline, body, frame, at, linearFar, angularFar, dt, gravity);
        Mirror set = MIRRORS.computeIfAbsent(twin.masterId(), id -> new Mirror());
        set.linear.set(linearFar);
        set.angular.set(angularFar);
        set.armed = true;
    }

    private static void place(PhysicsPipeline pipeline, ServerSubLevel body, PortalFrame frame, Snapshot at,
                              Vector3d linearFar, Vector3d angularFar, double dt, Vector3d gravity) {
        pipeline.teleport(body, frame.toFar(at.position()), frame.orientToFar(at.orientation()));
        Vector3d addLinear = new Vector3d(linearFar).sub(pipeline.getLinearVelocity(body, new Vector3d()))
                .fma(-dt, gravity);
        Vector3d addAngular = new Vector3d(angularFar).sub(pipeline.getAngularVelocity(body, new Vector3d()));
        pipeline.addLinearAndAngularVelocity(body, addLinear, addAngular);
    }

    private static void follow(PhysicsPipeline pipeline, ServerLevel level, Copy follower, double dt,
                               Vector3d gravity) {
        ServerSubLevel master = SubLevelData.resolve(level, follower.masterId());
        ServerSubLevel body = SubLevelData.resolve(level, follower.bodyId());
        if (master == null || body == null) {
            return;
        }
        Vector3d linear = pipeline.getLinearVelocity(master, new Vector3d());
        Vector3d angular = pipeline.getAngularVelocity(master, new Vector3d());
        Snapshot at = new Snapshot(new Vector3d(master.logicalPose().position()),
                new Quaterniond(master.logicalPose().orientation()), linear, angular);
        place(pipeline, body, IDENTITY, at, linear, angular, dt, gravity);
    }

    // unverified assumes rapiers linvel  1  dt  damping with sables universal drag as the damping
    private static void measure(PhysicsPipeline pipeline, ServerLevel level, Copy twin, double damping) {
        Mirror set = MIRRORS.get(twin.masterId());
        PortalFrame frame = FRAMES.get(twin.masterId());
        ServerSubLevel body = SubLevelData.resolve(level, twin.bodyId());
        if (set == null || !set.armed || frame == null || body == null) {
            return;
        }
        set.armed = false;
        Vector3d dLinear = pipeline.getLinearVelocity(body, new Vector3d())
                .sub(new Vector3d(set.linear).mul(damping));
        Vector3d dAngular = pipeline.getAngularVelocity(body, new Vector3d())
                .sub(new Vector3d(set.angular).mul(damping));
        if (dLinear.lengthSquared() < DEAD_BAND * DEAD_BAND && dAngular.lengthSquared() < DEAD_BAND * DEAD_BAND) {
            return;
        }
        Feedback fb = FEEDBACK.computeIfAbsent(twin.masterId(), id -> new Feedback());
        fb.linear.add(frame.dirToNear(dLinear));
        fb.angular.add(frame.dirToNear(dAngular));
    }

    // a twin mirrored into a wall a tick late is pushed out at rapiers corrective speed, that must not launch the car
    private static void applyFeedback(PhysicsPipeline pipeline, ServerLevel level, Copy twin) {
        Feedback fb = FEEDBACK.get(twin.masterId());
        if (fb == null || (fb.linear.lengthSquared() == 0.0 && fb.angular.lengthSquared() == 0.0)) {
            return;
        }
        ServerSubLevel master = SubLevelData.resolve(level, twin.masterId());
        if (master == null) {
            fb.linear.zero();
            fb.angular.zero();
            return;
        }
        Vector3d linear = clampLength(new Vector3d(fb.linear),
                pipeline.getLinearVelocity(master, new Vector3d()).length() + REBOUND);
        Vector3d angular = clampLength(new Vector3d(fb.angular),
                pipeline.getAngularVelocity(master, new Vector3d()).length() + ANGULAR_REBOUND);
        keepOnRails(twin.carId(), linear, angular);
        if (LoconauticsConfig.DEBUG) {
            traceFeedback(pipeline, twin, master, fb.linear, linear);
        }
        pipeline.addLinearAndAngularVelocity(master, linear, angular);
        fb.linear.zero();
        fb.angular.zero();
    }

    private static void traceFeedback(PhysicsPipeline pipeline, Copy twin, ServerSubLevel master, Vector3d raw,
                                      Vector3d sent) {
        Vector3d masterVelocity = pipeline.getLinearVelocity(master, new Vector3d());
        SableTrain jamTrain = SableTrainRegistry.bySubLevel(twin.carId());
        Vec3 jamAlong = jamTrain == null || jamTrain.car().carriage() == null ? null : jamTrain.car().carriage().forward();
        LoconauticsConstants.TRACE.info("[xjam] feedback twin={} master={} raw={} sent={} masterV={} alongV={} railed={}",
                PortalTrace.id(twin.bodyId()), PortalTrace.id(twin.masterId()), PortalTrace.f(raw.length()),
                PortalTrace.f(sent.length()), PortalTrace.f(masterVelocity.length()),
                jamAlong == null ? "n/a" : PortalTrace.f(masterVelocity.dot(jamAlong.x, jamAlong.y, jamAlong.z)),
                jamTrain != null && !jamTrain.isDerailed());
    }

    // the twin has no rails, so a side push on it must not walk the railed car off its track
    private static void keepOnRails(UUID carId, Vector3d linear, Vector3d angular) {
        SableTrain train = SableTrainRegistry.bySubLevel(carId);
        if (train == null || train.isDerailed() || train.isPhysics() || train.offTrackBogeyCount() > 0
                || train.car().carriage() == null) {
            return;
        }
        Vec3 along = train.car().carriage().forward();
        double slide = linear.x * along.x + linear.y * along.y + linear.z * along.z;
        linear.set(along.x * slide, along.y * slide, along.z * slide);
        angular.zero();
    }

    static Vector3d clampLength(Vector3d v, double limit) {
        double length = v.length();
        return length > limit && length > 0.0 ? v.mul(limit / length) : v;
    }

    private static PhysicsPipeline pipelineOf(ServerLevel level) {
        ServerSubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        return container == null ? null : container.physicsSystem().getPipeline();
    }

    private static ServerSubLevel spawn(ServerSubLevel master, PortalFrame frame, ServerLevel twinLevel, UUID carId) {
        Vector3d position = frame.toFar(master.logicalPose().position());
        Spawned made = cloneBody(master, position, frame.orientToFar(master.logicalPose().orientation()), twinLevel);
        if (made == null) {
            return null;
        }
        UUID masterId = master.getUniqueId();
        UUID bodyId = made.body().getUniqueId();
        COPIES.put(bodyId, new Copy(bodyId, masterId, carId, Role.TWIN, master.getLevel().dimension(),
                twinLevel.dimension(), made.slot(), made.box(), tick));
        TWIN_OF.put(masterId, bodyId);
        PortalTrace.log("spawned master={} car={} twin={} in {} slot={},{} plate={}", PortalTrace.id(masterId),
                PortalTrace.id(carId), PortalTrace.id(made.body().getUniqueId()), twinLevel.dimension().location(),
                made.slot().x(), made.slot().z(), !masterId.equals(carId));
        return made.body();
    }

    private static Spawned cloneBody(ServerSubLevel master, Vector3d position, Quaterniond orientation,
                                     ServerLevel twinLevel) {
        long started = System.nanoTime();
        ServerLevel masterLevel = master.getLevel();
        UUID masterId = master.getUniqueId();
        SableTeleportation.PlotSlot from = SableTeleportation.plotSlot(master);
        if (from == null) {
            return refused(masterId, "the master has no plot slot");
        }
        SableTeleportation.PlotSlot slot = SableTeleportation.plotSlotFree(twinLevel, from)
                ? from : SableTeleportation.firstFreeSlot(twinLevel, Set.of());
        if (slot == null) {
            return refused(masterId, "no free plot left in " + twinLevel.dimension().location());
        }
        CompoundTag tag = SubLevelSerializer.toData(master, List.of()).fullTag();
        long afterData = System.nanoTime();
        String rebased = SableTeleportation.rebaseSections(tag, masterLevel, twinLevel);
        if (rebased != null) {
            return refused(masterId, "rebase: " + rebased);
        }
        String repotted = SableTeleportation.repot(tag, masterLevel, twinLevel, from, slot);
        if (repotted != null) {
            return refused(masterId, "repot to " + slot.x() + ", " + slot.z() + ": " + repotted);
        }
        UUID twinId = UUID.randomUUID();
        tag.putUUID("uuid", twinId);
        Pose3d target = new Pose3d(master.logicalPose());
        target.position().set(position);
        target.orientation().set(orientation);
        target.rotationPoint().add(SableTeleportation.plotShift(masterLevel, twinLevel, from, slot));
        tag.put("pose", SableNBTUtils.writePose3d(target));
        tag.remove("linear_velocity");
        tag.remove("angular_velocity");
        CompoundTag userData = tag.getCompound("user_data");
        userData.putBoolean(TWIN_TAG, true);
        tag.put("user_data", userData);

        long afterRepot = System.nanoTime();
        BlockPos landing = BlockPos.containing(position.x(), position.y(), position.z());
        boolean coldLanding = twinLevel.getChunkSource().getChunkNow(landing.getX() >> 4, landing.getZ() >> 4) == null;
        twinLevel.getChunk(landing);
        long afterChunk = System.nanoTime();
        var clone = SubLevelSerializer.fromData(tag);
        long afterFrom = System.nanoTime();
        if (clone == null) {
            return refused(masterId, "fromData refused the repotted tag for " + twinLevel.dimension().location());
        }
        TWIN_IDS.add(twinId);
        ServerSubLevel twin = SubLevelSerializer.fullyLoad(twinLevel, clone);
        if (twin == null) {
            TWIN_IDS.remove(twinId);
            return refused(masterId, "fullyLoad refused the clone in " + twinLevel.dimension().location()
                    + " at plot " + slot.x() + ", " + slot.z());
        }
        long afterLoad = System.nanoTime();
        LoconauticsConstants.TRACE.info("[twinload] clone master={} twin={} from={},{} slot={},{} shifted={} coldLanding={} "
                        + "pose={} rotationPoint={} worldBounds={}", PortalTrace.id(masterId), PortalTrace.id(twinId),
                from.x(), from.z(), slot.x(), slot.z(), !from.equals(slot), coldLanding,
                PortalTrace.f(twin.logicalPose().position().x()) + "," + PortalTrace.f(twin.logicalPose().position().z()),
                PortalTrace.f(twin.logicalPose().rotationPoint().x()) + ","
                        + PortalTrace.f(twin.logicalPose().rotationPoint().z()),
                PortalTrace.f(twin.boundingBox().minX()) + ".." + PortalTrace.f(twin.boundingBox().maxX()) + " z "
                        + PortalTrace.f(twin.boundingBox().minZ()) + ".." + PortalTrace.f(twin.boundingBox().maxZ()));
        ServerSubLevelContainer container = SubLevelContainer.getContainer(twinLevel);
        if (container != null) {
            container.addForceLoadTicket(twin, HELD_BY_PORTAL, Unit.INSTANCE);
        }
        BoundingBox3ic plotBox = twin.getPlot().getBoundingBox();
        TWIN_DIMS.add(twinLevel.dimension());
        for (BlockEntity be : SubLevelBlocks.blockEntities(twin)) {
            stripContents(twinLevel, be);
        }
        long total = (System.nanoTime() - started) / 1_000_000L;
        String stages = "toData=" + (afterData - started) / 1_000_000L + "ms repot=" + (afterRepot - afterData) / 1_000_000L
                + "ms getChunk=" + (afterChunk - afterRepot) / 1_000_000L + "ms fromData=" + (afterFrom - afterChunk) / 1_000_000L
                + "ms fullyLoad=" + (afterLoad - afterFrom) / 1_000_000L + "ms";
        if (total >= SLOW_CLONE_MS) {
            LoconauticsConstants.LOGGER.warn("[portal] slow clone of {} into {} took {}ms ({}), slot {},{}",
                    PortalTrace.id(masterId), twinLevel.dimension().location(), total, stages, slot.x(), slot.z());
        }
        PortalTrace.log("clone timing master={} total={}ms {}", PortalTrace.id(masterId), total, stages);
        return new Spawned(twin, slot, plotBox == null ? null : new BoundingBox3i(plotBox));
    }

    private static void stripContents(ServerLevel level, BlockEntity be) {
        if (be instanceof SmartBlockEntity smart
                && smart.getBehaviour(RopeStrandHolderBehavior.TYPE) instanceof RopeStrandHolderBehavior rope) {
            rope.unload();
        }
        if (be instanceof Container container) {
            container.clearContent();
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
        if (handler instanceof IItemHandlerModifiable modifiable) {
            for (int i = 0; i < modifiable.getSlots(); i++) {
                modifiable.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        be.setChanged();
    }

    // the twin never changes on its own, every edit lands on the master and is copied over here
    public static void syncBlocks(MinecraftServer server) {
        for (Copy twin : List.copyOf(COPIES.values())) {
            if (twin.role() != Role.TWIN) {
                continue;
            }
            ServerLevel masterLevel = server.getLevel(twin.masterDim());
            ServerLevel twinLevel = server.getLevel(twin.dim());
            ServerSubLevel master = masterLevel == null ? null : SubLevelData.resolve(masterLevel, twin.masterId());
            ServerSubLevel body = twinLevel == null ? null : SubLevelData.resolve(twinLevel, twin.bodyId());
            if (master == null || body == null) {
                continue;
            }
            if (copyChanges(masterLevel, master, twinLevel, body)) {
                PortalColliders.invalidate(twin.masterId());
                PortalColliders.invalidate(twin.bodyId());
            }
        }
    }

    private static boolean copyChanges(ServerLevel masterLevel, ServerSubLevel master, ServerLevel twinLevel,
                                       ServerSubLevel twin) {
        BoundingBox3ic a = master.getPlot().getBoundingBox();
        BoundingBox3ic b = twin.getPlot().getBoundingBox();
        if (a == null || b == null) {
            return false;
        }
        BlockPos shift = twin.getPlot().getCenterBlock().subtract(master.getPlot().getCenterBlock());
        int minX = Math.min(a.minX(), b.minX() - shift.getX());
        int minY = Math.min(a.minY(), b.minY() - shift.getY());
        int minZ = Math.min(a.minZ(), b.minZ() - shift.getZ());
        int maxX = Math.max(a.maxX(), b.maxX() - shift.getX());
        int maxY = Math.max(a.maxY(), b.maxY() - shift.getY());
        int maxZ = Math.max(a.maxZ(), b.maxZ() - shift.getZ());
        List<BlockPos> changed = new ArrayList<>();
        BlockPos.MutableBlockPos from = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos to = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState want = masterLevel.getBlockState(from.set(x, y, z));
                    to.set(x + shift.getX(), y + shift.getY(), z + shift.getZ());
                    if (twinLevel.getBlockState(to) == want) {
                        continue;
                    }
                    setWithoutDrops(twinLevel, to, want);
                    BlockEntity source = masterLevel.getBlockEntity(from);
                    BlockEntity copy = twinLevel.getBlockEntity(to);
                    if (source != null && copy != null) {
                        copy.loadWithComponents(source.saveWithoutMetadata(masterLevel.registryAccess()),
                                twinLevel.registryAccess());
                        stripContents(twinLevel, copy);
                    }
                    changed.add(to.immutable());
                }
            }
        }
        return !changed.isEmpty();
    }

    public static MasterBlock masterBlock(ServerLevel level, BlockPos pos) {
        if (COPIES.isEmpty()) {
            return null;
        }
        SubLevelAccess access = SableCompanion.INSTANCE.getContaining(level, pos.getX() >> 4, pos.getZ() >> 4);
        if (!(access instanceof ServerSubLevel body) || !TWIN_IDS.contains(body.getUniqueId())) {
            return null;
        }
        Copy copy = COPIES.get(body.getUniqueId());
        if (copy == null) {
            return null;
        }
        ServerLevel masterLevel = level.getServer().getLevel(copy.masterDim());
        ServerSubLevel master = masterLevel == null ? null : SubLevelData.resolve(masterLevel, copy.masterId());
        if (master == null) {
            return null;
        }
        BlockPos shift = master.getPlot().getCenterBlock().subtract(body.getPlot().getCenterBlock());
        return new MasterBlock(masterLevel, pos.offset(shift), master);
    }

    public static Set<UUID> purgeSaved(ServerLevel level) {
        ServerSubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        if (container == null) {
            return Set.of();
        }
        Set<UUID> wiped = new HashSet<>();
        for (SubLevelAccess access : List.copyOf(container.getAllSubLevels())) {
            if (!(access instanceof ServerSubLevel sub) || sub.isRemoved() || !isTwin(sub)) {
                continue;
            }
            wipe(level, sub);
            wiped.add(sub.getUniqueId());
        }
        if (!wiped.isEmpty()) {
            LoconauticsConstants.LOGGER.info("[portal] wiped {} leaked twin sub level(s) in {}", wiped.size(),
                    level.dimension().location());
        }
        return wiped;
    }

    private static void reapUnclaimed(MinecraftServer server) {
        if (TWIN_DIMS.isEmpty()) {
            return;
        }
        Set<UUID> claimed = new HashSet<>(COPIES.keySet());
        for (ResourceKey<Level> dim : TWIN_DIMS) {
            ServerLevel level = server.getLevel(dim);
            ServerSubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            for (SubLevelAccess access : List.copyOf(container.getAllSubLevels())) {
                if (!(access instanceof ServerSubLevel sub) || sub.isRemoved() || !isTwin(sub)
                        || claimed.contains(sub.getUniqueId())) {
                    continue;
                }
                SubLevelContacts.releaseAll(sub.getUniqueId());
                wipe(level, sub);
                LoconauticsConstants.LOGGER.info("[portal] reaped unclaimed twin {} in {}", sub.getUniqueId(),
                        dim.location());
            }
        }
    }

    // a body sable dropped resolves null but may still hold its plot, so this asks the container, then the slot
    private static void clear(ServerLevel level, UUID bodyId, SableTeleportation.PlotSlot slot, BoundingBox3i box) {
        ServerSubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        if (container == null) {
            TWIN_IDS.remove(bodyId);
            return;
        }
        if (container.getSubLevel(bodyId) instanceof ServerSubLevel sub && !sub.isRemoved()) {
            wipe(level, sub);
            TWIN_IDS.remove(bodyId);
            return;
        }
        if (!SableTeleportation.plotSlotFree(level, slot)) {
            TWIN_IDS.remove(bodyId);
            return;
        }
        clearBox(level, box);
        LoconauticsConstants.LOGGER.info("[portal] cleared the blocks of twin {} in {}, its body had already left "
                + "the container", bodyId, level.dimension().location());
        TWIN_IDS.remove(bodyId);
    }

    private static void wipe(ServerLevel level, ServerSubLevel sub) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        container.removeForceLoadTicket(sub, HELD_BY_PORTAL, Unit.INSTANCE);
        clearBox(level, sub.getPlot().getBoundingBox());
        container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
        TWIN_IDS.remove(sub.getUniqueId());
    }

    private static void setWithoutDrops(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).hasBlockEntity()) {
            level.removeBlockEntity(pos);
        }
        level.setBlock(pos, state, TWIN_FLAGS);
    }

    // setblock, never destroyblock, which fires the break particles and cracks for every block even with drops off
    private static void clearBox(ServerLevel level, BoundingBox3ic bounds) {
        if (bounds == null || bounds.volume() <= 0) {
            return;
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    setWithoutDrops(level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }
}
