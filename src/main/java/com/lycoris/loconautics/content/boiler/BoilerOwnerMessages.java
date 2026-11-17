package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// server only, tells a train owner their boiler ran out, now if they are on, at login if they are not
@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class BoilerOwnerMessages extends SavedData {

    private static final String DATA_NAME = "loconautics_boiler_owner_messages";
    private static final double NEARBY = 64.0;

    private final Map<UUID, List<String>> waiting = new HashMap<>();

    public static BoilerOwnerMessages get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(BoilerOwnerMessages::new, BoilerOwnerMessages::load, null), DATA_NAME);
    }

    public static void tell(SableTrain train, String problem) {
        MinecraftServer server = train.level().getServer();
        if (server == null) return;
        String key = "loconautics.boiler.stopped." + problem;
        UUID owner = train.owner();
        if (owner == null) {
            ChunkPos chunk = train.bodyChunk();
            if (chunk == null) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                double dx = player.getX() - chunk.getMiddleBlockX();
                double dz = player.getZ() - chunk.getMiddleBlockZ();
                if (dx * dx + dz * dz <= NEARBY * NEARBY) send(player, key);
            }
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) {
            send(online, key);
            return;
        }
        BoilerOwnerMessages store = get(server);
        store.waiting.computeIfAbsent(owner, id -> new ArrayList<>()).add(key);
        store.setDirty();
    }

    private static void send(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        BoilerOwnerMessages store = get(player.getServer());
        List<String> keys = store.waiting.remove(player.getUUID());
        if (keys == null) return;
        for (String key : keys) send(player, key);
        store.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, List<String>> entry : waiting.entrySet()) {
            CompoundTag owner = new CompoundTag();
            owner.putUUID("Owner", entry.getKey());
            ListTag keys = new ListTag();
            for (String key : entry.getValue()) keys.add(StringTag.valueOf(key));
            owner.put("Keys", keys);
            owners.add(owner);
        }
        tag.put("Owners", owners);
        return tag;
    }

    private static BoilerOwnerMessages load(CompoundTag tag, HolderLookup.Provider registries) {
        BoilerOwnerMessages store = new BoilerOwnerMessages();
        ListTag owners = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++) {
            CompoundTag owner = owners.getCompound(i);
            List<String> keys = new ArrayList<>();
            ListTag list = owner.getList("Keys", Tag.TAG_STRING);
            for (int k = 0; k < list.size(); k++) keys.add(list.getString(k));
            store.waiting.put(owner.getUUID("Owner"), keys);
        }
        return store;
    }
}
