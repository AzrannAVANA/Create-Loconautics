package com.lycoris.loconautics.client.hold;

import com.lycoris.loconautics.mixin.client.hold.HoldKeyMappingInvoker;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class HoldInteraction {

    private BlockPos interactionPos;

    void start() {
        ((HoldKeyMappingInvoker) Minecraft.getInstance().options.keyAttack).invokeRelease();
    }

    void stop() {
        interactionPos = null;
    }

    protected void release() {
    }

    public boolean isActive() {
        return HoldInteractionManager.isActive(this);
    }

    public BlockPos getInteractionPos() {
        return interactionPos;
    }

    public void startHold(Level level, Player player, BlockPos blockPos) {
        HoldInteractionManager.start(this);
        interactionPos = blockPos;
    }

    public static boolean inInteractionRange(Player player, Vec3 target, double reachBuffer) {
        double distance = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue() + reachBuffer;
        Vec3 eye = player.getEyePosition();
        return Sable.HELPER.projectOutOfSubLevel(player.level(), JOMLConversion.toJOML(target))
                .distanceSquared(eye.x, eye.y, eye.z) < distance * distance;
    }

    public int getCrouchBlockingTicks() {
        return 0;
    }

    public boolean activeTick(Level level, LocalPlayer player) {
        return false;
    }

    public boolean activeOnMouseMove(double yaw, double pitch) {
        return false;
    }

    public void renderOverlay(GuiGraphics graphics, int width, int height, boolean hideGui) {
    }

    boolean onAttack(int action) {
        return isActive() && action != 0;
    }

    void onUse(int action) {
        if (action == 0 && isActive()) {
            release();
            HoldInteractionManager.stop();
            ((HoldKeyMappingInvoker) Minecraft.getInstance().options.keyUse).invokeRelease();
        }
    }
}
