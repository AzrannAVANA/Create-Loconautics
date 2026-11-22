package com.lycoris.loconautics.mixin.client.hold;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyMapping.class)
public interface HoldKeyMappingInvoker {

    @Invoker("release")
    void invokeRelease();
}
