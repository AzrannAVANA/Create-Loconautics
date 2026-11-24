package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerMenu;
import com.lycoris.loconautics.content.boiler.bogey.BoilerBogeyBlockEntity;
import com.lycoris.loconautics.content.boiler.bogey.BoilerBogeyWheels;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class BoilerBogeyScenes {

    private static final BlockPos TRACK_FROM = new BlockPos(0, 1, 3);
    private static final BlockPos TRACK_TO = new BlockPos(6, 1, 3);
    private static final BlockPos BOGEY = new BlockPos(3, 2, 3);
    private static final BlockPos FIREBOX = new BlockPos(2, 3, 3);
    private static final BlockPos BODY_FROM = new BlockPos(3, 3, 3);
    private static final BlockPos BODY_TO = new BlockPos(5, 3, 3);

    public static void wheels(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "bogey_wheels", "Fitting Wheels to Boiler Bogeys");

        scene.world().showSection(util.select().fromTo(TRACK_FROM, TRACK_TO), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(BOGEY), Direction.DOWN);
        scene.idle(25);
        scene.overlay().showText(60)
                .text("Boiler Bogeys roll along Track on a single pair of wheels")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BOGEY));
        scene.idle(70);

        scene.overlay().showText(70)
                .text("Right click with Train Wheels to fit up to 4 pairs")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BOGEY));
        for (int pairs = 2; pairs <= BoilerBogeyWheels.MAX_PAIRS; pairs++) {
            scene.overlay().showControls(util.vector().topOf(BOGEY), Pointing.DOWN, 20)
                    .rightClick()
                    .withItem(new ItemStack(LoconauticsRegistries.TRAIN_WHEEL.get()));
            scene.idle(10);
            setWheels(scene, util, pairs);
            scene.idle(20);
        }
        scene.idle(20);

        scene.overlay().showText(70)
                .text("Every pair after the first adds pull and draws more steam")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BOGEY));
        scene.idle(80);

        scene.overlay().showText(70)
                .text("Sneak and Right click with a Wrench to take a pair off")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BOGEY));
        for (int pairs = BoilerBogeyWheels.MAX_PAIRS - 1; pairs >= 1; pairs--) {
            scene.overlay().showControls(util.vector().topOf(BOGEY), Pointing.DOWN, 20)
                    .rightClick()
                    .whileSneaking()
                    .withItem(new ItemStack(AllItems.WRENCH.get()));
            scene.idle(10);
            setWheels(scene, util, pairs);
            scene.idle(20);
        }
        scene.idle(30);
    }

    public static void control(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "bogey_control", "Driving Boiler Bogeys");

        scene.world().showSection(util.select().fromTo(TRACK_FROM, TRACK_TO), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(BOGEY), Direction.DOWN);
        scene.idle(20);

        GuiPonder.Panel panel = GuiPonder.open(scene, GuiPonder.FOUR_ROWS,
                Component.translatable(AnalogControllerMenu.FORWARD_KEY),
                Component.translatable(AnalogControllerMenu.BACKWARD_KEY),
                Component.translatable(AnalogControllerMenu.LEFT_KEY),
                Component.translatable(AnalogControllerMenu.RIGHT_KEY));

        scene.overlay().showControls(util.vector().topOf(BOGEY), Pointing.DOWN, 30)
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(50)
                .text("Right click it with empty hands to open its Interface")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BOGEY));
        scene.idle(55);

        panel.show(scene);
        scene.idle(20);
        panel.mark(scene, 0);
        scene.overlay().showText(80)
                .text("The strength of the Speed signal sets the throttle")
                .attachKeyFrame()
                .placeNearTarget();
        fill(scene, panel, 0, Items.GOLD_INGOT, Items.REDSTONE);

        panel.mark(scene, 1);
        scene.overlay().showText(80)
                .text("A signal on the Direction row makes it reverse")
                .attachKeyFrame()
                .placeNearTarget();
        fill(scene, panel, 1, Items.IRON_INGOT, Items.LAPIS_LAZULI);

        panel.mark(scene, 2);
        scene.overlay().showText(80)
                .text("The Left row steers the bogey to the left")
                .attachKeyFrame()
                .placeNearTarget();
        fill(scene, panel, 2, Items.COPPER_INGOT, Items.EMERALD);

        panel.mark(scene, 3);
        scene.overlay().showText(80)
                .text("The Right row steers the bogey to the right")
                .attachKeyFrame()
                .placeNearTarget();
        fill(scene, panel, 3, Items.QUARTZ, Items.AMETHYST_SHARD);

        panel.mark(scene, -1);
        panel.hide(scene);
        scene.idle(15);
    }

    public static void boiler(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "bogey_boiler", "Boiler Bogeys and the Steam Pool");

        scene.world().showSection(util.select().fromTo(TRACK_FROM, TRACK_TO), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(BOGEY), Direction.DOWN);
        scene.idle(20);

        scene.world().showSection(util.select().fromTo(FIREBOX, BODY_TO), Direction.DOWN);
        scene.idle(25);
        scene.overlay().showText(60)
                .text("A Firebox and Boiler Bodies above it make the Boiler that drives the bogey")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(BODY_FROM, Direction.WEST));
        scene.idle(70);

        scene.world().modifyBlocks(util.select().position(FIREBOX),
                state -> state.setValue(BlockStateProperties.LIT, true), false);
        scene.idle(10);
        scene.overlay().showText(70)
                .text("It only drives while the Boiler holds steam pressure")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BOGEY));
        scene.idle(80);

        scene.overlay().showText(70)
                .text("Driving takes steam from the Boiler pool, ahead of a Steam Takeoff")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BOGEY));
        scene.idle(80);

        scene.overlay().showOutline(PonderPalette.GREEN, "fuel", util.select().position(FIREBOX), 70);
        scene.overlay().showText(70)
                .text("The more of the pool it uses, the more fuel and water the Boiler consumes")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(FIREBOX, Direction.WEST));
        scene.idle(80);

        scene.overlay().showText(70)
                .text("More wheel pairs and a heavier train use more of the pool")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BOGEY));
        scene.idle(80);

        scene.overlay().showText(70)
                .text("Asking for more than the pool holds stops the bogey completely")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BOGEY));
        scene.idle(80);
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }

    private static void setWheels(CreateSceneBuilder scene, SceneBuildingUtil util, int pairs) {
        scene.world().modifyBlockEntityNBT(util.select().position(BOGEY), BoilerBogeyBlockEntity.class,
                tag -> tag.getCompound("BogeyData").putInt(BoilerBogeyWheels.KEY, pairs));
    }

    private static void fill(CreateSceneBuilder scene, GuiPonder.Panel panel, int row, Item first, Item second) {
        scene.idle(20);
        panel.put(scene, row * 2, new ItemStack(first));
        scene.idle(20);
        panel.put(scene, row * 2 + 1, new ItemStack(second));
        scene.idle(50);
    }
}
