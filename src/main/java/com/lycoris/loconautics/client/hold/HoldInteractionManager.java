package com.lycoris.loconautics.client.hold;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class HoldInteractionManager {

    @Nullable
    private static HoldInteraction active;

    private static int crouchBlockTicks;

    private HoldInteractionManager() {
    }

    public static boolean isActive() {
        return active != null;
    }

    static boolean isActive(HoldInteraction interaction) {
        return interaction == active;
    }

    static void start(HoldInteraction interaction) {
        if (active != null) active.stop();
        active = interaction;
        active.start();
    }

    public static void stop() {
        if (active == null) return;
        active.stop();
        active = null;
    }

    public static boolean canCrouch() {
        return crouchBlockTicks <= 0;
    }

    public static void tick(Level level, LocalPlayer player) {
        if (crouchBlockTicks > 0) {
            if (!player.input.shiftKeyDown) crouchBlockTicks = 0;
            crouchBlockTicks--;
        }
        if (active == null || level == null) return;
        crouchBlockTicks = active.getCrouchBlockingTicks();
        if (active.activeTick(level, player)) stop();
    }

    public static void renderOverlay(GuiGraphics graphics, int width, int height) {
        if (active != null) active.renderOverlay(graphics, width, height, Minecraft.getInstance().options.hideGui);
    }

    public static boolean onMouseMove(double yaw, double pitch) {
        Minecraft mc = Minecraft.getInstance();
        return active != null && mc.screen == null && active.activeOnMouseMove(yaw, pitch);
    }

    public static boolean onInput(boolean mouse, int key, int scanCode, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null || mc.screen != null) return false;
        if (matches(mc.options.keyAttack, mouse, key, scanCode)) return active.onAttack(action);
        if (matches(mc.options.keyUse, mouse, key, scanCode)) active.onUse(action);
        return false;
    }

    private static boolean matches(KeyMapping mapping, boolean mouse, int key, int scanCode) {
        return mouse ? mapping.matchesMouse(key) : mapping.matches(key, scanCode);
    }
}
