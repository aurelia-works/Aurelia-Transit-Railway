package org.mtr.aurelia.crew;

import org.mtr.aurelia.signal.SignalAspect;

import java.util.ArrayList;
import java.util.List;

/**
 * The aspects of the signals ahead of a train once a signaller holds some at danger: a held signal shows red, the signal
 * before it no better than a single yellow (on a three- or four-aspect head), and the one before that no better than a
 * double yellow (four-aspect). Two-aspect heads have no caution aspect and are left as they are. Pure logic.
 */
public final class HoldSequence {

	private HoldSequence() {
	}

	/**
	 * @param aspects the aspects in order from the train, as the spacing gives them
	 * @param counts  how many aspects each head has (2, 3 or 4)
	 * @param held    which of them a signaller holds
	 */
	public static List<SignalAspect> apply(List<SignalAspect> aspects, int[] counts, boolean[] held) {
		final List<SignalAspect> result = new ArrayList<>(aspects);
		for (int i = 0; i < result.size(); i++) {
			if (held[i]) {
				result.set(i, SignalAspect.RED);
			}
		}
		for (int i = result.size() - 1; i >= 0; i--) {
			if (!held[i]) {
				continue;
			}
			if (i >= 1 && counts[i - 1] >= 3) {
				result.set(i - 1, worst(result.get(i - 1), SignalAspect.YELLOW));
			}
			if (i >= 2 && counts[i - 2] >= 4 && result.get(i - 1) == SignalAspect.YELLOW) {
				result.set(i - 2, worst(result.get(i - 2), SignalAspect.DOUBLE_YELLOW));
			}
		}
		return result;
	}

	/** The more restrictive of two aspects (the enum runs from red to green). */
	private static SignalAspect worst(SignalAspect a, SignalAspect b) {
		return a.ordinal() <= b.ordinal() ? a : b;
	}
}
