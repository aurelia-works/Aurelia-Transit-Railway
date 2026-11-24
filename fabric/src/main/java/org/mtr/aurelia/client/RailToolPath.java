package org.mtr.aurelia.client;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import org.mtr.core.data.Position;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ItemUsageContext;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.item.ItemBlockClickingBase;
import org.mtr.mod.packet.PacketApplyRailAction;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Client half of the multi-node bridge, tunnel, tunnel wall and rail builder tools (MTR 4.1). On the second click, if the
 * two nodes are not joined by one rail, the shortest run of track between them is found in the client's rail data and
 * sent to the server, which checks it and applies the tool to each rail.
 */
public final class RailToolPath {

	/** Enough for any real network; a bigger search means the click was not meant as a path. */
	private static final int MAX_VISITED = 200_000;

	private RailToolPath() {
	}

	/**
	 * Called client-side when a selectable rail tool is used on a block.
	 *
	 * @param onNode whether the clicked block is a node this tool works on
	 */
	public static void onClientUse(ItemUsageContext context, boolean onNode) {
		final PlayerEntity player = context.getPlayer();
		if (!onNode || player == null || player.isSneaking() || !RailToolSettings.multiNode()) {
			return;
		}
		final NbtCompound nbt = context.getStack().data.getNbt();
		if (nbt == null || !nbt.contains(ItemBlockClickingBase.TAG_POS)) {
			return;
		}

		final BlockPos start = BlockPos.fromLong(nbt.getLong(ItemBlockClickingBase.TAG_POS));
		final LongArrayList path = findPath(MinecraftClientData.getInstance().positionsToRail, Init.blockPosToPosition(start), Init.blockPosToPosition(context.getBlockPos()), PacketApplyRailAction.MAX_SEGMENTS);
		// One rail is the ordinary end click; only a longer run needs the path
		if (path != null && path.size() > 2) {
			InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketApplyRailAction(context.data.getHand() == Hand.OFF_HAND, path));
		}
	}

	/**
	 * Breadth-first search for the run of track with the fewest rails between two nodes.
	 *
	 * @return the nodes from start to end as {@link BlockPos#asLong()}, or {@code null} when they are not connected within
	 * {@code maxSegments} rails
	 */
	@Nullable
	static LongArrayList findPath(Map<Position, ? extends Map<Position, ?>> positionsToRail, Position start, Position end, int maxSegments) {
		if (start.equals(end) || !positionsToRail.containsKey(start)) {
			return null;
		}

		final Object2ObjectOpenHashMap<Position, Position> parents = new Object2ObjectOpenHashMap<>();
		final Object2IntOpenHashMap<Position> depths = new Object2IntOpenHashMap<>();
		final ObjectArrayList<Position> queue = new ObjectArrayList<>();
		parents.put(start, start);
		depths.put(start, 0);
		queue.add(start);

		boolean found = false;
		for (int index = 0; index < queue.size() && queue.size() < MAX_VISITED; index++) {
			final Position current = queue.get(index);
			if (current.equals(end)) {
				found = true;
				break;
			}
			final int depth = depths.getInt(current);
			final Map<Position, ?> neighbours = positionsToRail.get(current);
			if (depth >= maxSegments || neighbours == null) {
				continue;
			}
			for (final Position neighbour : neighbours.keySet()) {
				if (!parents.containsKey(neighbour)) {
					parents.put(neighbour, current);
					depths.put(neighbour, depth + 1);
					queue.add(neighbour);
				}
			}
		}

		if (!found) {
			return null;
		}

		final LongArrayList path = new LongArrayList();
		Position current = end;
		while (true) {
			path.add(Init.positionToBlockPos(current).asLong());
			final Position parent = parents.get(current);
			if (parent.equals(current)) {
				break;
			}
			current = parent;
		}
		// Built end to start; the server wants the first click first
		final LongArrayList reversed = new LongArrayList(path.size());
		for (int i = path.size() - 1; i >= 0; i--) {
			reversed.add(path.getLong(i));
		}
		return reversed;
	}
}
