package com.aureliatransit.architecture.block.elevated;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The passenger each fare gate last opened for. Gates are told apart by the world they stand in as well as their
 * position, so the same x, y, z in two dimensions are two gates. The world is a weak key, so a world that unloads
 * takes its entries with it instead of leaving them for the next save.
 */
public final class FarePayers {

	public static final int MAX_TRACKED_GATES = 512;

	private record Payer(UUID id, long until) {
	}

	private final Map<Object, Map<Long, Payer>> byWorld = new WeakHashMap<>();

	public void remember(Object world, long pos, UUID id, long until) {
		final Map<Long, Payer> gates = byWorld.computeIfAbsent(world, key -> new HashMap<>());
		if (gates.size() >= MAX_TRACKED_GATES) {
			gates.clear();
		}
		gates.put(pos, new Payer(id, until));
	}

	/** The passenger the gate is still waiting on at {@code now}, or null. A payer past the window is dropped. */
	public UUID payerWithin(Object world, long pos, long now) {
		final Map<Long, Payer> gates = byWorld.get(world);
		if (gates == null) {
			return null;
		}
		final Payer payer = gates.get(pos);
		if (payer == null) {
			return null;
		}
		if (now > payer.until()) {
			gates.remove(pos);
			return null;
		}
		return payer.id();
	}

	public void forget(Object world, long pos) {
		final Map<Long, Payer> gates = byWorld.get(world);
		if (gates != null) {
			gates.remove(pos);
		}
	}

	public int tracked(Object world) {
		final Map<Long, Payer> gates = byWorld.get(world);
		return gates == null ? 0 : gates.size();
	}
}
