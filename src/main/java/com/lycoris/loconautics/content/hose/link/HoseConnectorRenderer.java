package com.lycoris.loconautics.content.hose.link;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.common.strand.StrandRenderer;
import com.lycoris.loconautics.content.hose.HoseNozzleKnot;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public class HoseConnectorRenderer implements BlockEntityRenderer<HoseConnectorBlockEntity> {

    private static final double HOLD_OFFSET = 0.1875;

    private static final double KNOT_PUSH = 1.0 / 16.0;

    public HoseConnectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HoseConnectorBlockEntity be, float partialTicks, PoseStack ps,
                       MultiBufferSource buffer, int light, int overlay) {
        if (be.previewStrand() != null && be.getLevel() != null) {
            com.lycoris.loconautics.client.HoseStrandDraw.draw(ps, buffer, be.getLevel(), be.getBlockPos(),
                    be.getBlockState(), be.previewStrand());
        }
        SteelCableHolderBehaviour holder = be.getBehaviour(SteelCableHolderBehaviour.TYPE);
        if (holder == null || holder.attachedStrandIds().isEmpty()) {
            return;
        }
        Direction open = be.plateSide().getOpposite();
        SuperByteBuffer knot = CachedBuffers.partialFacing(
                LoconauticsPartialModels.HOSE_STRAND_KNOT, be.getBlockState(), Direction.NORTH);
        Quaternionf along = open == Direction.DOWN ? new Quaternionf().rotationX((float) Math.PI)
                : StrandRenderer.rotationTo(new Vector3d(0.0, 1.0, 0.0),
                        new Vector3d(open.getStepX(), open.getStepY(), open.getStepZ()));
        double reach = HOLD_OFFSET + KNOT_PUSH;
        ps.pushPose();
        ps.translate(0.5 + open.getStepX() * reach, 0.5 + open.getStepY() * reach, 0.5 + open.getStepZ() * reach);
        ps.mulPose(along);
        ps.scale(HoseNozzleKnot.INFLATE, HoseNozzleKnot.INFLATE, HoseNozzleKnot.INFLATE);
        ps.translate(-0.5, -0.5, -0.5);
        knot.light(light).renderInto(ps, buffer.getBuffer(RenderType.solid()));
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(HoseConnectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.0);
    }
}
