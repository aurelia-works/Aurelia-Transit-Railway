package org.mtr.mod.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Detail level of one train car by its distance from the camera. The car you ride is always drawn in full.
 * A car drawn as a box never asks for its model, so its model is not loaded until it comes closer.
 * Ported from Aurelia Transit Utilities 1.4.0 (TrainLod, TrainLodClient).
 */
public final class TrainLod {

	public enum Tier {FULL, NO_INTERIOR, BOX, BOX_FAR, HIDDEN}

	/** Set while one car's batched model is queued without its interior parts (render thread only). */
	private static boolean skipInterior;

	private TrainLod() {
	}

	public static Tier tier(double distanceBlocks, boolean riding, int interiorChunks, int boxChunks) {
		if (riding) {
			return Tier.FULL;
		}
		if (boxChunks > 0 && distanceBlocks > boxChunks * 16.0) {
			return Tier.BOX;
		}
		if (interiorChunks > 0 && distanceBlocks > interiorChunks * 16.0) {
			return Tier.NO_INTERIOR;
		}
		return Tier.FULL;
	}

	/** Runs {@code queue} with interior parts left out when {@code withoutInterior} is set. */
	public static void queue(boolean withoutInterior, Runnable queue) {
		skipInterior = withoutInterior;
		try {
			queue.run();
		} finally {
			skipInterior = false;
		}
	}

	public static boolean skipInterior() {
		return skipInterior;
	}

	/** The parts that are not interior parts, in their order. */
	public static <T> List<T> withoutInterior(List<T> parts, Predicate<T> isInterior) {
		final List<T> exterior = new ArrayList<>(parts.size());
		for (final T part : parts) {
			if (!isInterior.test(part)) {
				exterior.add(part);
			}
		}
		return exterior;
	}

	/**
	 * The tier past Minecraft's render distance. A car there is a plain box ({@link Tier#BOX_FAR}) and must be drawn
	 * without waiting for occlusion culling, because the culling distance stops at the render distance. 0 turns the
	 * train view distance off. With far detail off, cars past the view distance are hidden instead.
	 */
	public static Tier viewTier(double distanceBlocks, boolean riding, int trainViewChunks, int minecraftChunks, boolean farDetail) {
		if (riding || trainViewChunks <= 0) {
			return Tier.FULL;
		}
		if (distanceBlocks > trainViewChunks * 16.0) {
			return Tier.HIDDEN;
		}
		if (distanceBlocks > minecraftChunks * 16.0) {
			return farDetail ? Tier.BOX_FAR : Tier.HIDDEN;
		}
		return Tier.FULL;
	}
}
