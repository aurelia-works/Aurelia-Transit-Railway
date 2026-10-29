package org.mtr.aurelia.flight;

import org.junit.jupiter.api.Test;
import org.mtr.core.data.ClientData;
import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.data.AirplanePath;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The airplane bugs are in the simulation jar, and unit tests do not apply mixins, so this drives the
 * jar the way a repeating airport route does and checks the corrections the mixins call.
 */
public final class AirplaneLandingTest {

	@Test
	public void repeatingRouteStopsOnThePlatformInsteadOfTheOrigin() throws Exception {
		final Data data = airport();
		final ObjectArrayList<PathData> main = fly(data);
		SidingPathFinder.generatePathDataDistances(main, 40);
		PathData.writePathCache(main, data, TransportMode.AIRPLANE);

		final PathData platform = main.get(main.size() - 1);
		assertTrue(platform.getRail().isPlatform(), "the flight ends on the arrival platform");
		assertEquals(80, AirplanePath.savedRailSpeed(platform), "a platform's own limit is 80 km/h");
		assertTrue(platform.getSpeedLimitKilometersPerHour() > 80, "the cache copied the runway's 300 km/h onto the platform, got " + platform.getSpeedLimitKilometersPerHour());
		for (final PathData segment : main) {
			if (!segment.getRail().isPlatform() && !segment.getRail().isSiding()) {
				assertEquals(0, AirplanePath.savedRailSpeed(segment));
			}
		}

		AirplanePath.restoreSavedRailSpeeds(main);
		assertEquals(80, platform.getSpeedLimitKilometersPerHour());
		boolean keptCruise = false;
		for (final PathData segment : main) {
			if (AirplanePath.savedRailSpeed(segment) == 0 && segment.getRailLength() > 50) {
				assertEquals(300, segment.getSpeedLimitKilometersPerHour(), "runway and flight rails stay at cruise");
				keptCruise = true;
			}
		}
		assertTrue(keptCruise);

		final ObjectArrayList<PathData> path = dispatched(main);
		final PathData sentinel = path.get(path.size() - 1);
		assertTrue(sentinel.getEndDistance() > Double.MAX_VALUE / 2);
		final Vector origin = sentinel.getPosition(0);
		assertEquals(0.5, origin.x, 0.01);
		assertEquals(0, origin.y, 0.01);
		assertEquals(0.5, origin.z, 0.01, "stopping on the repeat sentinel is drawn at the world origin");

		final PathData arrival = path.get(path.size() - 2);
		final Vector arrivalEnd = arrival.getPosition(arrival.getEndDistance() - arrival.getStartDistance());
		final int index = Utilities.getIndexFromConditionalList(path, arrival.getEndDistance());
		assertEquals(path.size() - 1, index, "the stop at the platform end selects the sentinel, not the platform");
		final Vector parked = AirplanePath.sampledPosition(path, index, 0);
		assertEquals(arrivalEnd.x, parked.x, 1.5);
		assertEquals(arrivalEnd.y, parked.y, 1.5);
		assertEquals(arrivalEnd.z, parked.z, 1.5);

		AirplanePath.anchorRepeatSentinel(path);
		final Vector anchored = path.get(path.size() - 1).getPosition(0);
		assertEquals(arrivalEnd.x, anchored.x, 1.5);
		assertEquals(arrivalEnd.y, anchored.y, 1.5);
		assertEquals(arrivalEnd.z, anchored.z, 1.5);
		assertTrue(path.get(path.size() - 1).getSpeedLimitKilometersPerHour() > 1, "the sentinel must not become a 1 km/h rail");

		// The client rebuilds every segment against an empty world. The anchored point has to survive that.
		PathData.writePathCache(path, new ClientData(), TransportMode.AIRPLANE);
		final Vector afterCache = path.get(path.size() - 1).getPosition(0);
		assertFalse(Double.isNaN(afterCache.x) || Double.isNaN(afterCache.y) || Double.isNaN(afterCache.z));
		assertEquals(arrivalEnd.x, afterCache.x, 2);
		assertEquals(arrivalEnd.y, afterCache.y, 2);
		assertEquals(arrivalEnd.z, afterCache.z, 2);
		assertEquals(80, platform.getSpeedLimitKilometersPerHour(), "the client rebuild keeps the platform limit");
	}

