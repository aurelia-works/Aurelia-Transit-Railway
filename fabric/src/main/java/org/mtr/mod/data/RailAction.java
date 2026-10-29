package org.mtr.mod.data;

import org.mtr.core.data.Rail;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.SlabBlockExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Represent an action that runs along a rail. (e.g. One made by Tunnel Creator/Bridge Creator)
 */
public class RailAction {

	private double distance;

	public final long id;
	private final ServerWorld serverWorld;
	private final UUID uuid;
	private final String playerName;
	private final RailActionType railActionType;
	private final Rail rail;
	private final int radius;
	private final int height;
	private final double length;
	private final BlockState state;
	private final boolean isSlab;
	private final ObjectOpenHashSet<BlockPos> blacklistedPositions = new ObjectOpenHashSet<>();
	private int placed;
	@Nullable
	private final RailActionPlan plan;
	@Nullable
	private final RailBuilder builder;

	private static final double INCREMENT = 0.1;
	/**
	 * Server time an action may use in a tick: a fifth of the 50 ms tick, as ATA's station builds use. It was 2 ms, which
	 * spread a long bridge's walk and placement over many seconds on an idle server.
	 */
	private static final long NANOS_PER_TICK = 10_000_000L;

	public RailAction(ServerWorld serverWorld, ServerPlayerEntity serverPlayerEntity, RailActionType railActionType, Rail rail, int radius, int height, @Nullable BlockState state) {
		id = new Random().nextLong();
		this.serverWorld = serverWorld;
		uuid = serverPlayerEntity.getUuid();
		playerName = serverPlayerEntity.getName().getString();
		this.railActionType = railActionType;
		this.rail = rail;
		this.radius = radius;
		this.height = height;
		this.state = state;
		isSlab = state != null && SlabBlock.isInstance(state.getBlock());
		length = rail.railMath.getLength();
		distance = 0;
		plan = railActionType == RailActionType.BRIDGE ? RailActionPlan.bridge(rail, radius, isSlab) : RailActionPlan.tunnel(rail, radius, height, railActionType == RailActionType.TUNNEL);
		builder = null;
	}

	/**
	 * A Rail Builder action: {@code deck}, {@code clear} and {@code walls} are the parts it builds.
	 */
	public RailAction(ServerWorld serverWorld, ServerPlayerEntity serverPlayerEntity, Rail rail, int radius, int height, BlockState state, boolean deck, boolean clear, boolean walls) {
		id = new Random().nextLong();
		this.serverWorld = serverWorld;
		uuid = serverPlayerEntity.getUuid();
		playerName = serverPlayerEntity.getName().getString();
		railActionType = RailActionType.RAIL_BUILDER;
		this.rail = rail;
		this.radius = radius;
		this.height = height;
		this.state = state;
		isSlab = SlabBlock.isInstance(state.getBlock());
		length = rail.railMath.getLength();
		distance = 0;
		plan = null;
		builder = new RailBuilder(rail, radius, height, isSlab, deck, clear, walls);
	}

	public String getDescription() {
		return railActionType.nameTranslation.getString(playerName, Utilities.round(length, 1), state == null ? "" : TextHelper.translatable(state.getBlock().getTranslationKey()).getString());
	}

	public int getColor() {
		return railActionType.color;
	}

	/**
	 * Perform build action, should be called continuously/every tick.
	 * @return Whether the rail action is completed
	 */
	public boolean build() {
		switch (railActionType) {
			case BRIDGE:
			case TUNNEL:
			case TUNNEL_WALL:
				return buildPlanned();
			case RAIL_BUILDER:
				return buildWithBuilder();
			default:
				return true;
		}
	}

	private boolean createTunnel() {
		return create(true, vector -> {
			final BlockPos blockPos = fromVector(vector);
			if (!blacklistedPositions.contains(blockPos) && canPlace(serverWorld, blockPos)) {
				serverWorld.setBlockState(blockPos, Blocks.getAirMapped().getDefaultState());
				blacklistedPositions.add(blockPos);
			}
		});
	}

	private boolean createTunnelWall() {
		return create(false, vector -> {
			final BlockPos blockPos = fromVector(vector);
			if (!blacklistedPositions.contains(blockPos) && canPlace(serverWorld, blockPos)) {
				serverWorld.setBlockState(blockPos, state);
				blacklistedPositions.add(blockPos);
			}
		});
	}

	private boolean createBridge() {
		return create(false, vector -> {
			final boolean isTopHalf = vector.y - Math.floor(vector.y) >= 0.5;
			final BlockPos blockPos = fromVector(vector);

			final BlockPos placePos;
			final BlockState placeState;
			final boolean placeHalf;

			if (isSlab && isTopHalf) {
				placePos = blockPos;
				placeState = state.with(new Property<>(SlabBlockExtension.TYPE), SlabType.BOTTOM.data);
				placeHalf = false;
			} else {
				placePos = blockPos.down();
				placeState = isSlab ? state.with(new Property<>(SlabBlockExtension.TYPE), SlabType.TOP.data) : state;
				placeHalf = true;
			}

			final BlockPos halfPos = getHalfPos(placePos, placeHalf);
			if (blacklistedPositions.contains(halfPos)) {
				return;
			}

			if (placePos != blockPos && canPlace(serverWorld, blockPos)) {
				serverWorld.setBlockState(blockPos, Blocks.getAirMapped().getDefaultState());
			}
			if (canPlace(serverWorld, placePos)) {
				serverWorld.setBlockState(placePos, placeState);
				blacklistedPositions.add(halfPos);
			}
		});
	}

