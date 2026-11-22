package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record HoseType(
        ResourceLocation id,
        int maxLength,
        int bufferSize,
        float spillParticleRate,
        ResourceLocation strandTexture,
        float reelTierSpeedMultiplier,
        float maxRopeStretchAllowed,
        float snapStretchPercent
) {

    private static final Map<ResourceLocation, HoseType> REGISTRY = new ConcurrentHashMap<>();

    public static final HoseType DEFAULT = register(new HoseType(
            LoconauticsConstants.id("default"),
            28,
            1000,
            2.0f,
            LoconauticsConstants.id("block/hose/hose"),
            1.0f,
            25.0f,
            50.0f));

    @Override
    public int maxLength() {
        if (this == DEFAULT && LoconauticsConfig.SERVER_SPEC.isLoaded()) {
            return LoconauticsConfig.HOSE_MAX_LENGTH.get();
        }
        return maxLength;
    }

    public static HoseType register(HoseType type) {
        REGISTRY.put(type.id(), type);
        return type;
    }

    public static HoseType get(ResourceLocation id) {
        return REGISTRY.getOrDefault(id, DEFAULT);
    }
}
