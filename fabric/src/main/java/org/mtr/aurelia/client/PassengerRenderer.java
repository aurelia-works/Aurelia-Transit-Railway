package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Vehicle;
import org.mtr.core.generated.data.SavedRailBaseSchema;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.passenger.PassengerService;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the server's waiting and riding counts as simple people on platforms and inside cars.
 * Platform ends are {@code SavedRailBaseSchema.position1} and {@code position2}. Car positions come from
 * {@link Vehicle#getVehicleCarsAndPositions()}.
 */
public final class PassengerRenderer {

	/** Above the rail, where people stand in a car. */
	private static final double CAR_FLOOR = 1;
	/** A hard stop so a long draw distance cannot fill the frame. */
	private static final int FRAME_CAP = 256;
	/** Floors are read from the world this often, not every frame. */
	private static final long SPOT_REFRESH_MILLIS = 2000;
	private static final int SPOT_CACHE_SIZE = 64;

	@Nullable
	private static final Field POSITION_1 = field("position1");
	@Nullable
	private static final Field POSITION_2 = field("position2");
	private static boolean loggedEnds;

	private record Spots(int count, long millis, double[] at) {
	}

	private final double[] point = new double[3];
	private final BlockPos.Mutable mutable = new BlockPos.Mutable();
	private final Map<Long, Spots> spotCache = new HashMap<>();
	private int drawn;

	private PassengerRenderer() {
	}

	public static void register() {
		final PassengerRenderer renderer = new PassengerRenderer();
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(renderer::render);
	}

	private void render(WorldRenderContext context) {
		final PassengerDrawSettings.Values settings = PassengerDrawSettings.get();
		if (!settings.draw() || context.consumers() == null || context.camera() == null || context.matrixStack() == null || context.world() == null) {
			return;
		}
		final PassengerService.Counts counts = PassengerClient.counts();
		if (counts.waiting().isEmpty() && counts.riding().isEmpty()) {
			return;
		}
		final Vec3d camera = context.camera().getPos();
		final MatrixStack matrices = context.matrixStack();
		final VertexConsumer consumer = context.consumers().getBuffer(settings.solid() ? RenderLayer.getDebugFilledBox() : RenderLayer.getLines());
		drawn = 0;
		platforms(counts.waiting(), settings, camera, matrices, consumer, context.world());
		trains(counts.riding(), settings, camera, matrices, consumer);
	}

	private void platforms(Map<Long, Integer> waiting, PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices, VertexConsumer consumer, World world) {
		final MinecraftClientData data = MinecraftClientData.getInstance();
		final long now = System.currentTimeMillis();
		for (final Map.Entry<Long, Integer> entry : waiting.entrySet()) {
			if (drawn >= FRAME_CAP) {
				return;
			}
			final Platform platform = data.platformIdMap.get(entry.getKey().longValue());
			if (platform == null) {
				continue;
			}
			final Position mid = platform.getMidPosition();
			final Position[] ends = ends(platform);
			final boolean haveEnds = ends != null && ends[0] != null && ends[1] != null;
			// Rails run through block centres
			final double x1 = (haveEnds ? ends[0].getX() : mid.getX()) + 0.5;
			final double y1 = haveEnds ? ends[0].getY() : mid.getY();
			final double z1 = (haveEnds ? ends[0].getZ() : mid.getZ()) + 0.5;
			final double x2 = (haveEnds ? ends[1].getX() : mid.getX()) + 0.5;
			final double y2 = haveEnds ? ends[1].getY() : mid.getY();
			final double z2 = (haveEnds ? ends[1].getZ() : mid.getZ()) + 0.5;
			final double distance = PassengerFigures.distanceToPlatform(camera.x, camera.y, camera.z, x1, y1, z1, x2, y2, z2);
			final int figures = PassengerFigures.toDraw(entry.getValue(), settings.maxPerPlace(), distance, settings.drawDistance(), settings.detailDistance());
			if (figures <= 0) {
				continue;
			}
			Spots spots = spotCache.get(entry.getKey());
			if (spots == null || spots.count != figures || now - spots.millis > SPOT_REFRESH_MILLIS) {
				if (spotCache.size() > SPOT_CACHE_SIZE) {
					spotCache.clear();
				}
				final double[] found = new double[figures * 3];
				PassengerFigures.spots(x1, y1, z1, x2, y2, z2, figures, (x, z, railY) -> floor(world, x, z, railY), found);
				spots = new Spots(figures, now, found);
				spotCache.put(entry.getKey(), spots);
			}
			for (int i = 0; i < figures && drawn < FRAME_CAP; i++) {
				figure(matrices, consumer, camera, settings, spots.at[i * 3], spots.at[i * 3 + 1], spots.at[i * 3 + 2], i, settings.waitingHue());
			}
		}
	}

	/** The lowest place near the rail with something to stand on and room for a person above it. */
	private double floor(World world, double x, double z, int railY) {
		final int blockX = MathHelper.floor(x);
		final int blockZ = MathHelper.floor(z);
		for (int y = railY - 2; y <= railY + 2; y++) {
			final VoxelShape shape = world.getBlockState(mutable.set(blockX, y, blockZ)).getCollisionShape(world, mutable);
			if (shape.isEmpty()) {
				continue;
			}
			final boolean room = world.getBlockState(mutable.set(blockX, y + 1, blockZ)).getCollisionShape(world, mutable).isEmpty()
					&& world.getBlockState(mutable.set(blockX, y + 2, blockZ)).getCollisionShape(world, mutable).isEmpty();
			if (room) {
				return y + shape.getMax(Direction.Axis.Y);
			}
		}
		return Double.NaN;
	}

	private void trains(Map<Long, Integer> riding, PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices, VertexConsumer consumer) {
		for (final VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
			if (drawn >= FRAME_CAP) {
				return;
			}
			final Integer people = riding.get(vehicle.getId());
			if (people == null || people <= 0) {
				continue;
			}
			final ObjectArrayList<ObjectObjectImmutablePair<org.mtr.core.data.VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars = cars(vehicle);
			if (cars == null || cars.isEmpty()) {
				continue;
			}
			double distance = Double.MAX_VALUE;
			for (final ObjectObjectImmutablePair<org.mtr.core.data.VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>> car : cars) {
				for (final ObjectObjectImmutablePair<Vector, Vector> bogie : car.right()) {
					if (bogie.left() != null) {
						distance = Math.min(distance, distance(camera, bogie.left().x, bogie.left().y, bogie.left().z));
					}
					if (bogie.right() != null) {
						distance = Math.min(distance, distance(camera, bogie.right().x, bogie.right().y, bogie.right().z));
					}
				}
			}
			final int figures = PassengerFigures.toDraw(people, settings.maxPerPlace(), distance, settings.drawDistance(), settings.detailDistance());
			if (figures > 0) {
				drawInCars(cars, figures, settings, camera, matrices, consumer);
			}
		}
	}

	private void drawInCars(ObjectArrayList<ObjectObjectImmutablePair<org.mtr.core.data.VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars, int figures, PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices, VertexConsumer consumer) {
		int remaining = figures;
		for (int car = 0; car < cars.size() && remaining > 0 && drawn < FRAME_CAP; car++) {
			final int share = (int) Math.ceil(remaining / (double) (cars.size() - car));
			final ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>> bogies = cars.get(car).right();
			remaining -= share;
			if (bogies == null || bogies.isEmpty()) {
				continue;
			}
			final Vector start = bogies.get(0).left() != null ? bogies.get(0).left() : bogies.get(0).right();
			final ObjectObjectImmutablePair<Vector, Vector> last = bogies.get(bogies.size() - 1);
			final Vector end = last.right() != null ? last.right() : last.left();
			if (start == null || end == null) {
				continue;
			}
			for (int i = 0; i < share && drawn < FRAME_CAP; i++) {
				PassengerFigures.place(start.x, start.y, start.z, end.x, end.y, end.z, i, share, 0, point);
				figure(matrices, consumer, camera, settings, point[0], point[1] + CAR_FLOOR, point[2], i, settings.ridingHue());
			}
		}
	}

	/** A simple person: legs, a body in the place's colour and a head. Outlines draw one tall box instead. */
	private void figure(MatrixStack matrices, VertexConsumer consumer, Vec3d camera, PassengerDrawSettings.Values settings, double x, double y, double z, int index, int hue) {
		final double size = settings.figureSize() / 100D;
		final int rgb = MathHelper.hsvToRgb(hue / (float) PassengerDrawSettings.HUE_MAX, 0.65F, 1);
		final float shade = 0.75F + (index % 3) * 0.125F;
		final float r = (rgb >> 16 & 0xFF) / 255F * shade;
		final float g = (rgb >> 8 & 0xFF) / 255F * shade;
		final float b = (rgb & 0xFF) / 255F * shade;
		final double cx = x - camera.x;
		final double cy = y - camera.y;
		final double cz = z - camera.z;
		if (settings.solid()) {
			final double half = 0.2 * size;
			WorldRenderer.renderFilledBox(matrices, consumer, cx - half * 0.8, cy, cz - half * 0.8, cx + half * 0.8, cy + 0.75 * size, cz + half * 0.8, 0.16F, 0.18F, 0.24F, 1);
			WorldRenderer.renderFilledBox(matrices, consumer, cx - half, cy + 0.75 * size, cz - half, cx + half, cy + 1.35 * size, cz + half, r, g, b, 1);
			final double head = half * 0.75;
			WorldRenderer.renderFilledBox(matrices, consumer, cx - head, cy + 1.37 * size, cz - head, cx + head, cy + 1.72 * size, cz + head, 0.86F, 0.68F, 0.55F, 1);
		} else {
			final double half = 0.22 * size;
			WorldRenderer.drawBox(matrices, consumer, cx - half, cy, cz - half, cx + half, cy + 1.7 * size, cz + half, r, g, b, 1);
		}
		drawn++;
	}

	@Nullable
	private static ObjectArrayList<ObjectObjectImmutablePair<org.mtr.core.data.VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars(Vehicle vehicle) {
		try {
			return vehicle.getVehicleCarsAndPositions();
		} catch (Exception e) {
			return null;
		}
	}

	@Nullable
	private static Position[] ends(Platform platform) {
		if (POSITION_1 == null || POSITION_2 == null) {
			return null;
		}
		try {
			return new Position[]{(Position) POSITION_1.get(platform), (Position) POSITION_2.get(platform)};
		} catch (Exception e) {
			if (!loggedEnds) {
				loggedEnds = true;
				AureliaClient.LOGGER.error("[ATR] passenger figures cannot read platform ends", e);
			}
			return null;
		}
	}

	private static double distance(Vec3d camera, double x, double y, double z) {
		final double dx = x - camera.x;
		final double dy = y - camera.y;
		final double dz = z - camera.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	@Nullable
	private static Field field(String name) {
		try {
			final Field found = SavedRailBaseSchema.class.getDeclaredField(name);
			found.setAccessible(true);
			return found;
		} catch (Exception e) {
			AureliaClient.LOGGER.error("[ATR] passenger figures cannot read SavedRailBaseSchema.{}", name, e);
			return null;
		}
	}
}
