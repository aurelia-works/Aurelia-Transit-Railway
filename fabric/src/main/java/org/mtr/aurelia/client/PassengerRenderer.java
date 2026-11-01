package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
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
import java.util.UUID;

/**
 * Draws the server's waiting and riding counts as varied people (see {@link PassengerLook}) on platforms and inside cars.
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
	private VertexConsumerProvider provider;
	private World world;
	@Nullable
	private PlayerEntityModel<AbstractClientPlayerEntity> wide;
	@Nullable
	private PlayerEntityModel<AbstractClientPlayerEntity> slim;

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
		provider = context.consumers();
		world = context.world();
		drawn = 0;
		platforms(counts.waiting(), settings, camera, matrices, context.world());
		trains(counts.riding(), settings, camera, matrices);
		if (provider instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
	}

	private void platforms(Map<Long, Integer> waiting, PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices, World world) {
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
				final double fx = spots.at[i * 3];
				final double fz = spots.at[i * 3 + 2];
				final double length = (x2 - x1) * (x2 - x1) + (z2 - z1) * (z2 - z1);
				final double along = length <= 0 ? 0 : MathHelper.clamp(((fx - x1) * (x2 - x1) + (fz - z1) * (z2 - z1)) / length, 0, 1);
				final float yaw = PassengerLook.yawToward(x1 + (x2 - x1) * along - fx, z1 + (z2 - z1) * along - fz);
				figure(matrices, camera, settings, fx, spots.at[i * 3 + 1], fz, i, settings.waitingHue(), entry.getKey(), yaw);
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

	private void trains(Map<Long, Integer> riding, PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices) {
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
				drawInCars(cars, figures, settings, camera, matrices, vehicle.getId());
			}
		}
	}

	private void drawInCars(ObjectArrayList<ObjectObjectImmutablePair<org.mtr.core.data.VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars, int figures, PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices, long vehicleId) {
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
				figure(matrices, camera, settings, point[0], point[1] + CAR_FLOOR, point[2], i, settings.ridingHue(), vehicleId + car, PassengerLook.yawToward(end.x - start.x, end.z - start.z) + (i % 2) * 180);
			}
		}
	}

	/** One person: a real player model with one of the game's own skins, or the plain boxes when the panel asks for them. */
	private void figure(MatrixStack matrices, Vec3d camera, PassengerDrawSettings.Values settings, double x, double y, double z, int index, int hue, long place, float yaw) {
		final double cx = x - camera.x;
		final double cy = y - camera.y;
		final double cz = z - camera.z;
		if (settings.people()) {
			person(matrices, settings, cx, cy, cz, new BlockPos(MathHelper.floor(x), MathHelper.floor(y + 1), MathHelper.floor(z)), PassengerLook.of(place, index), yaw);
			drawn++;
			return;
		}
		final double size = settings.figureSize() / 100D;
		final int rgb = MathHelper.hsvToRgb(hue / (float) PassengerDrawSettings.HUE_MAX, 0.65F, 1);
		final float shade = 0.75F + (index % 3) * 0.125F;
		final float r = (rgb >> 16 & 0xFF) / 255F * shade;
		final float g = (rgb >> 8 & 0xFF) / 255F * shade;
		final float b = (rgb & 0xFF) / 255F * shade;
		final VertexConsumer consumer = provider.getBuffer(settings.solid() ? RenderLayer.getDebugFilledBox() : RenderLayer.getLines());
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

	private void person(MatrixStack matrices, PassengerDrawSettings.Values settings, double cx, double cy, double cz, BlockPos lightAt, PassengerLook look, float yaw) {
		if (wide == null) {
			final MinecraftClient client = MinecraftClient.getInstance();
			wide = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER), false);
			slim = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER_SLIM), true);
		}
		final UUID skin = new UUID(look.skinSeed(), look.skinSeed() * 31);
		final PlayerEntityModel<AbstractClientPlayerEntity> model = "slim".equals(DefaultSkinHelper.getModel(skin)) ? slim : wide;
		final float time = (System.currentTimeMillis() % 628_318L) / 1000F + look.phase();
		pose(model, look, time);
		final int light = WorldRenderer.getLightmapCoordinates(world, lightAt);
		final float scale = settings.figureSize() / 100F * look.height() * 0.9375F;

		matrices.push();
		matrices.translate(cx, cy, cz);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - yaw - look.turn()));
		matrices.push();
		matrices.scale(-scale, -scale, scale);
		matrices.translate(0, -1.501, 0);
		model.render(matrices, provider.getBuffer(RenderLayer.getEntityCutoutNoCull(DefaultSkinHelper.getTexture(skin))), light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 1);
		matrices.pop();
		carried(matrices, look, scale);
		matrices.pop();
	}

	/** Standing still is not frozen: people breathe, shift their arms and look around slowly. */
	private static void pose(PlayerEntityModel<AbstractClientPlayerEntity> model, PassengerLook look, float time) {
		model.child = false;
		model.sneaking = false;
		model.riding = false;
		final float breath = MathHelper.sin(time * 1.3F);
		model.head.pitch = 0.05F + MathHelper.sin(time * 0.31F) * 0.08F;
		model.head.yaw = look.headTurn() + MathHelper.sin(time * 0.23F) * 0.45F;
		model.head.roll = 0;
		model.body.yaw = 0;
		model.rightArm.pitch = breath * 0.03F;
		model.rightArm.yaw = 0;
		model.rightArm.roll = 0.07F + breath * 0.02F;
		model.leftArm.pitch = -breath * 0.03F;
		model.leftArm.yaw = 0;
		model.leftArm.roll = -0.07F - breath * 0.02F;
		model.rightLeg.pitch = 0;
		model.leftLeg.pitch = 0;
		model.rightLeg.yaw = 0.06F;
		model.leftLeg.yaw = -0.06F;
		switch (look.pose()) {
			case PHONE -> {
				model.rightArm.pitch = -1.25F + breath * 0.02F;
				model.rightArm.yaw = -0.35F;
				model.head.pitch = 0.5F;
				model.head.yaw = -0.15F;
			}
			case SHOULDER_BAG -> {
				model.leftArm.pitch = -0.55F;
				model.leftArm.yaw = 0.3F;
			}
			case SUITCASE -> model.leftArm.roll = -0.12F;
			default -> {
			}
		}
		model.hat.copyTransform(model.head);
		model.jacket.copyTransform(model.body);
		model.leftSleeve.copyTransform(model.leftArm);
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftPants.copyTransform(model.leftLeg);
		model.rightPants.copyTransform(model.rightLeg);
	}

	/** What someone has with them, in their own turned space. */
	private void carried(MatrixStack matrices, PassengerLook look, float scale) {
		final float r = (look.bagColor() >> 16 & 0xFF) / 255F;
		final float g = (look.bagColor() >> 8 & 0xFF) / 255F;
		final float b = (look.bagColor() & 0xFF) / 255F;
		final double s = scale;
		switch (look.pose()) {
			case SHOULDER_BAG -> bag(matrices, 0.3 * s, 0.68 * s, -0.09 * s, 0.46 * s, 1.0 * s, 0.09 * s, r, g, b);
			case BACKPACK -> bag(matrices, -0.2 * s, 0.82 * s, -0.3 * s, 0.2 * s, 1.34 * s, -0.14 * s, r, g, b);
			case SUITCASE -> bag(matrices, 0.42 * s, 0, -0.26 * s, 0.6 * s, 0.68 * s, 0.26 * s, r, g, b);
			default -> {
			}
		}
	}

	private void bag(MatrixStack matrices, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b) {
		WorldRenderer.renderFilledBox(matrices, provider.getBuffer(RenderLayer.getDebugFilledBox()), x1, y1, z1, x2, y2, z2, r, g, b, 1);
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
