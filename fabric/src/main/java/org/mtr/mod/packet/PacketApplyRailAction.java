package org.mtr.mod.packet;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TwoPositionsBase;
import org.mtr.core.operation.RailsRequest;
import org.mtr.core.operation.RailsResponse;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Init;
import org.mtr.mod.generated.lang.TranslationProvider;
import org.mtr.mod.item.ItemBlockClickingBase;
import org.mtr.mod.item.ItemNodeModifierSelectableBlockBase;

import java.util.UUID;

/**
 * Multi-node bridge, tunnel and tunnel wall creators (MTR 4.1, rebuilt for 1.20.1). When the second click lands on a node
 * that is not directly connected to the first, the client finds the track between them and sends the nodes along it; the
 * server applies the tool to every rail on the way, as if each had been clicked on its own.
 * <p>
 * The client's own use packet follows this one and runs the usual end click on the two clicked nodes. That rail does not
 * exist, so the end click would say so; {@link #consumePending} lets it stay quiet for the path just applied.
 */
public final class PacketApplyRailAction extends PacketHandler {

	/** Longest path one click may cover; a longer track needs more clicks. */
	public static final int MAX_SEGMENTS = 256;
	/** Generous reach: the player stands near the node they just clicked. */
	private static final double MAX_REACH_SQUARED = 12 * 12;
	/** The use packet follows at once; anything older is from a click that did not reach the item. */
	private static final long PENDING_MILLIS = 2000;
	private static final Object2ObjectOpenHashMap<UUID, long[]> PENDING = new Object2ObjectOpenHashMap<>();

	private final boolean offHand;
	private final LongArrayList positions;

	public PacketApplyRailAction(PacketBufferReceiver packetBufferReceiver) {
		offHand = packetBufferReceiver.readBoolean();
		final int count = packetBufferReceiver.readInt();
		positions = new LongArrayList();
		// A bad count reads as nothing, and runServer ignores it
		if (count > 0 && count <= MAX_SEGMENTS + 1) {
			for (int i = 0; i < count; i++) {
				positions.add(packetBufferReceiver.readLong());
			}
		}
	}

	/**
	 * @param positions the nodes along the track, first click first; at least three (two rails)
	 */
	public PacketApplyRailAction(boolean offHand, LongArrayList positions) {
		this.offHand = offHand;
		this.positions = positions;
	}

	@Override
	public void write(PacketBufferSender packetBufferSender) {
		packetBufferSender.writeBoolean(offHand);
		packetBufferSender.writeInt(positions.size());
		for (int i = 0; i < positions.size(); i++) {
			packetBufferSender.writeLong(positions.getLong(i));
		}
	}

	@Override
	public void runServer(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity) {
		if (positions.size() < 3 || positions.size() > MAX_SEGMENTS + 1) {
			return;
		}

		final ItemStack itemStack = new ItemStack(serverPlayerEntity.data.getStackInHand(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND));
		if (!(itemStack.getItem().data instanceof ItemNodeModifierSelectableBlockBase)) {
			return;
		}
		final ItemNodeModifierSelectableBlockBase item = (ItemNodeModifierSelectableBlockBase) itemStack.getItem().data;

		// The path must start at the node this tool selected and end next to the player
		final NbtCompound nbt = itemStack.data.getNbt();
		final long first = positions.getLong(0);
		final long last = positions.getLong(positions.size() - 1);
		if (nbt == null || !nbt.contains(ItemBlockClickingBase.TAG_POS) || nbt.getLong(ItemBlockClickingBase.TAG_POS) != first) {
			return;
		}
		if (serverPlayerEntity.data.squaredDistanceTo(Vec3d.ofCenter(net.minecraft.util.math.BlockPos.fromLong(last))) > MAX_REACH_SQUARED) {
			return;
		}

		// No node repeats, so the path cannot loop back on itself. The simulator checks that each rail exists; the nodes are
		// not looked up here, since far ones sit in unloaded chunks.
		final World world = new World(serverPlayerEntity.getServerWorld().data);
		final LongOpenHashSet seen = new LongOpenHashSet();
		final RailsRequest railsRequest = new RailsRequest();
		for (int i = 0; i < positions.size(); i++) {
			final long position = positions.getLong(i);
			if (!seen.add(position)) {
				return;
			}
			if (i > 0) {
				railsRequest.addRailId(TwoPositionsBase.getHexId(Init.blockPosToPosition(BlockPos.fromLong(positions.getLong(i - 1))), Init.blockPosToPosition(BlockPos.fromLong(position))));
			}
		}

		// Drop what has expired, so players who disconnect or never finish a click leave nothing behind
		final long now = System.currentTimeMillis();
		PENDING.values().removeIf(pending -> now - pending[2] > PENDING_MILLIS);
		PENDING.put(serverPlayerEntity.getUuid(), new long[]{first, last, now});
		final int expected = positions.size() - 1;
		Init.sendMessageC2S(OperationProcessor.RAILS, minecraftServer, world, railsRequest, railsResponse -> {
			int applied = 0;
			for (final Rail rail : railsResponse.getRails()) {
				item.applyToRail(rail, serverPlayerEntity, itemStack);
				applied++;
			}
			if (applied < expected) {
				// Part of the track changed since the client saw it
				serverPlayerEntity.sendMessage(TranslationProvider.GUI_MTR_RAIL_NOT_FOUND_ACTION.getText(), true);
			}
		}, RailsResponse.class);
	}

	/**
	 * Called by the end click on the two clicked nodes. True when a path between exactly those two nodes was just applied
	 * for this player, so the end click must not look up (and report missing) the direct rail between them.
	 */
	public static boolean consumePending(ServerPlayerEntity serverPlayerEntity, BlockPos posStart, BlockPos posEnd) {
		final long[] pending = PENDING.remove(serverPlayerEntity.getUuid());
		if (pending == null || System.currentTimeMillis() - pending[2] > PENDING_MILLIS) {
			return false;
		}
		final long start = posStart.asLong();
		final long end = posEnd.asLong();
		return pending[0] == start && pending[1] == end || pending[0] == end && pending[1] == start;
	}
}
