package org.mtr.mod.gametest;

import com.aureliatransit.architecture.gametest.AirportMtrGameTests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.packet.PacketUpdateData;

/**
 * MTR #924: a rail node removed without a player breaking it (/setblock, /fill, a mod) left its rails in MTR.
 */
public final class RailNodeGameTests implements FabricGameTest {

	private static final int WAIT_TICKS = 200;

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = WAIT_TICKS * 3)
	public void railsGoWhenANodeIsReplaced(TestContext context) {
		final ServerWorld world = context.getWorld();
		final BlockPos node1 = context.getAbsolutePos(new BlockPos(1, 2, 1));
		final BlockPos node2 = context.getAbsolutePos(new BlockPos(1, 2, 7));
		context.setBlockState(1, 2, 1, org.mtr.mod.Blocks.RAIL_NODE.get().data);
		context.setBlockState(1, 2, 7, org.mtr.mod.Blocks.RAIL_NODE.get().data);
		final Position position1 = new Position(node1.getX(), node1.getY(), node1.getZ());
		final Position position2 = new Position(node2.getX(), node2.getY(), node2.getZ());
		PacketUpdateData.sendDirectlyToServerRail(new org.mtr.mapping.holder.ServerWorld(world), Rail.newRail(position1, Angle.S, position2, Angle.N, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), 80, 80, false, false, true, false, false, TransportMode.TRAIN));

		final Simulator simulator = AirportMtrGameTests.simulator(world);
		final int[] ticks = {0};
		final int[] removedAt = {-1};
		context.runAtEveryTick(() -> {
			ticks[0]++;
			final boolean hasRail = Data.tryGet(simulator.positionsToRail, position1, position2) != null;
			if (removedAt[0] < 0) {
				if (hasRail) {
					// No player: the same path /setblock and /fill take
					world.setBlockState(node1, Blocks.AIR.getDefaultState());
					removedAt[0] = ticks[0];
				} else if (ticks[0] > WAIT_TICKS) {
					throw new GameTestException("the rail never reached MTR");
				}
			} else if (!hasRail) {
				context.complete();
			} else if (ticks[0] - removedAt[0] > WAIT_TICKS) {
				throw new GameTestException("the node is gone but its rail is still in MTR");
			}
		});
	}
}
