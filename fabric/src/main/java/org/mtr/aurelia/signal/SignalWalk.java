package org.mtr.aurelia.signal;

import java.util.List;

/**
 * Counts the signal blocks a signal can see. Each {@link Step} is one rail: its {@code signature} identifies the block
 * (MTR stores this as the rail's set of signal colours) and {@code blocked} says whether the block is occupied.
 *
 * <p>A block only counts as clear when every rail in it was walked. That is deliberate: a train further inside the
 * same block still turns the signal red, which the old MTR "seconds since last occupied" rule could not do.</p>
 */
public final class SignalWalk {

	public static final int LOOKAHEAD_BLOCKS = 3;
	public static final double NO_RESTRICTION = Double.POSITIVE_INFINITY;

	private SignalWalk() {
	}

	/** One rail on the route. {@code signature} is 0 when the rail has no signal colours (unsignalled track). */
	public record Step(int signature, boolean blocked, double metres) {
	}

	public record Result(int clearBlocks, double metresToRestriction, boolean routeEnded) {
	}

	/**
	 * @param steps      the rails ahead in travel order, at most a few hundred
	 * @param routeEnded whether the list ends because the route ends, rather than because the caller hit a rail cap
	 */
	public static Result walk(List<Step> steps, boolean routeEnded) {
		int clearBlocks = 0;
		int signature = 0;
		boolean blockClear = true;
		boolean blockStarted = false;
		double metres = 0;

		for (final Step step : steps) {
			if (step.signature() != 0 && step.signature() != signature) {
				if (blockStarted && blockClear) {
					clearBlocks++;
					if (clearBlocks >= LOOKAHEAD_BLOCKS) {
						return new Result(clearBlocks, NO_RESTRICTION, false);
					}
				}
				signature = step.signature();
				blockStarted = true;
				blockClear = true;
			} else if (!blockStarted && step.signature() != 0) {
				signature = step.signature();
				blockStarted = true;
			}

			if (step.blocked()) {
				return new Result(clearBlocks, metres, false);
			}
			metres += step.metres();
		}

		if (routeEnded && blockStarted && blockClear) {
			clearBlocks++;
		}
		if (!blockStarted) {
			// No signal colours ahead: unsignalled track. MTR's signals show proceed there, and a head facing a dead
			// end with no blocks is a route problem rather than an occupied one.
			return new Result(LOOKAHEAD_BLOCKS, NO_RESTRICTION, routeEnded);
		}
		return new Result(clearBlocks, NO_RESTRICTION, routeEnded);
	}

	public static SignalAspect aspect(int aspects, boolean routeSet, Result result) {
		return SignalAspect.forBlocks(aspects, routeSet, result.clearBlocks());
	}
}
