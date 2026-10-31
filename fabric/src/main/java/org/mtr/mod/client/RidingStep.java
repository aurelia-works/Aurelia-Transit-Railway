package org.mtr.mod.client;

import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectBooleanImmutablePair;
import org.mtr.mapping.holder.Box;
import org.mtr.mod.render.RenderVehicleHelper;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * One walking step of a player riding a vehicle or lift, in car coordinates: clamped to the floors, or out through a
 * doorway. Moved here from {@link VehicleRidingMovement#movePlayer} so it can be tested.
 */
public final class RidingStep {

	private RidingStep() {
	}

	/**
	 * @param floorsAndDoorways the car's floors ({@code true}) and open doorways ({@code false})
	 * @return where the player ends up and whether they left the vehicle
	 */
	public static Result step(ObjectArrayList<ObjectBooleanImmutablePair<Box>> floorsAndDoorways, double x, double y, double z, double movementX, double movementZ) {
		final ObjectArrayList<double[]> offsets = new ObjectArrayList<>();
		final double half = RenderVehicleHelper.HALF_PLAYER_WIDTH;
		clamp(floorsAndDoorways, x + movementX - half, y, z + movementZ - half, offsets);
		clamp(floorsAndDoorways, x + movementX + half, y, z + movementZ - half, offsets);
		clamp(floorsAndDoorways, x + movementX + half, y, z + movementZ + half, offsets);
		clamp(floorsAndDoorways, x + movementX - half, y, z + movementZ + half, offsets);

		if (offsets.isEmpty()) {
			// Not standing on any floor or doorway: the player leaves the vehicle where the step took them. Letting go where
			// they stood put them inside the car after a long low-FPS step, with nothing under them (MTR #1069, #943).
			return new Result(x + movementX, y, z + movementZ, true);
		}

		// The highest amounts to clamp the movement by in X and Z
		double clampX = 0;
		double maxY = -Double.MAX_VALUE;
		double clampZ = 0;
		for (final double[] offset : offsets) {
			if (Math.abs(offset[0]) > Math.abs(clampX)) {
				clampX = offset[0];
			}
			maxY = Math.max(maxY, offset[1]);
			if (Math.abs(offset[2]) > Math.abs(clampZ)) {
				clampZ = offset[2];
			}
		}
		return new Result(x + movementX + clampX, maxY, z + movementZ + clampZ, false);
	}

	private static void clamp(ObjectArrayList<ObjectBooleanImmutablePair<Box>> floorsAndDoorways, double x, double y, double z, ObjectArrayList<double[]> offsets) {
		final ObjectBooleanImmutablePair<Box> floorOrDoorway = best(floorsAndDoorways, x, y, z);
		if (floorOrDoorway != null) {
			final Box box = floorOrDoorway.left();
			if (floorOrDoorway.rightBoolean()) {
				// The intersecting or closest box is a floor: keep the player in it
				offsets.add(new double[]{Utilities.clamp(x, box.getMinXMapped(), box.getMaxXMapped()) - x, box.getMaxYMapped(), Utilities.clamp(z, box.getMinZMapped(), box.getMaxZMapped()) - z});
			} else if (RenderVehicleHelper.boxContains(box, x, y, z)) {
				// A doorway does not hold the player in; leaving it means leaving the vehicle
				offsets.add(new double[]{0, box.getMaxYMapped(), 0});
			}
		}
	}

	@Nullable
	private static ObjectBooleanImmutablePair<Box> best(ObjectArrayList<ObjectBooleanImmutablePair<Box>> floorsOrDoorways, double x, double y, double z) {
		return floorsOrDoorways.stream()
				.filter(floorOrDoorway -> RenderVehicleHelper.boxContains(floorOrDoorway.left(), x, y, z))
				.max(Comparator.comparingDouble(floorOrDoorway -> floorOrDoorway.left().getMaxYMapped()))
				.orElse(floorsOrDoorways.stream().filter(floorOrDoorway -> Math.abs(floorOrDoorway.left().getMaxYMapped() - y) <= 1).min(Comparator.comparingDouble(floorOrDoorway -> distance(floorOrDoorway.left(), x, z))).orElse(null));
	}

	private static double distance(Box box, double x, double z) {
		final double minX = box.getMinXMapped();
		final double maxX = box.getMaxXMapped();
		final double minZ = box.getMinZMapped();
		final double maxZ = box.getMaxZMapped();
		return (Utilities.isBetween(x, minX, maxX) ? 0 : Math.min(Math.abs(minX - x), Math.abs(maxX - x))) + (Utilities.isBetween(z, minZ, maxZ) ? 0 : Math.min(Math.abs(minZ - z), Math.abs(maxZ - z)));
	}

	public record Result(double x, double y, double z, boolean dismounted) {
	}
}
