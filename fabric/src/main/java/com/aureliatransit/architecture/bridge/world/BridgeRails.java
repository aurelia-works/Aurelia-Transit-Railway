package com.aureliatransit.architecture.bridge.world;

import com.aureliatransit.architecture.bridge.BridgeStampPlan;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.mod.item.ItemNodeModifierBase;

import java.util.function.Consumer;

/**
 * The two MTR calls the bridge generator needs: find the rail between two nodes (the same lookup the rail modifier items
 * use) and sample it for {@link BridgeStampPlan}. Kept here so the template and plan classes stay free of MTR and
 * Minecraft types and can be tested without a server.
 */
final class BridgeRails {

	private BridgeRails() {
	}

	static void getRail(ServerWorld world, ServerPlayerEntity player, BlockPos from, BlockPos to, Consumer<Rail> consumer) {
		ItemNodeModifierBase.getRail(
				new org.mtr.mapping.holder.World(world),
				new org.mtr.mapping.holder.BlockPos(from.getX(), from.getY(), from.getZ()),
				new org.mtr.mapping.holder.BlockPos(to.getX(), to.getY(), to.getZ()),
				new org.mtr.mapping.holder.ServerPlayerEntity(player),
				consumer
		);
	}

	static BridgeStampPlan.Path path(Rail rail) {
		return new BridgeStampPlan.Path() {

			private final double length = Math.max(0, rail.railMath.getLength());

			@Override
			public double length() {
				return length;
			}

			@Override
			public BridgeStampPlan.Sample at(double distance) {
				final double clamped = Math.max(0, Math.min(length, distance));
				final Vector point = rail.railMath.getPosition(clamped, false);
				final Vector ahead = rail.railMath.getPosition(Math.min(length, clamped + 0.1), false);
				final Vector behind = rail.railMath.getPosition(Math.max(0, clamped - 0.1), false);
				return new BridgeStampPlan.Sample(point.x, point.y, point.z, ahead.x - behind.x, ahead.z - behind.z);
			}
		};
	}
}
