package org.mtr.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A trailer camera path through keyframes. Positions ride a uniform Catmull-Rom spline, so the
 * sampled path passes through every keyframe with a continuous velocity (no jumps at keyframes);
 * yaw takes the short way around (359 to 1 passes through 0, never 180), pitch and FOV lerp.
 * Playback moves at a constant speed in blocks per second via arc-length lookup.
 * Pure Java (no Minecraft types) so plain unit tests cover it.
 */
public final class CameraPath {

	private static final int SAMPLES_PER_SEGMENT = 64;

	private final List<CameraKeyframe> keyframes;
	private double[] cumulative = new double[0];
	private double totalLength = -1;

	public CameraPath(List<CameraKeyframe> keyframes) {
		Objects.requireNonNull(keyframes, "keyframes");
		// Unwrap yaw so consecutive keyframes never jump across 0/360: playback sets the value
		// straight onto the player, where 359 -> 361 stays smooth but 359 -> 1 would spin.
		final List<CameraKeyframe> unwrapped = new ArrayList<>(keyframes.size());
		double previousRaw = 0;
		double previousUnwrapped = 0;
		boolean first = true;
		for (final CameraKeyframe keyframe : keyframes) {
			Objects.requireNonNull(keyframe, "keyframe");
			if (first) {
				previousRaw = keyframe.yaw();
				previousUnwrapped = keyframe.yaw();
				first = false;
			} else {
				previousUnwrapped += shortestDelta(previousRaw, keyframe.yaw());
				previousRaw = keyframe.yaw();
			}
			unwrapped.add(new CameraKeyframe(keyframe.x(), keyframe.y(), keyframe.z(), previousUnwrapped, keyframe.pitch(), keyframe.fov()));
		}
		this.keyframes = List.copyOf(unwrapped);
	}

	public List<CameraKeyframe> keyframes() {
		return keyframes;
	}

	public int size() {
		return keyframes.size();
	}

	public boolean isEmpty() {
		return keyframes.isEmpty();
	}

	/**
	 * Samples the path at global {@code t} in [0, size - 1]. {@code sample(i)} returns keyframe
	 * {@code i} (yaw equivalent mod 360); values outside clamp to the ends.
	 */
	public CameraKeyframe sample(double t) {
		if (keyframes.isEmpty()) {
			throw new IllegalStateException("Camera path has no keyframes");
		}
		if (keyframes.size() == 1) {
			return keyframes.get(0);
		}
		final double clamped = Math.max(0, Math.min(keyframes.size() - 1, t));
		final int segment = Math.min((int) Math.floor(clamped), keyframes.size() - 2);
		final double u = clamped - segment;
		final CameraKeyframe p0 = keyframes.get(Math.max(0, segment - 1));
		final CameraKeyframe p1 = keyframes.get(segment);
		final CameraKeyframe p2 = keyframes.get(segment + 1);
		final CameraKeyframe p3 = keyframes.get(Math.min(keyframes.size() - 1, segment + 2));
		return new CameraKeyframe(
				catmullRom(p0.x(), p1.x(), p2.x(), p3.x(), u),
				catmullRom(p0.y(), p1.y(), p2.y(), p3.y(), u),
				catmullRom(p0.z(), p1.z(), p2.z(), p3.z(), u),
				p1.yaw() + (p2.yaw() - p1.yaw()) * u,
				p1.pitch() + (p2.pitch() - p1.pitch()) * u,
				p1.fov() + (p2.fov() - p1.fov()) * u
		);
	}

	/** Approximate arc length in blocks, for constant-speed playback. */
	public double length() {
		buildTable();
		return totalLength;
	}

	/**
	 * Samples the path {@code distance} blocks along it from the start. Clamps to the ends,
	 * so playback at a set blocks-per-second speed just advances the distance each tick.
	 */
	public CameraKeyframe sampleAtDistance(double distance) {
		if (keyframes.isEmpty()) {
			throw new IllegalStateException("Camera path has no keyframes");
		}
		if (keyframes.size() == 1) {
			return keyframes.get(0);
		}
		buildTable();
		if (distance <= 0 || totalLength <= 0) {
			return sample(0);
		}
		if (distance >= totalLength) {
			return sample(keyframes.size() - 1);
		}
		int low = 0;
		int high = cumulative.length - 1;
		while (low < high) {
			final int mid = (low + high) / 2;
			if (cumulative[mid] < distance) {
				low = mid + 1;
			} else {
				high = mid;
			}
		}
		final int index = Math.max(1, low);
		final double before = cumulative[index - 1];
		final double after = cumulative[index];
		final double span = after - before;
		final double local = span <= 0 ? 0 : (distance - before) / span;
		final double t = ((index - 1) + local) / SAMPLES_PER_SEGMENT;
		return sample(t * (keyframes.size() - 1));
	}

	/** Signed shortest turn from {@code from} to {@code to} in degrees, in [-180, 180). */
	public static double shortestDelta(double from, double to) {
		return ((to - from + 180) % 360 + 360) % 360 - 180;
	}

	/** Normalises any angle to [0, 360). */
	public static double normalizeAngle(double angle) {
		return ((angle % 360) + 360) % 360;
	}

	private static double catmullRom(double p0, double p1, double p2, double p3, double u) {
		final double u2 = u * u;
		final double u3 = u2 * u;
		return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
	}

	private void buildTable() {
		if (totalLength >= 0) {
			return;
		}
		if (keyframes.size() < 2) {
			cumulative = new double[]{0};
			totalLength = 0;
			return;
		}
		final int steps = SAMPLES_PER_SEGMENT * (keyframes.size() - 1);
		cumulative = new double[steps + 1];
		double total = 0;
		double x = keyframes.get(0).x();
		double y = keyframes.get(0).y();
		double z = keyframes.get(0).z();
		for (int i = 1; i <= steps; i++) {
			final CameraKeyframe at = sample((double) i / SAMPLES_PER_SEGMENT);
			final double dx = at.x() - x;
			final double dy = at.y() - y;
			final double dz = at.z() - z;
			total += Math.sqrt(dx * dx + dy * dy + dz * dz);
			cumulative[i] = total;
			x = at.x();
			y = at.y();
			z = at.z();
		}
		totalLength = total;
	}
}
