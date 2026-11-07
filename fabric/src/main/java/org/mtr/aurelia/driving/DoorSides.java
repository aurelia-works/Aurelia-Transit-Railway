package org.mtr.aurelia.driving;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mod.block.PlatformHelper;
import org.mtr.mod.data.VehicleExtension;

/**
 * Which side of a stopped train has a platform. MTR opens a doorway when its own box scan finds platform blocks, so
 * this reuses the platform blocks themselves rather than assuming a side from the model. A train can be platformed on
 * both sides at a bay platform; MTR's door system cannot open one side only, so both are reported and both open.
 */
public final class DoorSides {

	public enum Side {
		NONE, LEFT, RIGHT, BOTH
	}

	/** How far past the car body to look, in blocks. A platform edge is normally one block from the door. */
	private static final double EDGE_MARGIN = 1.4;
	private static final double ALONG_CAR = 1.5;
	private static final int VERTICAL_RANGE = 2;

	private DoorSides() {
	}

	public static Side of(VehicleExtension vehicle) {
		final ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) {
			return Side.NONE;
		}

		final ObjectArrayList<ObjectObjectImmutablePair<VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars = vehicle.getVehicleCarsAndPositions();
		if (cars.isEmpty()) {
			return Side.NONE;
		}

		boolean left = false;
		boolean right = false;
		for (final ObjectObjectImmutablePair<VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>> car : cars) {
			final ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>> bogies = car.right();
			if (bogies.isEmpty()) {
				continue;
			}
			final Vector first = bogies.get(0).left();
			final Vector second = bogies.get(bogies.size() - 1).right();
			final double dx = second.x - first.x;
			final double dz = second.z - first.z;
			final double length = Math.sqrt(dx * dx + dz * dz);
			if (length < 0.001) {
				continue;
			}

			// The model's +Z points to the back of the car, so the driver's left is (-dz, dx) for a forward train.
			final double leftX = -dz / length;
			final double leftZ = dx / length;
			final double forwardX = -dx / length;
			final double forwardZ = -dz / length;
			final double sideDistance = car.left().getWidth() / 2 + EDGE_MARGIN;
			final double centreX = (first.x + second.x) / 2;
			final double centreY = (first.y + second.y) / 2;
			final double centreZ = (first.z + second.z) / 2;

			for (int along = -1; along <= 1; along++) {
				final double x = centreX + forwardX * ALONG_CAR * along;
				final double z = centreZ + forwardZ * ALONG_CAR * along;
				left |= platformAt(world, x + leftX * sideDistance, centreY, z + leftZ * sideDistance);
				right |= platformAt(world, x - leftX * sideDistance, centreY, z - leftZ * sideDistance);
			}
		}

		if (vehicle.getReversed()) {
			final boolean swap = left;
			left = right;
			right = swap;
		}
		return left && right ? Side.BOTH : left ? Side.LEFT : right ? Side.RIGHT : Side.NONE;
	}

	private static boolean platformAt(ClientWorld world, double x, double y, double z) {
		final BlockPos centre = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
		for (int dy = -VERTICAL_RANGE; dy <= VERTICAL_RANGE; dy++) {
			final BlockState state = world.getBlockState(centre.add(0, dy, 0));
			if (state.getBlock() instanceof PlatformHelper) {
				return true;
			}
		}
		return false;
	}
}
