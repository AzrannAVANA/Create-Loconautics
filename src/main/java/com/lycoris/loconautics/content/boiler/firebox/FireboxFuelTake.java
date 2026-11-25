package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class FireboxFuelTake {
    private FireboxFuelTake() {
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown() || !(event.getItemStack().getItem() instanceof ShovelItem)) {
            return;
        }
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof FireboxBlock) || state.getValue(FireboxBlock.EXTENSION)) {
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (!level.isClientSide && level.getBlockEntity(event.getPos()) instanceof FireboxBlockEntity controller) {
            controller.dropFuel();
        }
    }
}
