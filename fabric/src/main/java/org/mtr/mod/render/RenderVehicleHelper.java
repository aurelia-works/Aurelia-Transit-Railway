package org.mtr.mod.render;

import org.mtr.core.tool.Utilities;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.GraphicsHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.Init;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockPSDAPGDoorBase;
import org.mtr.mod.block.PlatformHelper;
import org.mtr.mod.config.Config;

public class RenderVehicleHelper {

	public static final float HALF_PLAYER_WIDTH = 0.3F;
	private static final int CHECK_DOOR_RADIUS_XZ = 1;
	private static final int CHECK_DOOR_RADIUS_Y = 2;
	private static final double RIDE_STEP_THRESHOLD = 0.75;
	/** A remembered search box is looked through again after this long, so a platform block placed or a chunk loaded is noticed. */
	private static final long DOOR_SCAN_MAX_AGE_NANOS = 500_000_000L;
	private static final int DOOR_SCAN_MAX_ENTRIES = 4096;
	private static final int DOOR_CHECK_COMPARE_EVERY = 256;
	private static final ExpiringCache<DoorScanKey, DoorScan> DOOR_SCANS = new ExpiringCache<>(DOOR_SCAN_MAX_AGE_NANOS, DOOR_SCAN_MAX_ENTRIES, System::nanoTime);
	private static int doorCheckCalls;
	private static boolean doorChecksFailed;

	/**
	 * @return whether the doorway is close to platform blocks, unlocked platform screen doors, or unlocked automatic platform gates
	 */
	public static boolean canOpenDoors(Box doorway, PositionAndRotation positionAndRotation, double doorValue) {
		final ClientWorld clientWorld = MinecraftClient.getInstance().getWorldMapped();
		if (clientWorld == null) {
			return false;
		}
		if (doorChecksFailed || !Config.getClient().getFasterDoorChecks()) {
			return canOpenDoorsStock(clientWorld, doorway, positionAndRotation, doorValue);
		}

		final DoorScan scan = getDoorScan(clientWorld, doorway, positionAndRotation);
		boolean canOpenDoors = scan.platform;
		// The door blocks are read on every call (unlocked state and block entity), in the stock order, so door values are handed out as before
		for (final BlockPos checkPos : scan.doors) {
			final BlockState blockState = clientWorld.getBlockState(checkPos);
			if (blockState.getBlock().data instanceof BlockPSDAPGDoorBase && blockState.get(new Property<>(BlockPSDAPGDoorBase.UNLOCKED.data))) {
				canOpenDoors = true;
				final BlockEntity blockEntity = clientWorld.getBlockEntity(checkPos);
				if (blockEntity != null && blockEntity.data instanceof BlockPSDAPGDoorBase.BlockEntityBase) {
					((BlockPSDAPGDoorBase.BlockEntityBase) blockEntity.data).setDoorValue(doorValue);
				}
			}
		}

		// Every so often run the stock search too: the answers must agree, otherwise the stock search is used from now on
		if (++doorCheckCalls % DOOR_CHECK_COMPARE_EVERY == 0) {
			final boolean stock = canOpenDoorsStock(clientWorld, doorway, positionAndRotation, doorValue);
			if (stock != canOpenDoors) {
				doorChecksFailed = true;
				Init.LOGGER.warn("A remembered door check differed from the stock one; the stock check is used for the rest of this session");
				return stock;
			}
		}
		return canOpenDoors;
	}

