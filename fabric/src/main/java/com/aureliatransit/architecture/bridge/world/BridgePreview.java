package com.aureliatransit.architecture.bridge.world;

import com.aureliatransit.architecture.bridge.BridgeStampPlan;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * A 30 second particle outline of a bridge plan, shown to the planning player only. It changes nothing in the world.
 * Large plans are sampled instead of drawing every block, so a long bridge cannot flood the client with particles.
 */
final class BridgePreview {

	static final int DURATION_TICKS = 600;
	private static final int REFRESH_TICKS = 10;
	private static final int MAX_POINTS = 400;
	private static final DustParticleEffect EFFECT = new DustParticleEffect(new Vector3f(0.2F, 0.6F, 1.0F), 1.0F);

	private final UUID player;
	private final RegistryKey<World> dimension;
	private final List<BridgeStampPlan.Placement> placements;
	private int age;

	BridgePreview(UUID player, RegistryKey<World> dimension, BridgeStampPlan plan) {
		this.player = player;
		this.dimension = dimension;
		placements = plan.placements();
	}

	UUID player() {
		return player;
	}

	/** One server tick; false when the preview is over (30 s, player gone). */
	boolean tick(MinecraftServer server) {
		age++;
		if (age > DURATION_TICKS) {
			return false;
		}
		final ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(player);
		if (viewer == null) {
			return false;
		}
		if ((age - 1) % REFRESH_TICKS != 0) {
			return true;
		}
		final ServerWorld world = server.getWorld(dimension);
		if (world == null || viewer.getServerWorld() != world || viewer.networkHandler == null) {
			return true;
		}
		final int stride = Math.max(1, (placements.size() + MAX_POINTS - 1) / MAX_POINTS);
		for (int i = 0; i < placements.size(); i += stride) {
			final BridgeStampPlan.Placement placement = placements.get(i);
			world.spawnParticles(viewer, EFFECT, true, placement.x() + 0.5, placement.y() + 0.5, placement.z() + 0.5, 1, 0, 0, 0, 0);
		}
		return true;
	}
}
