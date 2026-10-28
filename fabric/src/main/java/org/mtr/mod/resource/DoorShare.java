package org.mtr.mod.resource;

import org.mtr.mapping.render.object.VertexArray;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;

/**
 * MTR builds each train car twice, once with the doors open and once with them closed, and uploads every part that is
 * not a door into both. The two lists of each part condition are linked when the car is built: the parts both share
 * are uploaded once and both models draw the same buffers; only the closed doors are extra.
 * <p>
 * The shared buffers are held weakly: while either model draws them they stay, and once both are dropped they go like
 * any other unused model. Render thread only. Ported from Aurelia Transit Utilities 1.4.0 (DoorShare).
 */
public final class DoorShare<T> {

	/** Marks a part in more than one list of one build; such parts are never shared. */
	private static final DoorShare<?> CONFLICT = new DoorShare<>(Collections.emptySet());

	private final Set<T> common;
	/** The common parts in the order the first upload listed them, so both door states build the same list. */
	@Nullable
	private List<T> commonOrder;
	@Nullable
	private List<WeakReference<VertexArray>> uploaded;

	private DoorShare(Set<T> common) {
		this.common = common;
	}

	/**
	 * One build's lists per part condition, doors open and doors closed. Returns the share of every part that both
	 * lists of its condition hold (the open list must be inside the closed one); parts in more than one condition are
	 * left out.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Map<T, DoorShare<T>> link(Map<?, ? extends Collection<T>> open, Map<?, ? extends Collection<T>> closed) {
		final Map<T, DoorShare<T>> assigned = new IdentityHashMap<>();
		open.forEach((condition, openList) -> {
			final Collection<T> closedList = closed.get(condition);
			if (openList == null || closedList == null) {
				return;
			}
			final Set<T> openSet = identitySet(openList);
			if (openSet.isEmpty() || !identitySet(closedList).containsAll(openSet)) {
				return;
			}
			final DoorShare<T> share = new DoorShare<>(openSet);
			for (final T part : openSet) {
				assigned.merge(part, share, (a, b) -> (DoorShare<T>) CONFLICT);
			}
		});
		assigned.values().removeIf(share -> share == CONFLICT);
		return assigned;
	}

	/** Splits one upload: each part once, in order; with a share, the parts both door states use and the rest. */
	public static <T> Split<T> split(Collection<T> parts, Function<T, DoorShare<T>> shareOf) {
		final Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		final List<T> unique = new ArrayList<>(parts.size());
		for (final T part : parts) {
			if (seen.add(part)) {
				unique.add(part);
			}
		}

		DoorShare<T> found = null;
		for (final T part : unique) {
			final DoorShare<T> share = shareOf.apply(part);
			if (share == null) {
				continue;
			}
			if (found != null && found != share) {
				return new Split<>(unique, null, List.of(), List.of());
			}
			found = share;
		}
		if (found == null || !seen.containsAll(found.common)) {
			return new Split<>(unique, null, List.of(), List.of());
		}

		final List<T> common = new ArrayList<>(unique.size());
		final List<T> rest = new ArrayList<>();
		for (final T part : unique) {
			(found.common.contains(part) ? common : rest).add(part);
		}
		// Exactly the share's parts (checked above), so the first order fits every later upload
		if (found.commonOrder == null) {
			found.commonOrder = common;
		}
		return new Split<>(unique, found, found.commonOrder, rest);
	}

	/** The common parts' buffers if an earlier upload made them and they are still in use, else null. */
	@Nullable
	public List<VertexArray> uploaded() {
		if (uploaded == null) {
			return null;
		}
		final List<VertexArray> vertexArrays = new ArrayList<>(uploaded.size());
		for (final WeakReference<VertexArray> reference : uploaded) {
			final VertexArray vertexArray = reference.get();
			if (vertexArray == null) {
				return null;
			}
			vertexArrays.add(vertexArray);
		}
		return vertexArrays;
	}

	public void store(List<VertexArray> vertexArrays) {
		final List<WeakReference<VertexArray>> references = new ArrayList<>(vertexArrays.size());
		for (final VertexArray vertexArray : vertexArrays) {
			references.add(new WeakReference<>(vertexArray));
		}
		uploaded = references;
	}

	private static <T> Set<T> identitySet(Collection<T> parts) {
		final Set<T> set = Collections.newSetFromMap(new IdentityHashMap<>());
		for (final T part : parts) {
			if (part != null) {
				set.add(part);
			}
		}
		return set;
	}

	public static final class Split<T> {

		public final List<T> unique;
		/** Null when the upload is not one of a linked pair. */
		@Nullable
		public final DoorShare<T> share;
		public final List<T> common;
		public final List<T> rest;

		private Split(List<T> unique, @Nullable DoorShare<T> share, List<T> common, List<T> rest) {
			this.unique = unique;
			this.share = share;
			this.common = common;
			this.rest = rest;
		}
	}
}
