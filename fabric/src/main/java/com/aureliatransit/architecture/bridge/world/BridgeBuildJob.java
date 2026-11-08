package com.aureliatransit.architecture.bridge.world;

import com.aureliatransit.architecture.bridge.BridgeStampPlan;
import com.aureliatransit.architecture.bridge.BridgeTemplate;
import com.aureliatransit.architecture.station.StylePalette;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stamps a {@link BridgeStampPlan} into the world in small time slices. It never overwrites an MTR rail node or a block
 * entity, records what every cell held before the write for undo, and keeps a partial log if the server stops mid-build.
 */
final class BridgeBuildJob implements BridgeTask {

	record Entry(BlockPos pos, BlockState before, BlockState after) {
	}

	private final ServerWorld world;
	private final UUID owner;
	private final List<BridgeStampPlan.Placement> placements;
	private final BlockState[] states;
	private final List<Entry> undo = new ArrayList<>();
	private int index;
	private int placed;
	private int skipped;
	private int missing;
	private int ticks;
	private boolean finished;

	BridgeBuildJob(ServerWorld world, UUID owner, BridgeStampPlan plan, BridgeTemplate template) {
		this.world = world;
		this.owner = owner;
		placements = plan.placements();
		states = new BlockState[template.palette().size()];
		for (int i = 0; i < states.length; i++) {
			states[i] = BridgeStateCodec.decode(template.palette().get(i));
		}
	}

	@Override
	public boolean step(long deadline) {
		if (index >= placements.size()) {
			return true;
		}
		do {
			apply(placements.get(index++));
		} while (index < placements.size() && System.nanoTime() < deadline);
		if (index >= placements.size()) {
			return true;
		}
		ticks++;
		if (ticks % 20 == 0) {
			tell("Building bridge: " + (int) Math.round(100.0 * index / placements.size()) + "%", true);
		}
		return false;
	}

	private void apply(BridgeStampPlan.Placement placement) {
		if (placement.palette() < 0 || placement.palette() >= states.length || states[placement.palette()] == null) {
			missing++;
			return;
		}
		final BlockState target = BridgeStateCodec.rotate(states[placement.palette()], placement.quarterTurns());
		final BlockPos pos = new BlockPos(placement.x(), placement.y(), placement.z());
		final BlockState current = world.getBlockState(pos);
		if (current.equals(target)) {
			return;
		}
		if (current.hasBlockEntity() || Registries.BLOCK.getId(current.getBlock()).toString().equals(StylePalette.MTR_RAIL_NODE)) {
			skipped++;
			return;
		}
		if (!world.setBlockState(pos, target)) {
			skipped++;
			return;
		}
		undo.add(new Entry(pos, current, world.getBlockState(pos)));
		placed++;
	}

	@Override
	public void finish() {
		if (finished) {
			return;
		}
		finished = true;
		BridgeSessions.rememberUndo(owner, world.getRegistryKey(), undo);
		if (placed == 0 && skipped == 0 && missing == 0) {
			tell("The bridge already matches the template; nothing changed.", false);
		} else {
			tell("Bridge built: " + placed + " block" + (placed == 1 ? "" : "s") + " placed"
					+ (skipped == 0 ? "" : ", " + skipped + " left as they were (rail nodes or block entities)")
					+ (missing == 0 ? "" : ", " + missing + " skipped because a block type is not installed") + ".", false);
		}
	}

	private void tell(String message, boolean actionBar) {
		final ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
		if (player != null) {
			player.sendMessage(Text.literal(message), actionBar);
		}
	}
}
