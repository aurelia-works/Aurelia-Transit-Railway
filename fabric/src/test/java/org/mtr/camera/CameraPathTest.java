package org.mtr.camera;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java cover for the trailer path maths: the sampled path passes through every keyframe,
 * velocity is continuous at keyframes (no jumps), yaw takes the short way around, and a
 * save/load round trip keeps the values.
 */
public final class CameraPathTest {

	private static final List<CameraKeyframe> FOUR_KEYS = List.of(
			new CameraKeyframe(0, 64, 0, 350, -5, 70),
			new CameraKeyframe(10, 68, 6, 10, 0, 60),
			new CameraKeyframe(24, 66, -4, 90, 10, 80),
			new CameraKeyframe(40, 70, 10, 200, -10, 70)
	);

	@Test
	public void pathPassesThroughEveryKeyframe() {
		final CameraPath path = new CameraPath(FOUR_KEYS);
		for (int i = 0; i < FOUR_KEYS.size(); i++) {
			final CameraKeyframe sampled = path.sample(i);
			final CameraKeyframe expected = FOUR_KEYS.get(i);
			assertEquals(expected.x(), sampled.x(), 1.0e-9, "x at keyframe " + i);
			assertEquals(expected.y(), sampled.y(), 1.0e-9, "y at keyframe " + i);
			assertEquals(expected.z(), sampled.z(), 1.0e-9, "z at keyframe " + i);
			assertEquals(CameraPath.normalizeAngle(expected.yaw()), CameraPath.normalizeAngle(sampled.yaw()), 1.0e-9, "yaw at keyframe " + i);
			assertEquals(expected.pitch(), sampled.pitch(), 1.0e-9, "pitch at keyframe " + i);
			assertEquals(expected.fov(), sampled.fov(), 1.0e-9, "fov at keyframe " + i);
		}
	}

	@Test
	public void pathIsSmoothAtKeyframes() {
		// Positions ride Catmull-Rom, which is C1 at interior knots: secant velocities from either
		// side agree up to eps times the local acceleration. A positional jump of even 0.01 blocks
		// would read as 0.01/eps = 10 here, so a 0.5 tolerance catches jumps while true C1 passes.
		// Yaw, pitch and FOV lerp linearly on purpose (constant turn rate between keys, no spline
		// overshoot past the framed direction), so for them the no-jump check is C0: the two sides
		// meet at the keyframe. A wrap-around bug would read as ~360 here.
		final double eps = 1.0e-3;
		final double tolerance = 0.5;
		final CameraPath path = new CameraPath(FOUR_KEYS);
		for (int i = 1; i < FOUR_KEYS.size() - 1; i++) {
			final CameraKeyframe before = path.sample(i - eps);
			final CameraKeyframe at = path.sample(i);
			final CameraKeyframe after = path.sample(i + eps);
			for (final double[] component : new double[][]{
					{before.x(), at.x(), after.x()},
					{before.y(), at.y(), after.y()},
					{before.z(), at.z(), after.z()}
			}) {
				final double velocityIn = (component[1] - component[0]) / eps;
				final double velocityOut = (component[2] - component[1]) / eps;
				assertTrue(Math.abs(velocityIn - velocityOut) <= tolerance, "position jump at keyframe " + i);
			}
			assertTrue(Math.abs(after.yaw() - before.yaw()) <= tolerance, "yaw jump at keyframe " + i);
			assertTrue(Math.abs(after.pitch() - before.pitch()) <= tolerance, "pitch jump at keyframe " + i);
			assertTrue(Math.abs(after.fov() - before.fov()) <= tolerance, "fov jump at keyframe " + i);
		}
	}

	@Test
	public void rotationTakesShortWayAround() {
		final CameraPath path = new CameraPath(List.of(
				new CameraKeyframe(0, 64, 0, 359, 0, 70),
				new CameraKeyframe(10, 64, 0, 1, 0, 70)
		));
		final double middle = CameraPath.normalizeAngle(path.sample(0.5).yaw());
		assertTrue(Math.abs(middle) <= 1.0e-9 || Math.abs(middle - 360) <= 1.0e-9, "midpoint yaw " + middle + " should be ~0, not ~180");
		assertTrue(Math.abs(middle - 180) > 90, "midpoint yaw " + middle + " took the long way around");
		assertEquals(359, CameraPath.normalizeAngle(path.sample(0).yaw()), 1.0e-9);
		assertEquals(1, CameraPath.normalizeAngle(path.sample(1).yaw()), 1.0e-9);
		assertEquals(2, CameraPath.shortestDelta(359, 1), 1.0e-9);
		assertEquals(-2, CameraPath.shortestDelta(1, 359), 1.0e-9);
	}

	@Test
	public void saveLoadRoundTripKeepsValues() {
		final CameraPath path = new CameraPath(FOUR_KEYS);
		final CameraPathJson.LoadResult result = CameraPathJson.read(CameraPathJson.write(path, 12.5, false));
		assertEquals(0, result.skipped());
		assertEquals(FOUR_KEYS.size(), result.loaded());
		assertEquals(12.5, result.file().speed(), 1.0e-9);
		assertEquals(false, result.file().hideHud());
		for (int i = 0; i < FOUR_KEYS.size(); i++) {
			final CameraKeyframe expected = path.keyframes().get(i);
			final CameraKeyframe actual = result.file().path().keyframes().get(i);
			assertEquals(expected.x(), actual.x(), 0, "x " + i);
			assertEquals(expected.y(), actual.y(), 0, "y " + i);
			assertEquals(expected.z(), actual.z(), 0, "z " + i);
			assertEquals(expected.yaw(), actual.yaw(), 0, "yaw " + i);
			assertEquals(expected.pitch(), actual.pitch(), 0, "pitch " + i);
			assertEquals(expected.fov(), actual.fov(), 0, "fov " + i);
		}
	}

	@Test
	public void missingKeysFallBackToDefaults() {
		final CameraPathJson.LoadResult result = CameraPathJson.read("{\"keyframes\":[{\"x\":1,\"y\":2,\"z\":3}]}");
		assertEquals(1, result.loaded());
		assertEquals(0, result.skipped());
		assertEquals(CameraPathJson.DEFAULT_SPEED, result.file().speed(), 0);
		assertEquals(CameraPathJson.DEFAULT_HIDE_HUD, result.file().hideHud());
		final CameraKeyframe keyframe = result.file().path().keyframes().get(0);
		assertEquals(0, keyframe.yaw(), 0);
		assertEquals(0, keyframe.pitch(), 0);
		assertEquals(CameraKeyframe.DEFAULT_FOV, keyframe.fov(), 0);
		assertThrows(IllegalArgumentException.class, () -> CameraPathJson.read("{\"speed\":8}"));
		assertThrows(IllegalArgumentException.class, () -> CameraPathJson.read("not json"));
	}

	@Test
	public void constantSpeedCoversPathLength() {
		final CameraPath path = new CameraPath(List.of(
				new CameraKeyframe(0, 64, 0, 0, 0, 70),
				new CameraKeyframe(30, 64, 0, 0, 0, 70)
		));
		assertEquals(30, path.length(), 1.0e-3);
		final CameraKeyframe middle = path.sampleAtDistance(15);
		assertEquals(15, middle.x(), 1.0e-3);
		assertEquals(64, middle.y(), 1.0e-3);
		assertEquals(0, path.sampleAtDistance(-5).x(), 1.0e-9);
		assertEquals(30, path.sampleAtDistance(999).x(), 1.0e-9);
	}
}
