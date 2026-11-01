package com.aureliatransit.architecture.bridge.world;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Puts back what a bridge build changed, in reverse write order. A cell is restored only while it still holds the block
 * the build put there; a cell the player changed since is left alone.
 */
final class BridgeUndoJob implements BridgeTask {

	private final ServerWorld world;
	private final UUID owner;
	private final List<BridgeBuildJob.Entry> entries;
	private final int total;
	private int index;
	private int restored;
	private int kept;
	private int ticks;
	private boolean finished;

	BridgeUndoJob(ServerWorld world, UUID owner, List<BridgeBuildJob.Entry> entries) {
		this.world = world;
		this.owner = owner;
		this.entries = new ArrayList<>(entries);
		Collections.reverse(this.entries);
		total = this.entries.size();
	}

	@Override
	public boolean step(long deadline) {
		if (index >= total) {
			return true;
		}
		do {
			final BridgeBuildJob.Entry entry = entries.get(index++);
			if (world.getBlockState(entry.pos()).equals(entry.after())) {
				if (world.setBlockState(entry.pos(), entry.before())) {
					restored++;
				}
			} else {
				kept++;
			}
		} while (index < total && System.nanoTime() < deadline);
		if (index >= total) {
			return true;
		}
		ticks++;
		if (ticks % 20 == 0) {
			tell("Undoing bridge: " + (int) Math.round(100.0 * index / total) + "%", true);
		}
		return false;
	}

	@Override
	public void finish() {
		if (finished) {
			return;
		}
		finished = true;
		BridgeSessions.clearUndo(owner);
		tell("Undo: " + restored + " block" + (restored == 1 ? "" : "s") + " restored"
				+ (kept == 0 ? "" : ", " + kept + " left as they are because they changed since the build") + ".", false);
	}

	private void tell(String message, boolean actionBar) {
		final ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
		if (player != null) {
			player.sendMessage(Text.literal(message), actionBar);
		}
	}
}
