package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.mtr.core.data.Lift;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Vehicle;
import org.mtr.core.generated.data.SavedRailBaseSchema;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.Box;
import org.mtr.mod.block.BlockEscalatorSide;
import org.mtr.mod.block.BlockEscalatorStep;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.passenger.PassengerService;
import org.mtr.mod.resource.VehicleResourceCache;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws the server's waiting and riding counts as varied people (see {@link PassengerLook}) on platforms and inside cars.
 * Platform ends are {@code SavedRailBaseSchema.position1} and {@code position2}. Car positions come from
 * {@link Vehicle#getVehicleCarsAndPositions()}.
 */
public final class PassengerRenderer {

	/** Above the rail, where people stand in a car. */
	private static final double CAR_FLOOR = 1;
	private static final int WALKS_MAX = 256;
	/** How far outside a door someone lines up before stepping through it. */
	private static final double STEP_OUT = 0.9;
	private static final double FALLBACK_DOOR_SIDE = 1.5;
	/** Leg swing, in radians a second. */
	private static final double WALK_STRIDES = 8;
	private static final long WALK_STAGGER_MILLIS = 400;
	/** A train this close to a platform's rail is at it. */
	private static final double AT_PLATFORM = 12;
	/** Those who get off keep walking along the platform this far, fading out. */
	private static final double WALK_AWAY = 9;
	/** Walkers started since the game opened: the dev run reports it. */
	public static int walksStarted;
	/** Of those, the ones the walk grid routed and the ones left on a straight leg: the dev run reports them. */
	public static int walksRouted;
	public static int walksStraight;
	/** Exits on the last map scanned, one way and the other: the dev run reports them. */
	public static int exitsLeaving;
	public static int exitsArriving;
	/** Render-thread time for one frame's grid scan, averaged, and the worst frame of working out routes: the dev run reports them. */
	public static double scanMicros;
	public static double routeMicros;
	/** Worst frame of finding a map's exits and lift landings: the dev run reports it. */
	public static double exitMicros;
	/** Sees every walk as it starts; only the dev run sets it. */
	@Nullable
	public static Consumer<PassengerWalk> walkListener;
	/** Render-thread time for all figures, averaged, and how many the last frame drew: the dev run and panel report them. */
	public static double renderMicros;
	public static int lastFigures;
	/** Walkers alive on the last frame: the panel's cost read-out reports it. */
	public static int lastWalkers;
	/** A hard stop so a long draw distance cannot fill the frame. */
	private static final int FRAME_CAP = 256;
	/** Floors are read from the world this often, not every frame. */
	private static final long SPOT_REFRESH_MILLIS = 2000;
	private static final int SPOT_CACHE_SIZE = 64;
	/** Waiting spots slide to their new places over this long, so a train arriving does not snap the crowd sideways. */
	private static final long SPOT_BLEND_MILLIS = 1200;
	/** Door positions are read this often while a train stands at a platform. */
	private static final long DOOR_REFRESH_MILLIS = 1000;
	/** Waiting this close to a door steps aside while people get off. */
	private static final double STEP_ASIDE_REACH = 2.6;
	/** Grid cells read each frame, so a scan never shows as a hitch. */
	private static final int SCAN_CELLS = 400;
	/** Blocks are read again this often, so a walker sees what was built. */
	private static final long GRID_REFRESH_MILLIS = 30_000;
	/** Levels below the platform's floor. The grid changes level only on steps, which keeps people out of the track pit. */
	private static final int GRID_DOWN = 8;
	private static final int GRID_LEVELS = 17;
	/** A way that climbs this far off the platform's floor is an exit. */
	private static final int EXIT_RISE = 3;
	private static final int EXIT_APART = 5;
	/** How far a lift's landing can be from the floor people wait on. */
	private static final int LANDING_RADIUS = 3;
	/** Those leaving or arriving fade over this many blocks, at the exit. */
	private static final double FADE_BLOCKS = 2;
	private static final int GRID_CELLS_MAX = 120_000;

	@Nullable
	private static final Field POSITION_1 = field("position1");
	@Nullable
	private static final Field POSITION_2 = field("position2");
	private static boolean loggedEnds;

	/** {@code at} is where the crowd is heading; {@code from} is where it was when the train changed the doors. */
	private record Spots(int count, long millis, double[] at, double[] from, long blendStart, double[] doors) {
	}

	/** The grid is null when the platform is too big to scan: its walkers keep the straight leg. Exits are x, feet, z cells. */
	private record WalkMap(long platform, @Nullable PassengerWalkGrid grid, long millis, int[] spot, List<int[]> leaving, List<int[]> arriving) {
	}

	/** A count that arrived before the doors were wide enough to walk through. */
	private record PendingDoors(int change, long millis) {
	}

	/** A stopped train's door positions and when they were read. */
	private record DoorAnchors(double[] doors, long millis) {
	}

	private static final Identifier[] SKINS = new Identifier[PassengerLook.SKINS];

	static {
		for (int i = 0; i < SKINS.length; i++) {
			SKINS[i] = new Identifier("aurelia", String.format("textures/entity/passenger/p%02d.png", i));
		}
	}

	private final double[] point = new double[3];
	private final BlockPos.Mutable mutable = new BlockPos.Mutable();
	private final Map<Long, Spots> spotCache = new HashMap<>();
	private final Map<Long, PendingDoors> pendingDoors = new HashMap<>();
	private final Map<Long, DoorAnchors> doorCache = new HashMap<>();
	private final Map<Long, DoorAnchors> trainDoorCache = new HashMap<>();
	/** A platform's waiting spots with the alighting crowd's step-aside applied, reused between frames. */
	private double[] shifted = new double[0];
	private int drawn;
	private final List<PassengerWalk> walks = new ArrayList<>();
	private final Map<Long, WalkMap> walkMaps = new HashMap<>();
	@Nullable
	private WalkMap scanning;
	private long nextScanPick;
	@Nullable
	private PassengerService.Counts lastCounts;
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
			lastCounts = null;
			pendingDoors.clear();
			return;
		}
		final PassengerService.Counts counts = PassengerClient.counts();
		final long frameStart = System.nanoTime();
		final Vec3d camera = context.camera().getPos();
		final long now = System.currentTimeMillis();
		world = context.world();
		if (counts != lastCounts) {
			if (lastCounts != null && settings.people()) {
				startWalks(lastCounts, counts, settings, camera, context.world(), now);
				startArrivals(lastCounts, counts, settings, camera, context.world(), now);
			}
			lastCounts = counts;
		}
		if (settings.people()) {
			releasePendingDoors(settings, camera, context.world(), now);
		}
		if (counts.waiting().isEmpty() && counts.riding().isEmpty() && walks.isEmpty()) {
			lastFigures = 0;
			lastWalkers = 0;
			renderMicros = 0;
			return;
		}
		final MatrixStack matrices = context.matrixStack();
		provider = context.consumers();
		drawn = 0;
		if (settings.people() && settings.routes()) {
			scanWalkMaps(settings, camera, now);
		} else if (!walkMaps.isEmpty() || scanning != null) {
			// No maps means the straight walks, and no arrivals by exits
			walkMaps.clear();
			scanning = null;
		}
		platforms(counts.waiting(), settings, camera, matrices, context.world());
		trains(counts.riding(), settings, camera, matrices, now);
		walks(settings, camera, matrices, now);
		if (provider instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
		lastFigures = drawn;
		lastWalkers = walks.size();
		renderMicros = renderMicros * 0.95 + (System.nanoTime() - frameStart) / 1000D * 0.05;
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
			final PassengerFigures.Detail detail = PassengerFigures.detail(distance, settings.drawDistance(), settings.detailDistance());
			final int figures = PassengerFigures.toDraw(entry.getValue() - walkingTo(entry.getKey(), now), settings.maxPerPlace(), distance, settings.drawDistance(), settings.detailDistance());
			if (figures <= 0) {
				continue;
			}
			final double[] doors = doorsAt(entry.getKey(), x1, y1, z1, x2, y2, z2, now);
			Spots spots = spotCache.get(entry.getKey());
			if (spots == null || spots.count != figures || now - spots.millis > SPOT_REFRESH_MILLIS || !Arrays.equals(spots.doors, doors)) {
				if (spotCache.size() > SPOT_CACHE_SIZE) {
					spotCache.clear();
				}
				final double[] found = new double[figures * 3];
				final PassengerFigures.Ground ground = (x, z, railY) -> floor(world, x, z, railY);
				if (doors.length > 0) {
					PassengerFigures.spotsAtDoors(x1, y1, z1, x2, y2, z2, doors, figures, ground, found);
				} else {
					PassengerFigures.spots(x1, y1, z1, x2, y2, z2, figures, ground, found);
				}
				final double[] from = spots != null && spots.count == figures ? spots.at : null;
				spots = new Spots(figures, now, found, from, now, doors);
				spotCache.put(entry.getKey(), spots);
			}
			waitingPositions(entry.getKey(), spots, figures, now, x1, z1, x2, z2, doors);
			final double length = (x2 - x1) * (x2 - x1) + (z2 - z1) * (z2 - z1);
			for (int i = 0; i < figures && drawn < FRAME_CAP; i++) {
				final double fx = shifted[i * 3];
				final double fz = shifted[i * 3 + 2];
				final double along = length <= 0 ? 0 : MathHelper.clamp(((fx - x1) * (x2 - x1) + (fz - z1) * (z2 - z1)) / length, 0, 1);
				final float yaw = PassengerLook.yawToward(x1 + (x2 - x1) * along - fx, z1 + (z2 - z1) * along - fz);
				figure(matrices, camera, settings, fx, shifted[i * 3 + 1], fz, i, settings.waitingHue(), entry.getKey(), yaw, detail);
			}
		}
	}

	/** The crowd slides from where it was to where the train's doors want it, then steps aside for people getting off. */
	private void waitingPositions(long platform, Spots spots, int figures, long now, double x1, double z1, double x2, double z2, double[] doors) {
		if (shifted.length < figures * 3) {
			shifted = new double[figures * 3];
		}
		final double t = spots.from == null ? 1 : Math.min(1, (now - spots.blendStart) / (double) SPOT_BLEND_MILLIS);
		for (int i = 0; i < figures * 3; i++) {
			shifted[i] = spots.from == null ? spots.at[i] : spots.from[i] + (spots.at[i] - spots.from[i]) * t;
		}
		final double strength = stepAsideStrength(platform, now);
		if (strength > 0) {
			PassengerFigures.stepAside(shifted, shifted, figures, x1, z1, x2, z2, doors, strength, STEP_ASIDE_REACH, PassengerDoors.STEP_ASIDE);
		}
	}

	/** The strongest step-aside among the people getting off at this platform. */
	private double stepAsideStrength(long platform, long now) {
		double strength = 0;
		for (final PassengerWalk walk : walks) {
			if (!walk.boarding() && !walk.arriving() && walk.platform() == platform) {
				strength = Math.max(strength, PassengerDoors.stepAsideStrength(walk.progress(now)));
			}
		}
		return strength;
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

	/** Whether someone can stand with their feet in this block: {@link #floor}'s rule, plus nothing they would have to climb. */
	private boolean standable(int x, int y, int z) {
		final VoxelShape below = world.getBlockState(mutable.set(x, y - 1, z)).getCollisionShape(world, mutable);
		return !below.isEmpty() && below.getMax(Direction.Axis.Y) <= 1 && room(x, y, z) && room(x, y + 1, z);
	}

	/** An escalator's handrail runs along the edge of the block over each step, so it never stops someone riding. */
	private boolean room(int x, int y, int z) {
		final BlockState state = world.getBlockState(mutable.set(x, y, z));
		return state.getBlock() instanceof BlockEscalatorSide || state.getCollisionShape(world, mutable).isEmpty();
	}

	/** What the block under these feet allows: stairs and slabs both ways, an escalator the way it runs. */
	private int climb(int x, int y, int z) {
		final BlockState below = world.getBlockState(mutable.set(x, y - 1, z));
		if (below.getBlock() instanceof BlockEscalatorStep) {
			if (!below.get(BlockEscalatorStep.STATUS.data)) {
				return PassengerWalkGrid.BOTH_WAYS;
			}
			return below.get(BlockEscalatorStep.DIRECTION.data) ? PassengerWalkGrid.UP_ONLY : PassengerWalkGrid.DOWN_ONLY;
		}
		return below.getBlock() instanceof StairsBlock || below.getBlock() instanceof SlabBlock ? PassengerWalkGrid.BOTH_WAYS : PassengerWalkGrid.FLAT;
	}

	/** One slice of the scan of the nearest platform whose map is missing or old. World reads stay on this thread. */
	private void scanWalkMaps(PassengerDrawSettings.Values settings, Vec3d camera, long now) {
		if (scanning == null) {
			if (now < nextScanPick) {
				return;
			}
			nextScanPick = now + SPOT_REFRESH_MILLIS;
			scanning = nextWalkMap(settings, camera, now);
			if (scanning == null) {
				return;
			}
		}
		final long start = System.nanoTime();
		final boolean done = scanning.grid().scan(this::standable, this::climb, SCAN_CELLS);
		scanMicros = scanMicros * 0.95 + (System.nanoTime() - start) / 1000D * 0.05;
		if (done) {
			if (walkMaps.size() > SPOT_CACHE_SIZE) {
				walkMaps.clear();
			}
			final long exitsStart = System.nanoTime();
			walkMaps.put(scanning.platform(), withExits(scanning));
			exitMicros = Math.max(exitMicros, (System.nanoTime() - exitsStart) / 1000D);
			scanning = null;
		}
	}

	/** The ways off this floor the grid can see, and the landings of the lifts that stop on it. */
	private WalkMap withExits(WalkMap map) {
		final PassengerWalkGrid grid = map.grid();
		final int[] seed = grid.nearest(map.spot()[0], map.spot()[1], map.spot()[2], LANDING_RADIUS);
		if (seed == null) {
			return map;
		}
		final List<int[]> leaving = new ArrayList<>(grid.exits(seed[0], seed[1], seed[2], EXIT_RISE, EXIT_APART, false, PassengerDrawSettings.get().maxPath()));
		final List<int[]> arriving = new ArrayList<>(grid.exits(seed[0], seed[1], seed[2], EXIT_RISE, EXIT_APART, true, PassengerDrawSettings.get().maxPath()));
		for (final Lift lift : MinecraftClientData.getInstance().lifts) {
			lift.iterateFloors(floor -> {
				final Position at = floor.getPosition();
				if (Math.abs(at.getY() - seed[1]) <= 1) {
					final double out = lift.getDepth() / 2 + 1;
					final double angle = lift.getAngle().angleRadians;
					final int[] landing = grid.nearest(MathHelper.floor(at.getX() + lift.getOffsetX() + Math.cos(angle) * out), seed[1], MathHelper.floor(at.getZ() + lift.getOffsetZ() + Math.sin(angle) * out), LANDING_RADIUS);
					if (landing != null && grid.path(seed[0], seed[1], seed[2], landing[0], landing[1], landing[2], PassengerDrawSettings.get().maxPath()) != null && grid.path(landing[0], landing[1], landing[2], seed[0], seed[1], seed[2], PassengerDrawSettings.get().maxPath()) != null) {
						leaving.add(landing);
						arriving.add(landing);
					}
				}
			});
		}
		exitsLeaving = leaving.size();
		exitsArriving = arriving.size();
		return new WalkMap(map.platform(), grid, map.millis(), map.spot(), leaving, arriving);
	}

	@Nullable
	private WalkMap nextWalkMap(PassengerDrawSettings.Values settings, Vec3d camera, long now) {
		Platform nearest = null;
		double[] nearestLine = null;
		double nearestDistance = settings.drawDistance();
		for (final Platform platform : MinecraftClientData.getInstance().platforms) {
			final WalkMap known = walkMaps.get(platform.getId());
			if (known == null || now - known.millis() > GRID_REFRESH_MILLIS) {
				final double[] line = line(platform);
				final double distance = PassengerFigures.distanceToPlatform(camera.x, camera.y, camera.z, line[0], line[1], line[2], line[3], line[4], line[5]);
				if (distance <= nearestDistance) {
					nearest = platform;
					nearestLine = line;
					nearestDistance = distance;
				}
			}
		}
		if (nearest == null) {
			return null;
		}
		final double[] spot = new double[3];
		PassengerFigures.spots(nearestLine[0], nearestLine[1], nearestLine[2], nearestLine[3], nearestLine[4], nearestLine[5], 1, (x, z, railY) -> floor(world, x, z, railY), spot);
		final int minX = MathHelper.floor(Math.min(nearestLine[0], nearestLine[3])) - settings.walkRange();
		final int minZ = MathHelper.floor(Math.min(nearestLine[2], nearestLine[5])) - settings.walkRange();
		final int sizeX = MathHelper.floor(Math.max(nearestLine[0], nearestLine[3])) + settings.walkRange() - minX + 1;
		final int sizeZ = MathHelper.floor(Math.max(nearestLine[2], nearestLine[5])) + settings.walkRange() - minZ + 1;
		if ((long) sizeX * sizeZ * GRID_LEVELS > GRID_CELLS_MAX) {
			walkMaps.put(nearest.getId(), new WalkMap(nearest.getId(), null, now, cell(spot), List.of(), List.of()));
			return null;
		}
		return new WalkMap(nearest.getId(), new PassengerWalkGrid(minX, MathHelper.ceil(spot[1]) - GRID_DOWN, minZ, sizeX, GRID_LEVELS, sizeZ), now, cell(spot), List.of(), List.of());
	}

	private static int[] cell(double[] spot) {
		return new int[]{MathHelper.floor(spot[0]), MathHelper.ceil(spot[1]), MathHelper.floor(spot[2])};
	}

	/**
	 * Door x, z pairs of every train standing at this platform with its doors on this side. Read at most once a second,
	 * because reading a train's doorways goes through its resource. Empty when no train is at the platform.
	 */
	private double[] doorsAt(long platform, double x1, double y1, double z1, double x2, double y2, double z2, long now) {
		final DoorAnchors cached = doorCache.get(platform);
		if (cached != null && now - cached.millis() <= DOOR_REFRESH_MILLIS) {
			return cached.doors();
		}
		final DoubleArrayList found = new DoubleArrayList();
		for (final VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
			if (vehicle.isMoving()) {
				continue;
			}
			final double[] doors = trainDoors(vehicle, now);
			if (doors.length == 0) {
				continue;
			}
			final Platform at = platformAt(doors);
			if (at == null || at.getId() != platform) {
				continue;
			}
			for (int i = 0; i < doors.length; i += 4) {
				if (PassengerFigures.distanceToPlatform(outside(doors, i, 0), y1, outside(doors, i, 1), x1, y1, z1, x2, y2, z2) <= 2.5) {
					found.add(doors[i]);
					found.add(doors[i + 1]);
				}
			}
		}
		if (doorCache.size() > SPOT_CACHE_SIZE) {
			doorCache.clear();
		}
		final double[] doors = found.toDoubleArray();
		doorCache.put(platform, new DoorAnchors(doors, now));
		return doors;
	}

	/** One train's doors, read at most once a second while it stands still. */
	private double[] trainDoors(VehicleExtension vehicle, long now) {
		final DoorAnchors cached = trainDoorCache.get(vehicle.getId());
		if (cached != null && now - cached.millis() <= DOOR_REFRESH_MILLIS) {
			return cached.doors();
		}
		final var cars = cars(vehicle);
		final double[] doors = cars == null || cars.isEmpty() ? new double[0] : doors(vehicle, cars);
		if (trainDoorCache.size() > SPOT_CACHE_SIZE) {
			trainDoorCache.clear();
		}
		trainDoorCache.put(vehicle.getId(), new DoorAnchors(doors, now));
		return doors;
	}

	private void trains(Map<Long, Integer> riding, PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices, long now) {
		for (final VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
			if (drawn >= FRAME_CAP) {
				return;
			}
			final Integer counted = riding.get(vehicle.getId());
			final int people = counted == null ? 0 : counted - walkingIn(vehicle.getId(), now);
			if (people <= 0) {
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
			final PassengerFigures.Detail detail = PassengerFigures.detail(distance, settings.drawDistance(), settings.detailDistance());
			final int figures = PassengerFigures.toDraw(people, settings.maxPerPlace(), distance, settings.drawDistance(), settings.detailDistance());
			if (figures > 0) {
				drawInCars(cars, figures, detail, settings, camera, matrices, vehicle.getId());
			}
		}
	}

	private void drawInCars(ObjectArrayList<ObjectObjectImmutablePair<org.mtr.core.data.VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars, int figures, PassengerFigures.Detail detail, PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices, long vehicleId) {
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
				figure(matrices, camera, settings, point[0], point[1] + CAR_FLOOR, point[2], i, settings.ridingHue(), vehicleId + car, PassengerLook.yawToward(end.x - start.x, end.z - start.z) + (i % 2) * 180, detail);
			}
		}
	}

	private int walkingIn(long vehicle, long now) {
		int count = 0;
		for (final PassengerWalk walk : walks) {
			if (walk.boarding() && walk.vehicle() == vehicle && !walk.finished(now)) {
				count++;
			}
		}
		return count;
	}

	private int walkingTo(long platform, long now) {
		int count = 0;
		for (final PassengerWalk walk : walks) {
			if (walk.arriving() && walk.platform() == platform && !walk.finished(now)) {
				count++;
			}
		}
		return count;
	}

	/** New people at a platform come in by its exits and walk to their spots. With no exit they just appear, as before. */
	private void startArrivals(PassengerService.Counts before, PassengerService.Counts after, PassengerDrawSettings.Values settings, Vec3d camera, World world, long now) {
		for (final Map.Entry<Long, Integer> entry : after.waiting().entrySet()) {
			final long id = entry.getKey();
			final int change = entry.getValue() - before.waiting().getOrDefault(id, 0);
			final WalkMap map = walkMaps.get(id);
			final Platform platform = MinecraftClientData.getInstance().platformIdMap.get(id);
			if (change <= 0 || map == null || map.arriving().isEmpty() || platform == null) {
				continue;
			}
			final double[] line = line(platform);
			if (PassengerFigures.distanceToPlatform(camera.x, camera.y, camera.z, line[0], line[1], line[2], line[3], line[4], line[5]) > settings.drawDistance()) {
				continue;
			}
			final int shown = Math.min(settings.maxPerPlace(), entry.getValue());
			final int people = Math.min(settings.maxWalkers(), Math.min(shown, change));
			final double[] spots = new double[shown * 3];
			final double[] doors = doorsAt(id, line[0], line[1], line[2], line[3], line[4], line[5], now);
			final PassengerFigures.Ground ground = (x, z, railY) -> floor(world, x, z, railY);
			if (doors.length > 0) {
				PassengerFigures.spotsAtDoors(line[0], line[1], line[2], line[3], line[4], line[5], doors, shown, ground, spots);
			} else {
				PassengerFigures.spots(line[0], line[1], line[2], line[3], line[4], line[5], shown, ground, spots);
			}
			final long routeStart = System.nanoTime();
			for (int i = 0; i < people && walks.size() < WALKS_MAX; i++) {
				final int spot = shown - 1 - i;
				final int[] exit = map.arriving().get(i % map.arriving().size());
				final double[] points = leg(id, exit[0] + 0.5, exit[1], exit[2] + 0.5, spots[spot * 3], spots[spot * 3 + 1], spots[spot * 3 + 2]);
				walks.add(PassengerWalk.of(points, now + i * WALK_STAGGER_MILLIS, id, spot, id, 0, false, true));
				walksStarted++;
				if (walkListener != null) {
					walkListener.accept(walks.get(walks.size() - 1));
				}
			}
			routeMicros = Math.max(routeMicros, (System.nanoTime() - routeStart) / 1000D);
		}
	}

	/**
	 * A change in a train's riders at a platform is people crossing the gap: draw them doing it. The count can arrive
	 * while the doors are still opening, so it waits until they are wide enough; a count that never gets open doors is
	 * not a real exchange and starts no walk.
	 */
	private void startWalks(PassengerService.Counts before, PassengerService.Counts after, PassengerDrawSettings.Values settings, Vec3d camera, World world, long now) {
		for (final VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
			final int change = after.riding().getOrDefault(vehicle.getId(), 0) - before.riding().getOrDefault(vehicle.getId(), 0);
			if (change == 0) {
				continue;
			}
			if (!PassengerDoors.canCross(vehicle.persistentVehicleData.getDoorValue())) {
				final PendingDoors pending = pendingDoors.get(vehicle.getId());
				pendingDoors.put(vehicle.getId(), new PendingDoors((pending == null ? 0 : pending.change()) + change, pending == null ? now : pending.millis()));
			} else {
				startVehicleWalks(vehicle, change, before, settings, camera, world, now);
			}
		}
	}

	/** Counts held while the doors open: start them when the gap is clear, forget them if the train was not really stopping. */
	private void releasePendingDoors(PassengerDrawSettings.Values settings, Vec3d camera, World world, long now) {
		if (pendingDoors.isEmpty()) {
			return;
		}
		final Iterator<Map.Entry<Long, PendingDoors>> iterator = pendingDoors.entrySet().iterator();
		while (iterator.hasNext()) {
			final Map.Entry<Long, PendingDoors> entry = iterator.next();
			final PendingDoors pending = entry.getValue();
			final VehicleExtension vehicle = vehicle(entry.getKey());
			if (vehicle == null || PassengerDoors.tooLate(pending.millis(), now)) {
				iterator.remove();
			} else if (PassengerDoors.canCross(vehicle.persistentVehicleData.getDoorValue())) {
				iterator.remove();
				startVehicleWalks(vehicle, pending.change(), lastCounts == null ? PassengerService.Counts.NONE : lastCounts, settings, camera, world, now);
			}
		}
	}

	/** Draws one train's exchange: boarders leave spots by its doors, alighters come out and head for an exit. */
	private void startVehicleWalks(VehicleExtension vehicle, int change, PassengerService.Counts counts, PassengerDrawSettings.Values settings, Vec3d camera, World world, long now) {
		final var cars = cars(vehicle);
		if (cars == null || cars.isEmpty()) {
			return;
		}
		final double[] doors = doors(vehicle, cars);
		if (doors.length == 0) {
			return;
		}
		final Platform platform = platformAt(doors);
		if (platform == null) {
			return;
		}
		final double[] line = line(platform);
		if (PassengerFigures.distanceToPlatform(camera.x, camera.y, camera.z, line[0], line[1], line[2], line[3], line[4], line[5]) > settings.drawDistance()) {
			return;
		}
		final int people = Math.min(settings.maxWalkers(), Math.abs(change));
		if (people == 0) {
			return;
		}
		final int shown = Math.max(people, Math.min(settings.maxPerPlace(), counts.waiting().getOrDefault(platform.getId(), 0)));
		final double[] spots = new double[shown * 3];
		PassengerFigures.spotsAtDoors(line[0], line[1], line[2], line[3], line[4], line[5], doorPairs(doors), shown, (x, z, railY) -> floor(world, x, z, railY), spots);
		final long routeStart = System.nanoTime();
		for (int i = 0; i < people && walks.size() < WALKS_MAX; i++) {
			final int spot = shown - 1 - i;
			final double x = spots[spot * 3];
			final double y = spots[spot * 3 + 1];
			final double z = spots[spot * 3 + 2];
			final long start = now + i * WALK_STAGGER_MILLIS;
			if (change > 0) {
				final int door = nearestDoor(platform.getId(), doors, x, y, z);
				walks.add(PassengerWalk.of(toCar(platform.getId(), doors, door, x, y, z), start, platform.getId(), spot, platform.getId(), vehicle.getId(), true));
			} else if (map(platform.getId()) != null && !map(platform.getId()).leaving().isEmpty()) {
				final List<int[]> exits = map(platform.getId()).leaving();
				final int[] exit = exits.get(i % exits.size());
				final int door = nearestDoor(platform.getId(), doors, x, y, z);
				walks.add(PassengerWalk.of(fromCar(platform.getId(), doors, door, y, exit), start, vehicle.getId() + 1, spot, platform.getId(), vehicle.getId(), false));
			} else {
				final double dx = line[3] - line[0];
				final double dz = line[5] - line[2];
				final double length = Math.max(1.0E-4, Math.hypot(dx, dz));
				final double sign = i % 2 == 0 ? 1 : -1;
				final double reach = reach(platform.getId(), x, y, z, dx / length * sign, dz / length * sign);
				final double awayX = x + dx / length * reach * sign;
				final double awayZ = z + dz / length * reach * sign;
				final int door = nearestDoor(platform.getId(), doors, awayX, y, awayZ);
				walks.add(PassengerWalk.of(reversed(toCar(platform.getId(), doors, door, awayX, y, awayZ)), start, vehicle.getId() + 1, spot, platform.getId(), vehicle.getId(), false));
			}
			walksStarted++;
			if (walkListener != null) {
				walkListener.accept(walks.get(walks.size() - 1));
			}
		}
		routeMicros = Math.max(routeMicros, (System.nanoTime() - routeStart) / 1000D);
	}

	@Nullable
	private VehicleExtension vehicle(long id) {
		for (final VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
			if (vehicle.getId() == id) {
				return vehicle;
			}
		}
		return null;
	}

	/** The x, z pairs of a doors() array, for the spot helpers. */
	private static double[] doorPairs(double[] doors) {
		final double[] pairs = new double[doors.length / 2];
		for (int i = 0; i < doors.length; i += 4) {
			pairs[i / 2] = doors[i];
			pairs[i / 2 + 1] = doors[i + 1];
		}
		return pairs;
	}

	/**
	 * Every door of the train as door x, z then the car's middle behind it x, z. The doorways are the ones MTR lets players walk
	 * through; a train whose resource has none uses a door each side a quarter from each end of the car.
	 */
	private static double[] doors(VehicleExtension vehicle, ObjectArrayList<ObjectObjectImmutablePair<org.mtr.core.data.VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars) {
		final DoubleArrayList doors = new DoubleArrayList();
		for (int car = 0; car < cars.size(); car++) {
			final ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>> bogies = cars.get(car).right();
			if (bogies == null || bogies.isEmpty()) {
				continue;
			}
			final Vector start = bogies.get(0).left() != null ? bogies.get(0).left() : bogies.get(0).right();
			final ObjectObjectImmutablePair<Vector, Vector> last = bogies.get(bogies.size() - 1);
			final Vector end = last.right() != null ? last.right() : last.left();
			if (start == null || end == null) {
				continue;
			}
			final double middleX = (start.x + end.x) / 2;
			final double middleZ = (start.z + end.z) / 2;
			final double length = Math.max(1.0E-4, Math.hypot(end.x - start.x, end.z - start.z));
			final double forwardX = (end.x - start.x) / length;
			final double forwardZ = (end.z - start.z) / length;
			final int doorsBefore = doors.size();
			final int carNumber = car;
			final int total = cars.size();
			CustomResourceLoader.getVehicleById(vehicle.getTransportMode(), cars.get(car).left().getVehicleId(), details -> {
				final VehicleResourceCache cache = details.left().getCachedVehicleResource(carNumber, total, false);
				if (cache != null) {
					for (final Box doorway : cache.doorways) {
						addDoor(doors, middleX, middleZ, forwardX, forwardZ, (doorway.getMinZMapped() + doorway.getMaxZMapped()) / 2, (doorway.getMinXMapped() + doorway.getMaxXMapped()) / 2);
					}
				}
			});
			if (doors.size() == doorsBefore) {
				final double quarter = length / 4;
				for (final double along : new double[]{-quarter, quarter}) {
					addDoor(doors, middleX, middleZ, forwardX, forwardZ, along, FALLBACK_DOOR_SIDE);
					addDoor(doors, middleX, middleZ, forwardX, forwardZ, along, -FALLBACK_DOOR_SIDE);
				}
			}
		}
		return doors.toDoubleArray();
	}

	private static void addDoor(DoubleArrayList doors, double middleX, double middleZ, double forwardX, double forwardZ, double along, double across) {
		final double insideX = middleX + forwardX * along;
		final double insideZ = middleZ + forwardZ * along;
		doors.add(insideX - forwardZ * across);
		doors.add(insideZ + forwardX * across);
		doors.add(insideX);
		doors.add(insideZ);
	}

	/** The index of the door nearest the point, leaving out doors with something built in front of them unless all have. */
	private int nearestDoor(long platform, double[] doors, double x, double y, double z) {
		final WalkMap map = walkMaps.get(platform);
		final int feet = MathHelper.ceil(y);
		int best = -1;
		int bestBlocked = 0;
		double bestDistance = Double.MAX_VALUE;
		double bestBlockedDistance = Double.MAX_VALUE;
		for (int i = 0; i < doors.length; i += 4) {
			final double distance = Math.hypot(doors[i] - x, doors[i + 1] - z);
			if (distance < bestBlockedDistance) {
				bestBlockedDistance = distance;
				bestBlocked = i;
			}
			if (distance < bestDistance && (map == null || map.grid() == null || map.grid().standable(MathHelper.floor(outside(doors, i, 0)), feet, MathHelper.floor(outside(doors, i, 1))))) {
				bestDistance = distance;
				best = i;
			}
		}
		return best < 0 ? bestBlocked : best;
	}

	/** Where someone lines up outside a door: {@code axis} 0 for x, 1 for z. */
	private static double outside(double[] doors, int door, int axis) {
		final double across = Math.max(1.0E-4, Math.hypot(doors[door] - doors[door + 2], doors[door + 1] - doors[door + 3]));
		return doors[door + axis] + (doors[door + axis] - doors[door + axis + 2]) / across * STEP_OUT;
	}

	/** From a spot on the platform: up to the door, through it, and into the car. */
	private double[] toCar(long platform, double[] doors, int door, double x, double y, double z) {
		final double doorX = doors[door];
		final double doorZ = doors[door + 1];
		final double insideX = doors[door + 2];
		final double insideZ = doors[door + 3];
		final double[] leg = leg(platform, x, y, z, outside(doors, door, 0), outside(doors, door, 1));
		final double[] points = Arrays.copyOf(leg, leg.length + 6);
		final double doorY = leg[leg.length - 2];
		System.arraycopy(new double[]{doorX, doorY, doorZ, insideX, doorY, insideZ}, 0, points, leg.length, 6);
		return points;
	}

	/** From inside the car, out of the door and on to an exit: walked forwards, because an escalator only runs one way. */
	private double[] fromCar(long platform, double[] doors, int door, double y, int[] exit) {
		final double[] leg = leg(platform, outside(doors, door, 0), y, outside(doors, door, 1), exit[0] + 0.5, exit[1], exit[2] + 0.5);
		final double[] points = new double[leg.length + 6];
		System.arraycopy(new double[]{doors[door + 2], leg[1], doors[door + 3], doors[door], leg[1], doors[door + 1]}, 0, points, 0, 6);
		System.arraycopy(leg, 0, points, 6, leg.length);
		return points;
	}

	@Nullable
	private WalkMap map(long platform) {
		return walkMaps.get(platform);
	}

	/** A walk across the platform, round whatever stands in the way; the straight leg when there is no map yet or no way round. */
	private double[] leg(long platform, double x, double y, double z, double toX, double toZ) {
		return leg(platform, x, y, z, toX, y, toZ);
	}

	private double[] leg(long platform, double x, double y, double z, double toX, double toY, double toZ) {
		final WalkMap map = walkMaps.get(platform);
		if (map != null && map.grid() != null) {
			final PassengerWalkGrid grid = map.grid();
			final int feet = MathHelper.ceil(y);
			final int[] from = grid.nearest(MathHelper.floor(x), feet, MathHelper.floor(z), 2);
			final int[] to = grid.nearest(MathHelper.floor(toX), MathHelper.ceil(toY), MathHelper.floor(toZ), 2);
			final int[] cells = from == null || to == null ? null : grid.path(from[0], from[1], from[2], to[0], to[1], to[2], PassengerDrawSettings.get().maxPath());
			if (cells != null) {
				// A waiting spot can be inside something built after it was picked
				final boolean free = grid.standable(MathHelper.floor(x), feet, MathHelper.floor(z));
				final double[] turns = grid.turns(cells, free ? x : from[0] + 0.5, free ? z : from[2] + 0.5, toX, toZ);
				for (int i = 0; i < turns.length; i += 3) {
					turns[i + 1] = standHeight(turns[i], (int) turns[i + 1], turns[i + 2]);
				}
				walksRouted++;
				return turns;
			}
		}
		walksStraight++;
		return new double[]{x, y, z, toX, toY, toZ};
	}

	private double standHeight(double x, int feet, double z) {
		final VoxelShape below = world.getBlockState(mutable.set(MathHelper.floor(x), feet - 1, MathHelper.floor(z))).getCollisionShape(world, mutable);
		return below.isEmpty() ? feet : feet - 1 + below.getMax(Direction.Axis.Y);
	}

	/** How far along the platform someone can walk away from a spot before the floor ends. */
	private double reach(long platform, double x, double y, double z, double alongX, double alongZ) {
		final WalkMap map = walkMaps.get(platform);
		if (map != null && map.grid() != null) {
			final int feet = MathHelper.ceil(y);
			for (double reach = WALK_AWAY; reach >= 1; reach--) {
				if (map.grid().standable(MathHelper.floor(x + alongX * reach), feet, MathHelper.floor(z + alongZ * reach))) {
					return reach;
				}
			}
		}
		return WALK_AWAY;
	}

	private static double[] reversed(double[] points) {
		final double[] result = new double[points.length];
		for (int i = 0; i < points.length; i += 3) {
			System.arraycopy(points, points.length - 3 - i, result, i, 3);
		}
		return result;
	}

	@Nullable
	private Platform platformAt(double[] doors) {
		final int middle = doors.length / 8 * 4;
		final double x = doors[middle];
		final double z = doors[middle + 1];
		Platform best = null;
		double bestDistance = AT_PLATFORM;
		for (final Platform platform : MinecraftClientData.getInstance().platforms) {
			final double[] line = line(platform);
			final double distance = PassengerFigures.distanceToPlatform(x, line[1], z, line[0], line[1], line[2], line[3], line[1], line[5]);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = platform;
			}
		}
		return best;
	}

	/** The platform's rail from end to end, x, y, z twice; rails run through block centres. */
	private double[] line(Platform platform) {
		final Position mid = platform.getMidPosition();
		final Position[] ends = ends(platform);
		final boolean haveEnds = ends != null && ends[0] != null && ends[1] != null;
		return new double[]{
				(haveEnds ? ends[0].getX() : mid.getX()) + 0.5, haveEnds ? ends[0].getY() : mid.getY(), (haveEnds ? ends[0].getZ() : mid.getZ()) + 0.5,
				(haveEnds ? ends[1].getX() : mid.getX()) + 0.5, haveEnds ? ends[1].getY() : mid.getY(), (haveEnds ? ends[1].getZ() : mid.getZ()) + 0.5
		};
	}

	private void walks(PassengerDrawSettings.Values settings, Vec3d camera, MatrixStack matrices, long now) {
		final Iterator<PassengerWalk> iterator = walks.iterator();
		while (iterator.hasNext()) {
			final PassengerWalk walk = iterator.next();
			if (walk.finished(now)) {
				iterator.remove();
			} else if (settings.people() && drawn < FRAME_CAP) {
				final double t = walk.progress(now);
				final double[] at = new double[5];
				walk.at(t, at);
				final double x = at[0];
				final double y = at[1];
				final double z = at[2];
				final PassengerFigures.Detail detail = PassengerFigures.detail(distance(camera, x, y, z), settings.drawDistance(), settings.detailDistance());
				if (detail == PassengerFigures.Detail.NONE) {
					continue;
				}
				final float fade = walk.fade(t, FADE_BLOCKS);
				final PassengerLook look = PassengerLook.of(walk.place(), walk.index());
				if (detail == PassengerFigures.Detail.SIMPLE) {
					simplePerson(matrices, settings, x - camera.x, y - camera.y, z - camera.z, look, fade);
					drawn++;
				} else {
					final float stride = t <= 0 ? -1 : (float) (walk.durationMillis() * t / 1000 * WALK_STRIDES);
					person(matrices, settings, x - camera.x, y - camera.y, z - camera.z, new BlockPos(MathHelper.floor(x), MathHelper.floor(y + 1), MathHelper.floor(z)),
							look, PassengerLook.yawToward(at[3], at[4]), stride, fade);
					drawn++;
				}
			}
		}
	}

	/** One person: a real player model with one of the game's own skins, or the plain boxes when the panel asks for them. */
	private void figure(MatrixStack matrices, Vec3d camera, PassengerDrawSettings.Values settings, double x, double y, double z, int index, int hue, long place, float yaw, PassengerFigures.Detail detail) {
		final double cx = x - camera.x;
		final double cy = y - camera.y;
		final double cz = z - camera.z;
		final PassengerLook look = PassengerLook.of(place, index);
		final boolean simple = detail == PassengerFigures.Detail.SIMPLE;
		if (settings.people()) {
			if (simple) {
				simplePerson(matrices, settings, cx, cy, cz, look, 1);
			} else {
				person(matrices, settings, cx, cy, cz, new BlockPos(MathHelper.floor(x), MathHelper.floor(y + 1), MathHelper.floor(z)), look, yaw, -1, 1);
			}
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
			if (simple) {
				WorldRenderer.renderFilledBox(matrices, consumer, cx - half, cy, cz - half, cx + half, cy + 1.7 * size, cz + half, r, g, b, 1);
			} else {
				WorldRenderer.renderFilledBox(matrices, consumer, cx - half * 0.8, cy, cz - half * 0.8, cx + half * 0.8, cy + 0.75 * size, cz + half * 0.8, 0.16F, 0.18F, 0.24F, 1);
				WorldRenderer.renderFilledBox(matrices, consumer, cx - half, cy + 0.75 * size, cz - half, cx + half, cy + 1.35 * size, cz + half, r, g, b, 1);
				final double head = half * 0.75;
				WorldRenderer.renderFilledBox(matrices, consumer, cx - head, cy + 1.37 * size, cz - head, cx + head, cy + 1.72 * size, cz + head, 0.86F, 0.68F, 0.55F, 1);
			}
		} else {
			final double half = 0.22 * size;
			WorldRenderer.drawBox(matrices, consumer, cx - half, cy, cz - half, cx + half, cy + 1.7 * size, cz + half, r, g, b, 1);
		}
		drawn++;
	}

	/** The middle distance: three boxes with the same silhouette, a fraction of a player model's cost. */
	private void simplePerson(MatrixStack matrices, PassengerDrawSettings.Values settings, double cx, double cy, double cz, PassengerLook look, float alpha) {
		final double size = settings.figureSize() / 100D * look.height();
		final int rgb = look.bagColor();
		final float shade = 0.7F + (look.skin() % 3) * 0.15F;
		final float r = (rgb >> 16 & 0xFF) / 255F * shade;
		final float g = (rgb >> 8 & 0xFF) / 255F * shade;
		final float b = (rgb & 0xFF) / 255F * shade;
		final VertexConsumer consumer = provider.getBuffer(RenderLayer.getDebugFilledBox());
		WorldRenderer.renderFilledBox(matrices, consumer, cx - 0.13 * size, cy, cz - 0.12 * size, cx + 0.13 * size, cy + 0.78 * size, cz + 0.12 * size, 0.16F, 0.18F, 0.24F, alpha);
		WorldRenderer.renderFilledBox(matrices, consumer, cx - 0.2 * size, cy + 0.78 * size, cz - 0.13 * size, cx + 0.2 * size, cy + 1.42 * size, cz + 0.13 * size, r, g, b, alpha);
		WorldRenderer.renderFilledBox(matrices, consumer, cx - 0.12 * size, cy + 1.42 * size, cz - 0.12 * size, cx + 0.12 * size, cy + 1.7 * size, cz + 0.12 * size, 0.86F, 0.68F, 0.55F, alpha);
	}

	private void person(MatrixStack matrices, PassengerDrawSettings.Values settings, double cx, double cy, double cz, BlockPos lightAt, PassengerLook look, float yaw, float stride, float alpha) {
		if (wide == null) {
			final MinecraftClient client = MinecraftClient.getInstance();
			wide = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER), false);
			slim = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER_SLIM), true);
		}
		final PlayerEntityModel<AbstractClientPlayerEntity> model = look.skin() % 2 == 1 ? slim : wide;
		final float time = (System.currentTimeMillis() % 628_318L) / 1000F + look.phase();
		pose(model, look, time, stride);
		final int light = WorldRenderer.getLightmapCoordinates(world, lightAt);
		final float scale = settings.figureSize() / 100F * look.height() * 0.9375F;

		matrices.push();
		matrices.translate(cx, cy, cz);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - yaw - (stride < 0 ? look.turn() : 0)));
		matrices.push();
		matrices.scale(-scale, -scale, scale);
		matrices.translate(0, -1.501, 0);
		final Identifier texture = SKINS[look.skin()];
		model.render(matrices, provider.getBuffer(alpha < 1 ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutoutNoCull(texture)), light, OverlayTexture.DEFAULT_UV, 1, 1, 1, alpha);
		matrices.pop();
		if (alpha >= 1) {
			carried(matrices, look, scale);
		}
		matrices.pop();
	}

	/** Standing still is not frozen: people breathe, shift their arms and look around slowly. */
	private static void pose(PlayerEntityModel<AbstractClientPlayerEntity> model, PassengerLook look, float time, float stride) {
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
		if (stride >= 0) {
			final float swing = MathHelper.sin(stride) * 0.75F;
			model.rightLeg.pitch = swing;
			model.leftLeg.pitch = -swing;
			model.rightLeg.yaw = 0;
			model.leftLeg.yaw = 0;
			model.head.yaw = 0;
			if (look.pose() != PassengerLook.Pose.PHONE) {
				model.rightArm.pitch = -swing * 0.7F;
			}
			if (look.pose() == PassengerLook.Pose.RELAXED || look.pose() == PassengerLook.Pose.PHONE || look.pose() == PassengerLook.Pose.BACKPACK) {
				model.leftArm.pitch = swing * 0.7F;
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