	@Test
	public void descentFollowsTheGlideslopeAndTheJarFlattensIt() throws Exception {
		final Data data = airport();
		final ObjectArrayList<PathData> main = fly(data);
		SidingPathFinder.generatePathDataDistances(main, 0);
		PathData.writePathCache(main, data, TransportMode.AIRPLANE);

		PathData descent = null;
		for (final PathData segment : main) {
			if (segment.isDescending() && (descent == null || drop(segment) > drop(descent))) {
				descent = segment;
			}
		}
		assertNotNull(descent, "the flight has a descent onto the runway");
		assertTrue(drop(descent) > 50, "descent drops " + drop(descent));

		Vector previous = descent.getPosition(0);
		double previousY = previous.y;
		final Vector chordStart = previous;
		final Vector chordEnd = descent.getPosition(descent.getRailLength());
		for (double distance = 4; distance < descent.getRailLength(); distance += 4) {
			final Vector point = descent.getPosition(distance);
			assertFalse(Double.isNaN(point.x) || Double.isNaN(point.y) || Double.isNaN(point.z));
			assertTrue(point.y <= previousY + 0.05, "descent climbs at " + distance + ": " + previousY + " -> " + point.y);
			final double jump = Math.hypot(Math.hypot(point.x - previous.x, point.y - previous.y), point.z - previous.z);
			assertTrue(jump < 8, "position jumps " + jump + " over 4 blocks of path");
			assertTrue(lateral(chordStart, chordEnd, point) < 2, "descent leaves the centerline by " + lateral(chordStart, chordEnd, point));
			previous = point;
			previousY = point.y;
		}

		final Vehicle vehicle = new Vehicle(dispatchedVehicle(main), null, TransportMode.AIRPLANE, data);
		final Method getPosition = Vehicle.class.getDeclaredMethod("getPosition", double.class, DoubleArrayList.class);
		getPosition.setAccessible(true);
		final double along = steepest(descent);
		final DoubleArrayList overrideY = new DoubleArrayList();
		final Vector nose = (Vector) getPosition.invoke(vehicle, descent.getStartDistance() + along, overrideY);
		final Vector tail = (Vector) getPosition.invoke(vehicle, descent.getStartDistance() + along + 12, overrideY);
		final Vector realTail = descent.getPosition(along + 12);
		assertEquals(nose.y, tail.y, 0.01, "the jar pins the tail to the nose height");
		assertTrue(Math.abs(realTail.y - nose.y) > 2, "the glideslope itself drops " + Math.abs(realTail.y - nose.y) + " over one car");
		final int index = Utilities.getIndexFromConditionalList(vehicle.vehicleExtraData.immutablePath, descent.getStartDistance() + along + 12);
		final Vector corrected = AirplanePath.sampledPosition(vehicle.vehicleExtraData.immutablePath, index, along + 12);
		assertEquals(realTail.y, corrected.y, 0.05, "the correction stays on the glideslope");
		assertEquals(realTail.x, corrected.x, 0.05);
		assertEquals(realTail.z, corrected.z, 0.05);
	}

