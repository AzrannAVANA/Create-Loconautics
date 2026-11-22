package com.lycoris.loconautics.mixin.client.hold;

import com.lycoris.loconautics.client.hold.HoldInteractionManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class HoldKeyboardHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;",
            ordinal = 0, opcode = 180), cancellable = true)
    private void loconautics$holdKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (minecraft.screen != null || minecraft.player == null || minecraft.player.isSpectator()) return;
        if (HoldInteractionManager.onInput(false, key, scanCode, action)) ci.cancel();
    }
}
