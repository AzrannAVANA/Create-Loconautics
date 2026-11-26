package com.lycoris.loconautics.content.boiler.slant;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

public enum SlantSide implements StringRepresentable {
    NONE("none", null),
    NORTH("north", Direction.NORTH),
    EAST("east", Direction.EAST),
    SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST);

    public static final EnumProperty<SlantSide> SLANT = EnumProperty.create("slant", SlantSide.class);

    private final String name;
    @Nullable private final Direction lean;

    SlantSide(String name, @Nullable Direction lean) {
        this.name = name;
        this.lean = lean;
    }

    public boolean isSlanted() {
        return lean != null;
    }

    @Nullable
    public Direction lean() {
        return lean;
    }

    public static SlantSide of(Direction lean) {
        return switch (lean) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NONE;
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
