package org.mtr.mod.coupling;

/**
 * Whether a stopped train may split or couple. The siding's saved car list stays the fixed-length train: a consist only
 * changes while that vehicle is out, and a siding with no coupler order never changes length.
 */
public final class Coupling {

	public enum Mode {
		NONE,
		SPLIT,
		COUPLE
	}

	private Coupling() {
	}

	/**
	 * How many cars stay on the front. {@code requested <= 0} keeps half. The result is always at least one and always
	 * leaves at least one car behind when the train has two or more cars.
	 */
	public static int keepCount(int cars, int requested) {
		if (cars < 2) {
			return cars;
		}
		int keep = requested <= 0 ? cars / 2 : requested;
		if (keep < 1) {
			keep = 1;
		}
		if (keep >= cars) {
			keep = cars - 1;
		}
		return keep;
	}

	public static boolean canSplit(int cars, int keep) {
		return cars >= 2 && keep >= 1 && keep < cars;
	}

	public static boolean canCouple(int leadCars, int trailingCars) {
		return leadCars >= 1 && trailingCars >= 1;
	}
}
