package com.lycoris.loconautics.content.controls.panellever;

import com.lycoris.loconautics.client.hold.HoldGuiSkins;
import com.lycoris.loconautics.client.hold.HoldInteraction;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.network.packets.controls.LeverSetPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class LeverHoldInteraction extends HoldInteraction {

    private static final LeverHoldInteraction INSTANCE = new LeverHoldInteraction();

    private static final int BAR_LENGTH = 100;

    private static final int BAR_THICKNESS = 30;

    private static final int TRACK_END = 7;

    private static final int TRACK_TILE = 60;

    private static final int STRIP_BORDER = 3;

    private static final int STRIP_HEIGHT = 13;

    private static final int CURSOR_HEIGHT = 14;

    private static final int CURSOR_BODY = 32;

    private static final int CURSOR_CAP = 3;

    private static final int TRAVEL = 40;

    private static final int TEXT_COLOR = 0x4A3A28;

    private static final float CHASE = 0.4f;

    private PanelSlot slot = PanelSlot.BOTTOM_LEFT;

    private boolean inverted;

    private int body;

    private int knobColor;

    private int signal;

    private int sentSignal;

    private float value;

    private float animatedValue;

    private float lastAnimatedValue;

    public static boolean gripped() {
        return INSTANCE.isActive();
    }

    public static boolean grab(Level level, Player player, BlockPos panelPos, PanelSlot slot, int power,
                               boolean inverted, int body, int knobColor) {
        if (INSTANCE.isActive()) return false;
        INSTANCE.slot = slot;
        INSTANCE.inverted = inverted;
        INSTANCE.body = body;
        INSTANCE.knobColor = knobColor;
        INSTANCE.signal = INSTANCE.sentSignal = LeverPart.clamp(power);
        INSTANCE.value = INSTANCE.signal / (float) LeverPart.MAX_POWER;
        INSTANCE.animatedValue = INSTANCE.lastAnimatedValue = INSTANCE.value;
        INSTANCE.startHold(level, player, panelPos);
        return true;
    }

    private static void trackSlice(GuiGraphics graphics, ResourceLocation sheet, int x, int y, int height, int u,
                                   int width, boolean mirrored) {
        int uWidth = mirrored ? -width : width;
        float startU = mirrored ? u + width : u;
        int middle = height - 2 * STRIP_BORDER;
        int middleSource = STRIP_HEIGHT - 2 * STRIP_BORDER;
        graphics.blit(sheet, x, y, width, STRIP_BORDER, startU, 0f, uWidth, STRIP_BORDER, 256, 256);
        graphics.blit(sheet, x, y + STRIP_BORDER, width, middle, startU, (float) STRIP_BORDER, uWidth, middleSource,
                256, 256);
        graphics.blit(sheet, x, y + STRIP_BORDER + middle, width, STRIP_BORDER, startU,
                (float) (STRIP_HEIGHT - STRIP_BORDER), uWidth, STRIP_BORDER, 256, 256);
    }

    @Override
    public boolean activeTick(Level level, LocalPlayer player) {
        if (getInteractionPos() == null) return true;
        if (!inInteractionRange(player, getInteractionPos().getCenter(), 0.0)) return true;
        lastAnimatedValue = animatedValue;
        animatedValue += (signal / (float) LeverPart.MAX_POWER - animatedValue) * CHASE;
        return false;
    }

    @Override
    public boolean activeOnMouseMove(double yaw, double pitch) {
        value = Math.min(1f, Math.max(0f, value - (float) (pitch / 180.0)));
        signal = LeverPart.clamp(Math.round(value * LeverPart.MAX_POWER));
        if (signal != sentSignal) {
            sentSignal = signal;
            PacketDistributor.sendToServer(
                    new LeverSetPacket(getInteractionPos(), slot.ordinal(), signal));
        }
        return true;
    }

    @Override
    public void renderOverlay(GuiGraphics graphics, int width, int height, boolean hideGui) {
        if (hideGui) return;

        HoldGuiSkins.Skin skin = HoldGuiSkins.INSTANCE.get(body, knobColor);
        ResourceLocation sheet = skin.texture();
        int x = width / 2 - BAR_LENGTH / 2 + 16;
        int y = height / 2 - BAR_THICKNESS / 2;
        float centerX = x + BAR_LENGTH / 2f;
        float centerY = y + BAR_THICKNESS / 2f;
        float partialTick = AnimationTickHolder.getPartialTicks();
        float shown = Mth.clamp(lastAnimatedValue * (1f - partialTick) + animatedValue * partialTick, 0f, 1f);
        PoseStack ms = graphics.pose();

        ms.pushPose();
        ms.translate(centerX, centerY, 0f);
        ms.mulPose(Axis.ZP.rotationDegrees(90f));
        ms.translate(-centerX, -centerY, 0f);
        graphics.blit(sheet, x, y, 65, 14, 4, 4);
        graphics.blit(sheet, x + BAR_LENGTH - 4, y, 70, 14, 4, 4);
        graphics.blit(sheet, x, y + BAR_THICKNESS - 4, 65, 24, 4, 4);
        graphics.blit(sheet, x + BAR_LENGTH - 4, y + BAR_THICKNESS - 4, 70, 24, 4, 4);
        graphics.blit(sheet, x, y + 4, 3, BAR_THICKNESS - 8, 65f, 19f, 3, 4, 256, 256);
        graphics.blit(sheet, x + BAR_LENGTH - 3, y + 4, 3, BAR_THICKNESS - 8, 71f, 19f, 3, 4, 256, 256);
        graphics.blit(sheet, x + 4, y, 0, 29, 92, 3);
        graphics.blit(sheet, x + 4, y + BAR_THICKNESS - 3, 0, 32, 92, 3);
        int trackY = y + 3;
        int trackHeight = BAR_THICKNESS - 6;
        trackSlice(graphics, sheet, x + 3, trackY, trackHeight, 0, TRACK_END, false);
        for (int done = 0; done < 94 - 2 * TRACK_END; done += TRACK_TILE) {
            trackSlice(graphics, sheet, x + 3 + TRACK_END + done, trackY, trackHeight, TRACK_END,
                    Math.min(TRACK_TILE, 94 - 2 * TRACK_END - done), false);
        }
        trackSlice(graphics, sheet, x + 97 - TRACK_END, trackY, trackHeight, 0, TRACK_END, true);

        int trackStart = x + 3;
        int trackSpan = 93;
        int filledFrom = trackStart + Math.round((1f - shown) * trackSpan);
        int fillColor = 0x44000000 | skin.tint();
        if (inverted) graphics.fill(trackStart, y + 3, filledFrom, y + BAR_THICKNESS - 3, fillColor);
        else graphics.fill(filledFrom, y + 3, trackStart + 94, y + BAR_THICKNESS - 3, fillColor);
        for (int step = 0; step <= LeverPart.MAX_POWER; step++) {
            int tickX = trackStart + Math.round((LeverPart.MAX_POWER - step) * trackSpan / (float) LeverPart.MAX_POWER);
            int tickColor = step % 5 == 0 ? 0x77FFFFFF : 0x33FFFFFF;
            int tickLength = step % 5 == 0 ? 5 : 3;
            graphics.fill(tickX, y + 3, tickX + 1, y + 3 + tickLength, tickColor);
            graphics.fill(tickX, y + BAR_THICKNESS - 3 - tickLength, tickX + 1, y + BAR_THICKNESS - 3, tickColor);
        }
        ms.popPose();

        ms.pushPose();
        ms.translate(0f, 0f, 4f);
        int cursorX = Math.round(centerX - CURSOR_BODY / 2f);
        int cursorY = Math.round(centerY - (shown - 0.5f) * 2f * TRAVEL - CURSOR_HEIGHT / 2f);
        graphics.blit(sheet, cursorX - CURSOR_CAP, cursorY, 0, 14, CURSOR_CAP, CURSOR_HEIGHT);
        graphics.blit(sheet, cursorX, cursorY, 4, 14, CURSOR_BODY, CURSOR_HEIGHT);
        graphics.blit(sheet, cursorX + CURSOR_BODY, cursorY, 61, 14, CURSOR_CAP, CURSOR_HEIGHT);
        ms.translate(0f, 0f, 4f);
        String text = String.valueOf(inverted ? LeverPart.MAX_POWER - signal : signal);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, text, Math.round(centerX - font.width(text) / 2f), cursorY + 3, TEXT_COLOR, false);
        ms.popPose();
    }
}
