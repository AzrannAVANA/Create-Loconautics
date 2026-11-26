package com.lycoris.loconautics.content.boiler.slant;

import com.lycoris.loconautics.foundation.math.TiltedShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class SlantShapes {
    private SlantShapes() {
    }

    // blocks up the lean where a part on the host starts, and where one in the next diagonal cell starts
    public static final double FIRST_BASE = 0.5;
    public static final double NEXT_BASE = Math.sqrt(0.5) - 0.5;
    // a column piece is longer to fill its diagonal cell, the first one starts on the host
    public static final double FIRST_REACH = Math.sqrt(2.0) * 1.5 - 0.5 - FIRST_BASE;
    public static final double NEXT_REACH = Math.sqrt(2.0);

    public static VoxelShape tilt(VoxelShape flat, SlantSide side) {
        return tilt(flat, side, FIRST_BASE, 1.0);
    }

    public static VoxelShape tilt(VoxelShape flat, SlantSide side, double base, double reach) {
        if (!side.isSlanted() || flat.isEmpty()) return flat;
        SlantFrame frame = SlantFrame.of(side);
        List<AABB> boxes = flat.toAabbs().stream().map(box -> along(box, base, reach)).toList();
        return TiltedShape.of(Shapes.empty(), boxes, frame::toMount, frame::toPart,
                outline(along(flat.bounds(), base, reach), frame));
    }

    private static AABB along(AABB box, double base, double reach) {
        return new AABB(box.minX, base + (box.minY - 0.5) * reach, box.minZ,
                box.maxX, base + (box.maxY - 0.5) * reach, box.maxZ);
    }

    private static List<Vec3[]> outline(AABB box, SlantFrame frame) {
        Vec3[] corner = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corner[i] = frame.toMount(new Vec3((i & 1) == 0 ? box.minX : box.maxX,
                    (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ));
        }
        List<Vec3[]> edges = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) == 0) edges.add(new Vec3[]{corner[i], corner[i | bit]});
            }
        }
        return edges;
    }
}
