package com.lycoris.loconautics.content.boiler.slant;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// a part is authored on the flat top of its host, this moves it onto the slanted side the part leans to
public record SlantFrame(Direction lean) {
    private static final Vec3 CENTER = new Vec3(0.5, 0.5, 0.5);
    private static final Vec3 PART_CELL = new Vec3(0.0, 1.0, 0.0);
    private static final double TURN = -Math.PI / 4;

    public static SlantFrame of(SlantSide side) {
        return new SlantFrame(side.lean());
    }

    public Vec3 offset() {
        return new Vec3(lean.getStepX(), 1.0, lean.getStepZ());
    }

    public Vec3 toPart(Vec3 point) {
        return rotate(point.add(offset()), -TURN).subtract(PART_CELL);
    }

    public Vec3 toMount(Vec3 point) {
        return rotate(point.add(PART_CELL), TURN).subtract(offset());
    }

    private Vec3 rotate(Vec3 point, double angle) {
        Vec3 rel = point.subtract(CENTER);
        boolean leanX = lean.getAxis() == Direction.Axis.X;
        double side = leanX ? rel.x * lean.getStepX() : rel.z * lean.getStepZ();
        double along = leanX ? rel.z : rel.x;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double turnedSide = side * cos - rel.y * sin;
        double turnedHigh = side * sin + rel.y * cos;
        double x = leanX ? turnedSide * lean.getStepX() : along;
        double z = leanX ? along : turnedSide * lean.getStepZ();
        return CENTER.add(x, turnedHigh, z);
    }

    public Matrix4f partToMount() {
        Vec3 origin = toMount(Vec3.ZERO);
        Vec3 x = toMount(new Vec3(1, 0, 0)).subtract(origin);
        Vec3 y = toMount(new Vec3(0, 1, 0)).subtract(origin);
        Vec3 z = toMount(new Vec3(0, 0, 1)).subtract(origin);
        return new Matrix4f(
                (float) x.x, (float) x.y, (float) x.z, 0f,
                (float) y.x, (float) y.y, (float) y.z, 0f,
                (float) z.x, (float) z.y, (float) z.z, 0f,
                (float) origin.x, (float) origin.y, (float) origin.z, 1f);
    }
}
