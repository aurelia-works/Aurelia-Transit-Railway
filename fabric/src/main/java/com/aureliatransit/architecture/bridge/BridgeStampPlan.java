package com.aureliatransit.architecture.bridge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The blocks a bridge template stamps along a rail. The rail is sampled every {@code step} blocks; each sample's tangent
 * picks the nearest quarter turn, and the template is placed with its anchor on the rail. Positions reached twice keep
 * their first placement, so a short template repeated along a curve never writes the same block twice.
 *
 * <p>Quarter turns are deliberate: vanilla block states only rotate in 90-degree steps, so a template cannot be placed at
 * an arbitrary angle without inventing blocks that do not exist. A curved rail therefore steps between quarter turns.
 */
public final class BridgeStampPlan {

	/** A single build larger than this is a mistake; it also keeps previews and undo logs bounded. */
	public static final int MAX_PLACEMENTS = 200_000;

	public record Placement(int x, int y, int z, int palette, int quarterTurns) {
	}

	/** One point on the rail, with its horizontal tangent. */
	public record Sample(double x, double y, double z, double dx, double dz) {
	}

	/** A rail the plan can sample; {@code at} is clamped to 0..length. */
	public interface Path {

		double length();

		Sample at(double distance);
	}

	private final List<Placement> placements;

	private BridgeStampPlan(List<Placement> placements) {
		this.placements = List.copyOf(placements);
	}

	/**
	 * @param step   distance along the rail between stamps, in blocks
	 * @param height vertical offset from the rail's block level
	 */
	public static BridgeStampPlan of(BridgeTemplate template, Path path, double step, int height) {
		if (step <= 0 || !Double.isFinite(step)) {
			throw new IllegalArgumentException("Step must be greater than 0.");
		}
		final double length = Math.max(0, path.length());
		if (length > MAX_PLACEMENTS * step) {
			throw new IllegalArgumentException("This rail is too long for one bridge; use a shorter rail or a bigger step.");
		}
		final int samples = (int) Math.ceil(length / step) + 1;
		final Map<Long, Placement> unique = new LinkedHashMap<>();
		for (int i = 0; i < samples; i++) {
			final double distance = Math.min(length, i * step);
			final Sample sample = path.at(distance);
			final int quarterTurns = quarterTurns(sample.dx(), sample.dz());
			final int anchorX = (int) Math.floor(sample.x());
			final int anchorY = (int) Math.floor(sample.y());
			final int anchorZ = (int) Math.floor(sample.z());
			for (final BridgeTemplate.Placed placed : template.stamp(anchorX, anchorY, anchorZ, quarterTurns, height)) {
				unique.putIfAbsent(key(placed.x(), placed.y(), placed.z()), new Placement(placed.x(), placed.y(), placed.z(), placed.palette(), quarterTurns));
				if (unique.size() > MAX_PLACEMENTS) {
					throw new IllegalArgumentException("This bridge would be larger than " + MAX_PLACEMENTS + " blocks; use a smaller template or a shorter rail.");
				}
			}
			if (distance >= length) {
				break;
			}
		}
		return new BridgeStampPlan(List.copyOf(unique.values()));
	}

	/**
	 * The clockwise quarter turn that best matches the horizontal tangent, with north as 0.
	 */
	public static int quarterTurns(double dx, double dz) {
		if (Math.abs(dx) < 1e-9 && Math.abs(dz) < 1e-9) {
			return 0;
		}
		final double bearing = Math.toDegrees(Math.atan2(dx, -dz));
		return Math.floorMod((int) Math.round(bearing / 90), 4);
	}

	public List<Placement> placements() {
		return placements;
	}

	public int size() {
		return placements.size();
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF;
	}
}