	private static boolean canOpenDoorsStock(ClientWorld clientWorld, Box doorway, PositionAndRotation positionAndRotation, double doorValue) {
		final Vector3d doorwayPosition1 = positionAndRotation.transformForwards(new Vector3d(doorway.getMinXMapped(), doorway.getMaxYMapped(), doorway.getMinZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
		final Vector3d doorwayPosition2 = positionAndRotation.transformForwards(new Vector3d(doorway.getMaxXMapped(), doorway.getMaxYMapped(), doorway.getMinZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
		final Vector3d doorwayPosition3 = positionAndRotation.transformForwards(new Vector3d(doorway.getMaxXMapped(), doorway.getMaxYMapped(), doorway.getMaxZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
		final Vector3d doorwayPosition4 = positionAndRotation.transformForwards(new Vector3d(doorway.getMinXMapped(), doorway.getMaxYMapped(), doorway.getMaxZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
		final double minX = Math.min(Math.min(doorwayPosition1.getXMapped(), doorwayPosition2.getXMapped()), Math.min(doorwayPosition3.getXMapped(), doorwayPosition4.getXMapped()));
		final double maxX = Math.max(Math.max(doorwayPosition1.getXMapped(), doorwayPosition2.getXMapped()), Math.max(doorwayPosition3.getXMapped(), doorwayPosition4.getXMapped()));
		final double minY = Math.min(Math.min(doorwayPosition1.getYMapped(), doorwayPosition2.getYMapped()), Math.min(doorwayPosition3.getYMapped(), doorwayPosition4.getYMapped()));
		final double maxY = Math.max(Math.max(doorwayPosition1.getYMapped(), doorwayPosition2.getYMapped()), Math.max(doorwayPosition3.getYMapped(), doorwayPosition4.getYMapped()));
		final double minZ = Math.min(Math.min(doorwayPosition1.getZMapped(), doorwayPosition2.getZMapped()), Math.min(doorwayPosition3.getZMapped(), doorwayPosition4.getZMapped()));
		final double maxZ = Math.max(Math.max(doorwayPosition1.getZMapped(), doorwayPosition2.getZMapped()), Math.max(doorwayPosition3.getZMapped(), doorwayPosition4.getZMapped()));
		boolean canOpenDoors = false;

		for (double checkX = minX - CHECK_DOOR_RADIUS_XZ; checkX <= maxX + CHECK_DOOR_RADIUS_XZ; checkX++) {
			for (double checkY = minY - CHECK_DOOR_RADIUS_Y; checkY <= maxY + CHECK_DOOR_RADIUS_Y; checkY++) {
				for (double checkZ = minZ - CHECK_DOOR_RADIUS_XZ; checkZ <= maxZ + CHECK_DOOR_RADIUS_XZ; checkZ++) {
					final BlockPos checkPos = Init.newBlockPos(checkX, checkY, checkZ);
					final BlockState blockState = clientWorld.getBlockState(checkPos);
					final Block block = blockState.getBlock();
					if (block.data instanceof PlatformHelper) {
						canOpenDoors = true;
					} else if (block.data instanceof BlockPSDAPGDoorBase && blockState.get(new Property<>(BlockPSDAPGDoorBase.UNLOCKED.data))) {
						canOpenDoors = true;
						final BlockEntity blockEntity = clientWorld.getBlockEntity(checkPos);
						if (blockEntity != null && blockEntity.data instanceof BlockPSDAPGDoorBase.BlockEntityBase) {
							((BlockPSDAPGDoorBase.BlockEntityBase) blockEntity.data).setDoorValue(doorValue);
						}
					}
				}
			}
		}

		return canOpenDoors;
	}

	/** The stock box and search, recording what is in it instead of deciding. */
	private static DoorScan scanDoorway(ClientWorld clientWorld, Box doorway, PositionAndRotation positionAndRotation) {
		final Vector3d doorwayPosition1 = positionAndRotation.transformForwards(new Vector3d(doorway.getMinXMapped(), doorway.getMaxYMapped(), doorway.getMinZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
		final Vector3d doorwayPosition2 = positionAndRotation.transformForwards(new Vector3d(doorway.getMaxXMapped(), doorway.getMaxYMapped(), doorway.getMinZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
		final Vector3d doorwayPosition3 = positionAndRotation.transformForwards(new Vector3d(doorway.getMaxXMapped(), doorway.getMaxYMapped(), doorway.getMaxZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
		final Vector3d doorwayPosition4 = positionAndRotation.transformForwards(new Vector3d(doorway.getMinXMapped(), doorway.getMaxYMapped(), doorway.getMaxZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
		final double minX = Math.min(Math.min(doorwayPosition1.getXMapped(), doorwayPosition2.getXMapped()), Math.min(doorwayPosition3.getXMapped(), doorwayPosition4.getXMapped()));
		final double maxX = Math.max(Math.max(doorwayPosition1.getXMapped(), doorwayPosition2.getXMapped()), Math.max(doorwayPosition3.getXMapped(), doorwayPosition4.getXMapped()));
		final double minY = Math.min(Math.min(doorwayPosition1.getYMapped(), doorwayPosition2.getYMapped()), Math.min(doorwayPosition3.getYMapped(), doorwayPosition4.getYMapped()));
		final double maxY = Math.max(Math.max(doorwayPosition1.getYMapped(), doorwayPosition2.getYMapped()), Math.max(doorwayPosition3.getYMapped(), doorwayPosition4.getYMapped()));
		final double minZ = Math.min(Math.min(doorwayPosition1.getZMapped(), doorwayPosition2.getZMapped()), Math.min(doorwayPosition3.getZMapped(), doorwayPosition4.getZMapped()));
		final double maxZ = Math.max(Math.max(doorwayPosition1.getZMapped(), doorwayPosition2.getZMapped()), Math.max(doorwayPosition3.getZMapped(), doorwayPosition4.getZMapped()));
		boolean platform = false;
		final ObjectArrayList<BlockPos> doors = new ObjectArrayList<>();

		for (double checkX = minX - CHECK_DOOR_RADIUS_XZ; checkX <= maxX + CHECK_DOOR_RADIUS_XZ; checkX++) {
			for (double checkY = minY - CHECK_DOOR_RADIUS_Y; checkY <= maxY + CHECK_DOOR_RADIUS_Y; checkY++) {
				for (double checkZ = minZ - CHECK_DOOR_RADIUS_XZ; checkZ <= maxZ + CHECK_DOOR_RADIUS_XZ; checkZ++) {
					final BlockPos checkPos = Init.newBlockPos(checkX, checkY, checkZ);
					final Object block = clientWorld.getBlockState(checkPos).getBlock().data;
					if (block instanceof PlatformHelper) {
						platform = true;
					} else if (block instanceof BlockPSDAPGDoorBase) {
						doors.add(checkPos);
					}
				}
			}
		}

		return new DoorScan(platform, doors);
	}

	/** What a doorway's search box holds: a platform block anywhere, and the door blocks in the stock search order. */
	private static final class DoorScan {

		private final boolean platform;
		private final ObjectArrayList<BlockPos> doors;

		private DoorScan(boolean platform, ObjectArrayList<BlockPos> doors) {
			this.platform = platform;
			this.doors = doors;
		}
	}

	/** One doorway at one position: the search box follows from these. */
	private static final class DoorScanKey {

		private final double[] values;
		private final int hash;

		private DoorScanKey(Box doorway, PositionAndRotation positionAndRotation) {
			values = new double[]{
					doorway.getMinXMapped(), doorway.getMaxYMapped(), doorway.getMinZMapped(), doorway.getMaxXMapped(), doorway.getMaxZMapped(),
					positionAndRotation.position.x, positionAndRotation.position.y, positionAndRotation.position.z, positionAndRotation.yaw, positionAndRotation.pitch
			};
			hash = java.util.Arrays.hashCode(values);
		}

		@Override
		public boolean equals(Object object) {
			return object instanceof DoorScanKey && java.util.Arrays.equals(values, ((DoorScanKey) object).values);
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}

	public static double getDoorBlockedAmount(Box doorway, double playerX, double playerY, double playerZ) {
		if (playerX > doorway.getMinXMapped() - HALF_PLAYER_WIDTH && playerX < doorway.getMaxXMapped() + HALF_PLAYER_WIDTH && Utilities.isBetween(playerY, doorway.getMinYMapped(), doorway.getMaxYMapped()) && playerZ > doorway.getMinZMapped() - HALF_PLAYER_WIDTH && playerZ < doorway.getMaxZMapped() + HALF_PLAYER_WIDTH) {
			final double halfWidth = (doorway.getMaxZMapped() - doorway.getMinZMapped()) / 2;
			final double distance = Math.min(playerZ - HALF_PLAYER_WIDTH - doorway.getMinZMapped(), doorway.getMaxZMapped() - HALF_PLAYER_WIDTH - playerZ);
			return Utilities.clamp((halfWidth - distance) / halfWidth, 0, 1);
		} else {
			return 0;
		}
	}

	public static void renderFloorOrDoorway(Box floorOrDoorway, int color, Vector3d playerPosition, PositionAndRotation positionAndRotation, boolean useOffset) {
		final ClientPlayerEntity clientPlayerEntity = MinecraftClient.getInstance().getPlayerMapped();
		if (clientPlayerEntity != null && clientPlayerEntity.isHolding(Items.BRUSH.get())) {
			final Vector3d corner1 = positionAndRotation.transformForwards(new Vector3d(floorOrDoorway.getMinXMapped(), floorOrDoorway.getMaxYMapped(), floorOrDoorway.getMinZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
			final Vector3d corner2 = positionAndRotation.transformForwards(new Vector3d(floorOrDoorway.getMaxXMapped(), floorOrDoorway.getMaxYMapped(), floorOrDoorway.getMinZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
			final Vector3d corner3 = positionAndRotation.transformForwards(new Vector3d(floorOrDoorway.getMaxXMapped(), floorOrDoorway.getMaxYMapped(), floorOrDoorway.getMaxZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
			final Vector3d corner4 = positionAndRotation.transformForwards(new Vector3d(floorOrDoorway.getMinXMapped(), floorOrDoorway.getMaxYMapped(), floorOrDoorway.getMaxZMapped()), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
			final int newColor = boxContains(floorOrDoorway,
					playerPosition.getXMapped() - HALF_PLAYER_WIDTH,
					playerPosition.getYMapped(),
					playerPosition.getZMapped() - HALF_PLAYER_WIDTH
			) || boxContains(floorOrDoorway,
					playerPosition.getXMapped() + HALF_PLAYER_WIDTH,
					playerPosition.getYMapped(),
					playerPosition.getZMapped() - HALF_PLAYER_WIDTH
			) || boxContains(floorOrDoorway,
					playerPosition.getXMapped() + HALF_PLAYER_WIDTH,
					playerPosition.getYMapped(),
					playerPosition.getZMapped() + HALF_PLAYER_WIDTH
			) || boxContains(floorOrDoorway,
					playerPosition.getXMapped() - HALF_PLAYER_WIDTH,
					playerPosition.getYMapped(),
					playerPosition.getZMapped() + HALF_PLAYER_WIDTH
			) ? 0xFF00FF00 : color;
			final Vector3d zeroVector = Vector3d.getZeroMapped();
			MainRenderer.scheduleRender(QueuedRenderLayer.LINES, (graphicsHolder, offset) -> {
				drawLine(graphicsHolder, corner1, corner2, useOffset ? offset : zeroVector, newColor);
				drawLine(graphicsHolder, corner2, corner3, useOffset ? offset : zeroVector, newColor);
				drawLine(graphicsHolder, corner3, corner4, useOffset ? offset : zeroVector, newColor);
				drawLine(graphicsHolder, corner4, corner1, useOffset ? offset : zeroVector, newColor);
			});
		}
	}

	public static boolean boxContains(Box box, double x, double y, double z) {
		return Utilities.isBetween(
				x,
				box.getMinXMapped(),
				box.getMaxXMapped()
		) && Utilities.isBetween(
				y,
				box.getMinYMapped(),
				box.getMaxYMapped(),
				RIDE_STEP_THRESHOLD
		) && Utilities.isBetween(
				z,
				box.getMinZMapped(),
				box.getMaxZMapped()
		);
	}

	// Last in the class: add-ons hook MTR 4.0.5's numbered lambdas, so new lambdas must come after them
	private static DoorScan getDoorScan(ClientWorld clientWorld, Box doorway, PositionAndRotation positionAndRotation) {
		return DOOR_SCANS.get(new DoorScanKey(doorway, positionAndRotation), clientWorld.data, () -> scanDoorway(clientWorld, doorway, positionAndRotation));
	}

	private static void drawLine(GraphicsHolder graphicsHolder, Vector3d corner1, Vector3d corner2, Vector3d offset, int color) {
		graphicsHolder.drawLineInWorld(
				(float) (corner1.getXMapped() - offset.getXMapped()), (float) (corner1.getYMapped() - offset.getYMapped()), (float) (corner1.getZMapped() - offset.getZMapped()),
				(float) (corner2.getXMapped() - offset.getXMapped()), (float) (corner2.getYMapped() - offset.getYMapped()), (float) (corner2.getZMapped() - offset.getZMapped()),
				color
		);
	}
}
