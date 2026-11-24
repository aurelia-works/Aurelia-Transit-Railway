package org.mtr.mod.cargo;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * One read of the simulation. {@code reliable} is false when the private vehicle list could not be read, so a caller
 * must not treat a missing id as a deleted train.
 */
public final class TrainSnapshot {

	private final List<StoppedTrain> stopped;
	private final List<List<double[]>> trains;
	private final Set<Long> allIds;
	private final boolean reliable;
	private final long millis;

	public TrainSnapshot(List<StoppedTrain> stopped, Set<Long> allIds, boolean reliable, long millis) {
		this(stopped, List.of(), allIds, reliable, millis);
	}

	/**
	 * @param trains every train, moving or not, as its points ({@code x, y, z}): the head first when it is known, then the
	 *               bogies, for level crossings
	 */
	public TrainSnapshot(List<StoppedTrain> stopped, List<List<double[]>> trains, Set<Long> allIds, boolean reliable, long millis) {
		this.stopped = stopped == null ? List.of() : List.copyOf(stopped);
		this.trains = trains == null ? List.of() : List.copyOf(trains);
		this.allIds = allIds == null ? Set.of() : Set.copyOf(allIds);
		this.reliable = reliable;
		this.millis = millis;
	}

	public static TrainSnapshot unreliable() {
		return new TrainSnapshot(List.of(), Set.of(), false, System.currentTimeMillis());
	}

	public List<StoppedTrain> stopped() {
		return Collections.unmodifiableList(stopped);
	}

	/** Where every train is: each one's points, head first, {@code x, y, z}. Do not change the arrays. */
	public List<List<double[]>> trains() {
		return trains;
	}

	public Set<Long> allIds() {
		return Collections.unmodifiableSet(allIds);
	}

	public boolean reliable() {
		return reliable;
	}

	public long millis() {
		return millis;
	}
}
