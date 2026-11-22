package com.lycoris.loconautics.client.hold;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID, value = Dist.CLIENT)
public final class HoldClientEvents {

    private HoldClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) HoldInteractionManager.stop();
        HoldInteractionManager.tick(mc.level, mc.player);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        HoldInteractionManager.renderOverlay(event.getGuiGraphics(), event.getGuiGraphics().guiWidth(),
                event.getGuiGraphics().guiHeight());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isLocalPlayer() || !HoldInteractionManager.isActive()) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !HoldInteractionManager.isActive()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }
}
