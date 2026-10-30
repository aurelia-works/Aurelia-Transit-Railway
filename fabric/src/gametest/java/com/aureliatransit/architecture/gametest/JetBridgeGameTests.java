package com.aureliatransit.architecture.gametest;

import com.aureliatransit.architecture.airport.AircraftSample;
import com.aureliatransit.architecture.block.entity.JetBridgeBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.mtr.mod.block.PlatformHelper;

import java.util.List;

/**
 * The apron platform is an MTR platform block, so plane doors can open beside it, and a jet bridge steps out when a
 * stopped airplane with open doors is on top of it.
 */
public final class JetBridgeGameTests implements FabricGameTest {

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void apronPlatformOpensDoorsAndTheBridgeFollowsThem(TestContext context) {
		final Block apron = Registries.BLOCK.get(new Identifier("aurelia_transit_architecture", "apron_platform"));
		check(apron instanceof PlatformHelper, "apron platform is not a platform block");
		final Block bridge = Registries.BLOCK.get(new Identifier("aurelia_transit_architecture", "jet_bridge"));
		context.setBlockState(1, 2, 2, bridge.getDefaultState());
		final BlockPos pos = context.getAbsolutePos(new BlockPos(1, 2, 2));
		final BlockEntity entity = context.getWorld().getBlockEntity(pos);
		check(entity instanceof JetBridgeBlockEntity, "jet bridge block entity missing");
		final JetBridgeBlockEntity jet = (JetBridgeBlockEntity) entity;
		check(jet.extension() == 0f, "bridge started extended");
		final AircraftSample docked = new AircraftSample(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, false, 1, true);
		jet.drive(List.of(docked));
		final float extended = jet.extension();
		check(extended > 0f, "bridge did not extend");
		jet.drive(List.of(new AircraftSample(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, true, 1, true)));
		check(jet.extension() < extended, "bridge did not retract");
		context.complete();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}