	private boolean create(boolean includeMiddle, Consumer<Vector> consumer) {
		final long startTime = System.currentTimeMillis();
		while (System.currentTimeMillis() - startTime < 2) {
			final Vector pos1 = rail.railMath.getPosition(distance, false);
			distance += INCREMENT;
			final Vector pos2 = rail.railMath.getPosition(distance, false);
			final Vector vec3 = new Vector(pos2.x - pos1.x, 0, pos2.z - pos1.z).normalize().rotateY((float) Math.PI / 2);

			for (double x = -radius; x <= radius; x += INCREMENT) {
				final Vector editPos = pos1.add(vec3.multiply(x, 0, x));
				final boolean wholeNumber = Math.floor(editPos.y) == Math.ceil(editPos.y);
				if (includeMiddle || Math.abs(x) > radius - INCREMENT || radius == 0) {
					for (int y = 0; y <= height; y++) {
						if (y < height || !wholeNumber || (height == 0 && radius == 0)) {
							consumer.accept(editPos.add(0, y, 0));
						}
					}
				} else {
					consumer.accept(editPos.add(0, Math.max(0, wholeNumber ? height - 1 : height), 0));
				}
			}

			if (length - distance < INCREMENT) {
				sendProgressMessage(100);
				return true;
			}
		}

		sendProgressMessage((float) Utilities.round(100 * distance / length, 1));
		return false;
	}

	/**
	 * Walks the rail into a {@link RailActionPlan}, then changes the planned blocks in walk order, within
	 * {@link #NANOS_PER_TICK} each tick. Replaces {@link #create(boolean, Consumer)}. The old private create* methods and
	 * their lambdas stay unused: add-on mixins target methods by name, private ones included, and DropInSignaturesTest keeps
	 * every MTR 4.0.5 method.
	 */
	private boolean buildPlanned() {
		final long deadline = System.nanoTime() + NANOS_PER_TICK;
		if (plan.walk(deadline)) {
			while (placed < plan.size() && System.nanoTime() < deadline) {
				applyPlanned(plan.get(placed++));
			}
			if (placed == plan.size()) {
				sendProgressMessage(100);
				return true;
			}
		}

		sendProgressMessage((float) Utilities.round(50 * plan.walked() + (plan.size() == 0 ? 0 : 50.0 * placed / plan.size()), 1));
		return false;
	}

	private void applyPlanned(RailActionPlan.Op op) {
		final BlockPos blockPos = new BlockPos(op.x(), op.y(), op.z());
		switch (railActionType) {
			case TUNNEL:
				if (canPlace(serverWorld, blockPos)) {
					serverWorld.setBlockState(blockPos, Blocks.getAirMapped().getDefaultState());
				}
				break;
			case TUNNEL_WALL:
				if (canPlace(serverWorld, blockPos)) {
					serverWorld.setBlockState(blockPos, state);
				}
				break;
			default:
				if (op.clearAbove() && canPlace(serverWorld, blockPos.up())) {
					serverWorld.setBlockState(blockPos.up(), Blocks.getAirMapped().getDefaultState());
				}
				if (canPlace(serverWorld, blockPos)) {
					serverWorld.setBlockState(blockPos, isSlab ? state.with(new Property<>(SlabBlockExtension.TYPE), (op.topHalf() ? SlabType.TOP : SlabType.BOTTOM).data) : state);
				}
				break;
		}
	}

	private boolean buildWithBuilder() {
		final boolean done = builder.step(System.nanoTime() + NANOS_PER_TICK, new WorldTerrain());
		sendProgressMessage(done ? 100 : (float) Utilities.round(100 * builder.progress(), 1));
		return done;
	}

	private void sendProgressMessage(float percentage) {
		final PlayerEntity playerEntity = serverWorld.getPlayerByUuid(uuid);
		if (playerEntity != null) {
			playerEntity.sendMessage(railActionType.progressTranslation.getText(percentage), true);
		}
	}

	private static boolean canPlace(ServerWorld serverWorld, BlockPos pos) {
		return serverWorld.getBlockEntity(pos) == null && !(serverWorld.getBlockState(pos).getBlock().data instanceof BlockNode);
	}

	/**
	 * The world as the {@link RailBuilder} sees it.
	 */
	private final class WorldTerrain implements RailBuilder.Terrain {

		@Override
		public boolean isGround(int x, int y, int z) {
			final BlockState blockState = serverWorld.getBlockState(new BlockPos(x, y, z));
			return !blockState.isAir() && blockState.data.getFluidState().isEmpty();
		}

		@Override
		public boolean canChange(int x, int y, int z) {
			return canPlace(serverWorld, new BlockPos(x, y, z));
		}

		@Override
		public void set(int x, int y, int z, RailBuilder.Part part, boolean topHalf) {
			final BlockState placeState;
			if (part == RailBuilder.Part.AIR) {
				placeState = Blocks.getAirMapped().getDefaultState();
			} else if (isSlab) {
				placeState = state.with(new Property<>(SlabBlockExtension.TYPE), (part == RailBuilder.Part.WALL ? SlabType.DOUBLE : topHalf ? SlabType.TOP : SlabType.BOTTOM).data);
			} else {
				placeState = state;
			}
			serverWorld.setBlockState(new BlockPos(x, y, z), placeState);
		}
	}

	private static BlockPos getHalfPos(BlockPos pos, boolean isTopHalf) {
		return new BlockPos(pos.getX(), pos.getY() * 2 + (isTopHalf ? 1 : 0), pos.getZ());
	}

	private static BlockPos fromVector(Vector vector) {
		return Init.newBlockPos(vector.x, vector.y, vector.z);
	}
}
