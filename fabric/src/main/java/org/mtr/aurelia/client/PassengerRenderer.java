package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
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
import java.util.Map;

/**
 * Draws the server's waiting and riding counts as box figures along platforms and inside cars.
 * Platform ends are {@code SavedRailBaseSchema.position1} and {@code position2}. Car positions come from
 * {@link Vehicle#getVehicleCarsAndPositions()}.
 */
public final class PassengerRenderer {

	private static final double BOX = 0.22;
	private static final double HEIGHT = 1.5;
	/** A hard stop so a long draw distance cannot fill the frame with boxes. */
	private static final int FRAME_CAP = 256;

	@Nullable
	private static final Field POSITION_1 = field("position1");
	@Nullable
	private static final Field POSITION_2 = field("position2");
	private static boolean loggedEnds;

	private final double[] point = new double[3];
	private int drawn;

	private PassengerRenderer() {
	}

	public static void register() {
		final PassengerRenderer renderer = new PassengerRenderer();
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(renderer::render);
	}

	private void render(WorldRenderContext context) {
		final PassengerDrawSettings.Values settings = PassengerDrawSettings.get();
		if (!settings.draw() || context.consumers() == null || context.camera() == null || context.matrixStack() == null) {
			return;
		}
		final PassengerService.Counts counts = PassengerClient.counts();
		if (counts.waiting().isEmpty() && counts.riding().isEmpty()) {
			return;
		}
		final Vec3d camera = context.camera().getPos();
		final MatrixStack matrices = context.matrixStack();
		final VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
		drawn = 0;
		platforms(counts.waiting(), settings, camera, matrices, lines);
		trains(counts.riding(), settings, camera, matrices, lines);
	}

	private void platforms(Map<Long, Integer> waiting, PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices, VertexConsumer lines) {
		final MinecraftClientData data = MinecraftClientData.getInstance();
		for (final Map.Entry<Long, Integer> entry : waiting.entrySet()) {
			if (drawn >= FRAME_CAP) {
				return;
			}
			final Platform platform = data.platformIdMap.get(entry.getKey().longValue());
			if (platform == null) {
				continue;
			}
			final Position mid = platform.getMidPosition();
			final double distance = distance(camera, mid.getX(), mid.getY(), mid.getZ());
			final int figures = PassengerFigures.toDraw(entry.getValue(), settings.maxPerPlace(), distance, settings.drawDistance(), settings.detailDistance());
			if (figures <= 0) {
				continue;
			}
			final Position[] ends = ends(platform);
			final double x1 = ends == null ? mid.getX() : ends[0].getX();
			final double y1 = ends == null ? mid.getY() : ends[0].getY();
			final double z1 = ends == null ? mid.getZ() : ends[0].getZ();
			final double x2 = ends == null ? mid.getX() : ends[1].getX();
			final double y2 = ends == null ? mid.getY() : ends[1].getY();
			final double z2 = ends == null ? mid.getZ() : ends[1].getZ();
			for (int i = 0; i < figures && drawn < FRAME_CAP; i++) {
				PassengerFigures.place(x1, y1, z1, x2, y2, z2, i, figures, 0.9, point);
				box(matrices, lines, camera, point[0], point[1], point[2], 0.35F, 0.65F, 1.0F);
			}
		}
	}

	private void trains(Map<Long, Integer> riding, PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices, VertexConsumer lines) {
		for (final VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
			if (drawn >= FRAME_CAP) {
				return;
			}
			final Integer people = riding.get(vehicle.getId());
			if (people == null || people <= 0) {
				continue;
			}
			final Vector head = head(vehicle);
			if (head == null) {
				continue;
			}
			final double distance = distance(camera, head.x, head.y, head.z);
			final int figures = PassengerFigures.toDraw(people, settings.maxPerPlace(), distance, settings.drawDistance(), settings.detailDistance());
			if (figures <= 0) {
				continue;
			}
			if (distance > settings.detailDistance()) {
				box(matrices, lines, camera, head.x, head.y, head.z, 1.0F, 0.65F, 0.25F);
				continue;
			}
			drawInCars(vehicle, figures, camera, matrices, lines, head);
		}
	}

	private void drawInCars(Vehicle vehicle, int figures, Vec3d camera, MatrixStack matrices, VertexConsumer lines, Vector head) {
		ObjectArrayList<ObjectObjectImmutablePair<org.mtr.core.data.VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars = null;
		try {
			cars = vehicle.getVehicleCarsAndPositions();
		} catch (Exception ignored) {
			cars = null;
		}
		if (cars == null || cars.isEmpty()) {
			box(matrices, lines, camera, head.x, head.y, head.z, 1.0F, 0.65F, 0.25F);
			return;
		}
		int remaining = figures;
		for (int car = 0; car < cars.size() && remaining > 0 && drawn < FRAME_CAP; car++) {
			final int share = remaining / (cars.size() - car);
			final ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>> bogies = cars.get(car).right();
			double x1 = head.x;
			double y1 = head.y;
			double z1 = head.z;
			double x2 = head.x;
			double y2 = head.y;
			double z2 = head.z;
			if (bogies != null && !bogies.isEmpty()) {
				final Vector start = bogies.get(0).left() != null ? bogies.get(0).left() : bogies.get(0).right();
				final ObjectObjectImmutablePair<Vector, Vector> last = bogies.get(bogies.size() - 1);
				final Vector end = last.right() != null ? last.right() : last.left();
				if (start != null) {
					x1 = start.x;
					y1 = start.y;
					z1 = start.z;
				}
				if (end != null) {
					x2 = end.x;
					y2 = end.y;
					z2 = end.z;
				}
			}
			for (int i = 0; i < share && drawn < FRAME_CAP; i++) {
				PassengerFigures.place(x1, y1, z1, x2, y2, z2, i, share, 0, point);
				box(matrices, lines, camera, point[0], point[1] + 0.2, point[2], 1.0F, 0.65F, 0.25F);
			}
			remaining -= share;
		}
	}

	private void box(MatrixStack matrices, VertexConsumer lines, Vec3d camera, double x, double y, double z, float r, float g, float b) {
		WorldRenderer.drawBox(matrices, lines,
				x - camera.x - BOX, y - camera.y, z - camera.z - BOX,
				x - camera.x + BOX, y - camera.y + HEIGHT, z - camera.z + BOX,
				r, g, b, 1.0F);
		drawn++;
	}

	@Nullable
	private static Vector head(Vehicle vehicle) {
		try {
			return vehicle.getHeadPosition();
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
