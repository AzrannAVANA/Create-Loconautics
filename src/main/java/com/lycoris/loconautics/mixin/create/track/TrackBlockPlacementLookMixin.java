package com.lycoris.loconautics.mixin.create.track;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lycoris.loconautics.common.sable.SubLevelFrames;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.trains.track.TrackBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TrackBlock.class, remap = false)
public abstract class TrackBlockPlacementLookMixin {

    @WrapOperation(method = "getStateForPlacement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 loconautics$localPlacementLook(Player player, Operation<Vec3> original,
                                                @Local(argsOnly = true) BlockPlaceContext ctx) {
        Vec3 world = original.call(player);
        Level level = ctx.getLevel();
        Vec3 local = SubLevelFrames.lookToLocal(level, ctx.getClickedPos(), world);
        if (local == world || level.isClientSide || !(player instanceof DeployerFakePlayer)) {
            return local;
        }
        if (local.x * local.x + local.z * local.z > 0.0025) {
            return local;
        }
        Vec3 chord = carChord(level, ctx);
        return chord == null ? local : chord;
    }

    private static Vec3 carChord(Level level, BlockPlaceContext ctx) {
        SubLevel sub = Sable.HELPER.getContaining(level, ctx.getClickedPos());
        if (sub == null || sub.getUniqueId() == null) {
            return null;
        }
        SableTrain.Car car = null;
        var train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
        if (train != null) {
            car = train.car();
        }
        if (car == null || car.localLead() == null || car.localTrail() == null) {
            return null;
        }
        Vector3dc lead = car.localLead();
        Vector3dc trail = car.localTrail();
        return new Vec3(lead.x() - trail.x(), 0.0, lead.z() - trail.z());
    }
}
