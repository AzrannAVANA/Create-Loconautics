package com.lycoris.loconautics.client.hold;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.client.paletteswap.PaletteColors;
import com.lycoris.loconautics.client.paletteswap.PaletteSwap;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@OnlyIn(Dist.CLIENT)
public final class HoldGuiSkins implements ResourceManagerReloadListener {

    public static final HoldGuiSkins INSTANCE = new HoldGuiSkins();

    public record Skin(ResourceLocation texture, int tint) {
    }

    private static final ResourceLocation SHEET = LoconauticsConstants.id("textures/gui/hold/value.png");

    private static final ResourceLocation KNOB_SHEET = LoconauticsConstants.id("textures/block/controls/lever_atlas.png");

    private static final String SOURCE_COLOR = "brass";

    private static final String[] COLOR_BY_BODY = {"red", "industrial", "brass", "andesite"};

    private static final PaletteSwap.Knobs[] FRAME_KNOBS_BY_BODY = {
            new PaletteSwap.Knobs(0.9, 1.0, 1.2),
            new PaletteSwap.Knobs(0.5, 0.25, 2.3),
            PaletteSwap.Knobs.DEFAULT,
            new PaletteSwap.Knobs(0.6, 0.9, 1.6)};

    private static final int BRASS_BODY = 2;

    private static final PaletteSwap.Knobs HANDLE_KNOBS = new PaletteSwap.Knobs(0.6, 0.9, 1.4);

    private static final int KNOB_X = 2;

    private static final int KNOB_Y = 16;

    private static final int KNOB_STRIDE = 5;

    private static final int KNOB_SIZE = 4;

    private static final int KNOBS_PER_ROW = 5;

    private static final List<List<Integer>> CURSOR_AREAS = List.of(List.of(0, 14, 64, 14), List.of(0, 49, 22, 20));

    private static final List<List<Integer>> EVERYTHING_BUT_CURSOR = List.of(
            List.of(0, 0, 256, 14), List.of(0, 28, 256, 228), List.of(64, 14, 192, 14));

    private final Map<Integer, Skin> skins = new HashMap<>();

    private HoldGuiSkins() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Minecraft mc = Minecraft.getInstance();
        for (Skin skin : skins.values()) {
            mc.getTextureManager().release(skin.texture());
        }
        skins.clear();
    }

    public Skin get(int body, int knobColor) {
        int clampedBody = Mth.clamp(body, 0, COLOR_BY_BODY.length - 1);
        return skins.computeIfAbsent(clampedBody << 8 | knobColor, key -> build(clampedBody, knobColor));
    }

    private Skin build(int body, int knobColor) {
        Minecraft mc = Minecraft.getInstance();
        ResourceManager manager = mc.getResourceManager();
        PaletteColors palette = PaletteColors.load(manager);
        PaletteColors.Color origin = palette.get(SOURCE_COLOR);
        PaletteColors.Color frame = palette.get(COLOR_BY_BODY[body]);
        int[][] knob = knobRamp(manager, knobColor);
        if (origin == null || frame == null || knob == null) {
            LoconauticsConstants.LOGGER.warn("[HoldGui] missing palette for body {} knob {}", body, knobColor);
            return new Skin(SHEET, 0xFFFFFF);
        }
        try (InputStream stream = manager.getResourceOrThrow(SHEET).open(); NativeImage base = NativeImage.read(stream)) {
            NativeImage framed = body == BRASS_BODY ? copy(base)
                    : PaletteSwap.recolor(base, origin.ramp(), frame.ramp(), FRAME_KNOBS_BY_BODY[body], CURSOR_AREAS);
            NativeImage finished = PaletteSwap.recolor(framed, origin.ramp(), knob, HANDLE_KNOBS,
                    EVERYTHING_BUT_CURSOR);
            framed.close();
            ResourceLocation id = LoconauticsConstants.id("hold_gui/" + body + "_" + knobColor);
            mc.getTextureManager().register(id, new DynamicTexture(finished));
            return new Skin(id, tint(knob));
        } catch (IOException e) {
            LoconauticsConstants.LOGGER.warn("[HoldGui] could not read {}: {}", SHEET, e.getMessage());
            return new Skin(SHEET, 0xFFFFFF);
        }
    }

    private static NativeImage copy(NativeImage source) {
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, source.getWidth(), source.getHeight(), false);
        out.copyFrom(source);
        return out;
    }

    private static int[][] knobRamp(ResourceManager manager, int knobColor) {
        Optional<Resource> resource = manager.getResource(KNOB_SHEET);
        if (resource.isEmpty()) return null;
        int originX = KNOB_X + KNOB_STRIDE * (knobColor % KNOBS_PER_ROW);
        int originY = KNOB_Y + KNOB_STRIDE * (knobColor / KNOBS_PER_ROW);
        try (InputStream stream = resource.get().open(); NativeImage atlas = NativeImage.read(stream)) {
            TreeMap<Double, int[]> byBrightness = new TreeMap<>();
            for (int y = originY; y < originY + KNOB_SIZE; y++) {
                for (int x = originX; x < originX + KNOB_SIZE; x++) {
                    if (x >= atlas.getWidth() || y >= atlas.getHeight()) continue;
                    int abgr = atlas.getPixelRGBA(x, y);
                    if ((abgr >>> 24) == 0) continue;
                    int r = abgr & 0xFF;
                    int g = (abgr >> 8) & 0xFF;
                    int b = (abgr >> 16) & 0xFF;
                    byBrightness.put(r * 0.299 + g * 0.587 + b * 0.114, new int[]{r, g, b});
                }
            }
            List<int[]> shades = new ArrayList<>(byBrightness.values());
            if (shades.isEmpty()) return null;
            if (shades.size() == 1) shades.add(shades.get(0));
            return shades.toArray(new int[0][]);
        } catch (IOException e) {
            LoconauticsConstants.LOGGER.warn("[HoldGui] could not read {}: {}", KNOB_SHEET, e.getMessage());
            return null;
        }
    }

    private static int tint(int[][] ramp) {
        int[] mid = ramp[ramp.length / 2];
        return mid[0] << 16 | mid[1] << 8 | mid[2];
    }
}
