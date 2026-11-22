package com.lycoris.loconautics.mixin.client.hold;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.client.hold.HoldInteractionManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class HoldLocalPlayerMixin extends AbstractClientPlayer {

    public HoldLocalPlayerMixin(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "isShiftKeyDown", at = @At("RETURN"), cancellable = true)
    private void loconautics$holdShiftBlock(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !HoldInteractionManager.canCrouch()) cir.setReturnValue(false);
    }

    @WrapOperation(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/player/Input;shiftKeyDown:Z"))
    private boolean loconautics$holdShiftHidden(Input instance, Operation<Boolean> original) {
        return original.call(instance) && HoldInteractionManager.canCrouch();
    }
}
