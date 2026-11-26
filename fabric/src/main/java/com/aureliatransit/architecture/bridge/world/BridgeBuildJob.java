package com.aureliatransit.architecture.bridge.world;

import com.aureliatransit.architecture.block.BridgeMemberBlock;
import com.aureliatransit.architecture.bridge.BridgeStampPlan;
import com.aureliatransit.architecture.station.StylePalette;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

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
	private final Map<BlockPos, NbtCompound> entities;
	private final Consumer<List<Entry>> done;
	private final List<Entry> undo = new ArrayList<>();
	private int index;
	private int placed;
	private int skipped;
	private int missing;
	private int ticks;
	private boolean finished;

	/**
	 * A build from placements and their palette, as the rail bridge builder plans them. {@code entities} is block entity data
	 * written into the cells that get it; {@code done} receives the undo log when the build finishes.
	 */
	BridgeBuildJob(ServerWorld world, UUID owner, List<BridgeStampPlan.Placement> placements, List<String> palette, Map<BlockPos, NbtCompound> entities,
			Consumer<List<Entry>> done) {
		this.world = world;
		this.owner = owner;
		this.placements = placements;
		this.entities = entities;
		this.done = done;
		states = new BlockState[palette.size()];
		for (int i = 0; i < states.length; i++) {
			states[i] = BridgeStateCodec.decode(palette.get(i));
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
		final NbtCompound data = entities.get(pos);
		if (current.equals(target) && data == null) {
			return;
		}
		// a bridge member block is ours to replace; any other block entity is not
		if ((current.hasBlockEntity() && !(current.getBlock() instanceof BridgeMemberBlock))
				|| Registries.BLOCK.getId(current.getBlock()).toString().equals(StylePalette.MTR_RAIL_NODE)) {
			skipped++;
			return;
		}
		if (!current.equals(target) && !world.setBlockState(pos, target)) {
			skipped++;
			return;
		}
		if (data != null) {
			final BlockEntity entity = world.getBlockEntity(pos);
			if (entity != null) {
				entity.readNbt(data);
				entity.markDirty();
				world.updateListeners(pos, target, target, Block.NOTIFY_LISTENERS);
			}
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
		done.accept(List.copyOf(undo));
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