	@Test
	public void bodyPitchesUpButNotDown() {
		assertFalse(AirplanePath.allowBodyPitch(TransportMode.AIRPLANE, 70, 80));
		assertTrue(AirplanePath.allowBodyPitch(TransportMode.AIRPLANE, 80, 70));
		assertFalse(AirplanePath.allowBodyPitch(TransportMode.AIRPLANE, 70, 70));
		assertTrue(AirplanePath.allowBodyPitch(TransportMode.TRAIN, 70, 80));
		assertTrue(AirplanePath.allowBodyPitch(TransportMode.TRAIN, 80, 70));
		assertTrue(AirplanePath.allowBodyPitch(TransportMode.BOAT, 70, 70));

		final Rail siding = Rail.newSidingRail(new Position(0, 64, 0), Angle.E, new Position(20, 64, 0), Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.AIRPLANE);
		assertEquals(40, AirplanePath.savedRailSpeed(new PathData(siding, 0, 0, 0, new Position(0, 64, 0), new Position(20, 64, 0))));
		final Rail runway = Rail.newRail(new Position(0, 64, 0), Angle.E, new Position(100, 64, 0), Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), 0, 300, false, false, true, true, false, TransportMode.AIRPLANE);
		assertEquals(0, AirplanePath.savedRailSpeed(new PathData(runway, 0, 0, 0, new Position(0, 64, 0), new Position(100, 64, 0))));
	}

	@Test
	public void correctionsAreAppliedOnBothSides() throws Exception {
		final String json = Files.readString(Path.of("src/main/resources/atr.mixins.json"));
		final String common = json.substring(json.indexOf("\"mixins\""), json.indexOf("\"client\""));
		assertTrue(common.contains("AirplanePathMixin"));
		assertTrue(common.contains("AirplanePositionMixin"));
		assertTrue(common.contains("AirplaneRepeatMixin"));
	}

	/**
	 * Home platform and runway on x, facing east, and a second airport to the east. The runway is fast
	 * toward the far end and stopped at the platform end, which is how a one-way runway is stored:
	 * the dead end you leave is outbound and the dead end you arrive at is inbound.
	 */
	private static Data airport() {
		final Data data = new Data() {
		};
		data.rails.add(Rail.newPlatformRail(new Position(-60, 80, 0), Angle.E, new Position(0, 80, 0), Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.AIRPLANE));
		data.rails.add(Rail.newRail(new Position(0, 80, 0), Angle.E, new Position(100, 80, 0), Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), 300, 0, false, false, true, true, false, TransportMode.AIRPLANE));
		data.rails.add(Rail.newRail(new Position(800, 70, 200), Angle.E, new Position(900, 70, 200), Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), 300, 0, false, false, true, true, false, TransportMode.AIRPLANE));
		data.rails.add(Rail.newPlatformRail(new Position(900, 70, 200), Angle.E, new Position(960, 70, 200), Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.AIRPLANE));
		final Platform home = new Platform(new Position(-60, 80, 0), new Position(0, 80, 0), TransportMode.AIRPLANE, data);
		final Platform far = new Platform(new Position(900, 70, 200), new Position(960, 70, 200), TransportMode.AIRPLANE, data);
		home.setDwellTime(8000);
		far.setDwellTime(8000);
		data.platforms.add(home);
		data.platforms.add(far);
		data.sync();
		return data;
	}

	private static ObjectArrayList<PathData> fly(Data data) {
		final Platform home = data.platforms.stream().filter(platform -> platform.getMidPosition().getX() < 0).findFirst().orElseThrow();
		final Platform far = data.platforms.stream().filter(platform -> platform.getMidPosition().getX() > 0).findFirst().orElseThrow();
		final ObjectArrayList<PathData> path = new ObjectArrayList<>();
		final ObjectArrayList<SidingPathFinder<org.mtr.core.data.Station, Platform, org.mtr.core.data.Station, Platform>> finders = new ObjectArrayList<>();
		finders.add(new SidingPathFinder<>(data, home, far, 0));
		final boolean[] failed = {false};
		SidingPathFinder.findPathTick(path, finders, 256, () -> {
		}, (start, end) -> failed[0] = true);
		assertFalse(failed[0], "pathfinder failed with " + path.size() + " segments and " + data.runwaysInbound.size() + " inbound runways");
		assertTrue(path.size() >= 2, "path has " + path.size() + " segments");
		return path;
	}

	private static ObjectArrayList<PathData> dispatched(ObjectArrayList<PathData> main) throws Exception {
		final ObjectArrayList<PathData> siding = new ObjectArrayList<>();
		siding.add(new PathData(main.get(0), 0, 40));
		final Method createPathData = VehicleExtraData.class.getDeclaredMethod("createPathData", ObjectArrayList.class, ObjectArrayList.class, ObjectArrayList.class, boolean.class, PathData.class);
		createPathData.setAccessible(true);
		@SuppressWarnings("unchecked")
		final ObjectArrayList<PathData> path = (ObjectArrayList<PathData>) createPathData.invoke(null, siding, main, new ObjectArrayList<PathData>(), true, main.get(0));
		assertTrue(path.size() > main.size());
		return path;
	}

	private static VehicleExtraData dispatchedVehicle(ObjectArrayList<PathData> main) throws Exception {
		final ObjectArrayList<PathData> siding = new ObjectArrayList<>();
		siding.add(new PathData(main.get(0), 0, main.get(0).getStartDistance()));
		final ObjectArrayList<VehicleCar> cars = new ObjectArrayList<>();
		cars.add(new VehicleCar("airplane_30", 30, 3, -12, 12, 0, 0));
		return VehicleExtraData.create(1, 1, 40, cars, siding, main, new ObjectArrayList<>(), main.get(0), true, 0.000004, 0.000004, false, 0.02, 0);
	}

	private static double drop(PathData segment) {
		return segment.getPosition(0).y - segment.getPosition(segment.getRailLength()).y;
	}

	/** Distance of {@code point} from the line through {@code start} and {@code end}, in the horizontal plane. */
	private static double lateral(Vector start, Vector end, Vector point) {
		final double dx = end.x - start.x;
		final double dz = end.z - start.z;
		final double length = Math.hypot(dx, dz);
		if (length < 1) {
			return Math.hypot(point.x - start.x, point.z - start.z);
		}
		return Math.abs((point.x - start.x) * dz - (point.z - start.z) * dx) / length;
	}

	/** A distance along the descent where the next car-length falls the most. */
	private static double steepest(PathData descent) {
		double best = 0;
		double bestDrop = 0;
		final double length = descent.getRailLength();
		for (double distance = 0; distance + 12 < length; distance += 4) {
			final double fallen = descent.getPosition(distance).y - descent.getPosition(distance + 12).y;
			if (fallen > bestDrop) {
				bestDrop = fallen;
				best = distance;
			}
		}
		assertTrue(bestDrop > 2, "no part of the descent drops more than 2 blocks over a car");
		return best;
	}
}
