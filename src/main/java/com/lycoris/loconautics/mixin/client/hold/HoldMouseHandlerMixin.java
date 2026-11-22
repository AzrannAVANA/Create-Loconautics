package com.lycoris.loconautics.mixin.client.hold;

import com.llamalad7.mixinextras.sugar.Local;
import com.lycoris.loconautics.client.hold.HoldInteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class HoldMouseHandlerMixin {

    @Inject(method = "turnPlayer", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void loconautics$holdTurn(double d, CallbackInfo ci, @Local(ordinal = 4) double j,
                                      @Local(ordinal = 5) double k, @Local(ordinal = 0) int l) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return;
        if (HoldInteractionManager.onMouseMove(j, k * (double) l)) ci.cancel();
    }

    @Inject(method = "onPress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;",
            ordinal = 0), cancellable = true)
    private void loconautics$holdPress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return;
        if (HoldInteractionManager.onInput(true, button, -1, action)) ci.cancel();
    }
}
