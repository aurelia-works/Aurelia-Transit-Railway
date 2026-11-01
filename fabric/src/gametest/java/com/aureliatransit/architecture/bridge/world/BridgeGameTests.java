package com.aureliatransit.architecture.bridge.world;

import com.aureliatransit.architecture.bridge.BridgeStampPlan;
import com.aureliatransit.architecture.bridge.BridgeTemplate;
import com.aureliatransit.architecture.station.Heading;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * B1 in a real server world: capture a small section, save and load it through the same JSON store the command uses,
 * sample an MTR rail, stamp it, build it and undo it. This is the proof that the command is not just a planner; it
 * writes and restores real blocks.
 */
public final class BridgeGameTests implements FabricGameTest {

	private static final int WAIT_TICKS = 100;

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = WAIT_TICKS)
	public void capturesStampsBuildsAndUndoes(TestContext context) throws Exception {
		final ServerWorld world = context.getWorld();
		for (int x = 0; x <= 10; x++) {
			for (int y = 0; y <= 6; y++) {
				for (int z = 0; z <= 10; z++) {
					context.setBlockState(x, y, z, Blocks.AIR);
				}
			}
		}

		// A three-block deck, a wall block and a stair, captured around a rail-level origin.
		context.setBlockState(1, 2, 2, Blocks.STONE);
		context.setBlockState(2, 2, 2, Blocks.STONE);
		context.setBlockState(3, 2, 2, Blocks.STONE);
		context.setBlockState(2, 3, 1, Blocks.STONE_BRICKS);
		context.setBlockState(1, 3, 2, Blocks.OAK_STAIRS);
		final BlockPos origin = context.getAbsolutePos(new BlockPos(2, 3, 2));
		final BridgeCapture.Result captured = BridgeCapture.capture(world,
				context.getAbsolutePos(new BlockPos(1, 2, 1)),
				context.getAbsolutePos(new BlockPos(3, 4, 3)),
				origin, Heading.NORTH);
		if (captured.template().cells().size() != 5) {
			throw new GameTestException("captured " + captured.template().cells().size() + " blocks, expected 5");
		}

		final Path folder = Files.createTempDirectory("atr-bridge-gametest");
		final BridgeTemplateStore store = new BridgeTemplateStore(folder);
		store.save("test", captured.template());
		final BridgeTemplate loaded = store.load("test");
		if (!loaded.palette().equals(captured.template().palette()) || !loaded.cells().equals(captured.template().cells())) {
			throw new GameTestException("the saved bridge template did not load back unchanged");
		}

		// A straight MTR rail through the test area, sampled by the same path adapter the command uses.
		final Position start = new Position(origin.getX() - 2, origin.getY(), origin.getZ());
		final Position end = new Position(origin.getX() + 8, origin.getY(), origin.getZ());
		final Rail rail = Rail.newRail(start, Angle.E, end, Angle.E, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), 80, 80, false, false, true, false, false, TransportMode.TRAIN);
		final BridgeStampPlan plan = BridgeStampPlan.of(loaded, BridgeRails.path(rail), 1.0, 0);
		if (plan.size() < 10) {
			throw new GameTestException("the rail produced only " + plan.size() + " placements");
		}

		final UUID owner = UUID.randomUUID();
		final BlockPos farDeck = context.getAbsolutePos(new BlockPos(6, 2, 2));
		if (!world.getBlockState(farDeck).isAir()) {
			throw new GameTestException("the far deck cell should start as air");
		}
		if (!BridgeSessions.start(owner, new BridgeBuildJob(world, owner, plan, loaded))) {
			throw new GameTestException("the bridge build did not start");
		}
		tickUntilDone(world, owner);
		if (!world.getBlockState(farDeck).isOf(Blocks.STONE)) {
			throw new GameTestException("the bridge did not place the deck at " + farDeck);
		}

		if (!BridgeSessions.undo(world, owner)) {
			throw new GameTestException("the bridge undo did not start");
		}
		tickUntilDone(world, owner);
		if (!world.getBlockState(farDeck).isAir()) {
			throw new GameTestException("the bridge undo left " + world.getBlockState(farDeck) + " at " + farDeck);
		}
		context.complete();
	}

	private static void tickUntilDone(ServerWorld world, UUID owner) {
		for (int i = 0; i < WAIT_TICKS && BridgeSessions.hasJob(owner); i++) {
			BridgeSessions.tick(world.getServer());
		}
		if (BridgeSessions.hasJob(owner)) {
			throw new GameTestException("the bridge task did not finish within " + WAIT_TICKS + " ticks");
		}
	}
}
