package org.mtr.mod.cargo;

import java.util.List;

/**
 * Moves items between container slots and a {@link TrainCargo}. Slots are plain piles so the rules can be tested
 * without a world. A loader walks the piles in order and stops when the hold is full. An unloader fills the first
 * pile that can take the front stack.
 */
public final class CargoExchange {

	private CargoExchange() {
	}

	public static final class Pile {

		private String itemId;
		private String nbt;
		private int count;
		private int maxCount;

		public Pile(String itemId, String nbt, int count, int maxCount) {
			this.itemId = itemId == null ? "" : itemId;
			this.nbt = nbt == null ? "" : nbt;
			this.count = Math.max(0, count);
			this.maxCount = maxCount <= 0 ? TrainCargo.DEFAULT_MAX_STACK : maxCount;
		}

		public static Pile empty(int maxCount) {
			return new Pile("", "", 0, maxCount);
		}

		public boolean isEmpty() {
			return count <= 0 || itemId.isEmpty();
		}

		public String itemId() {
			return itemId;
		}

		public String nbt() {
			return nbt;
		}

		public int count() {
			return count;
		}

		public int maxCount() {
			return maxCount;
		}
	}

	/**
	 * @return items moved into the hold
	 */
	public static int load(List<Pile> sources, TrainCargo hold, int limit) {
		if (limit <= 0) {
			return 0;
		}
		int moved = 0;
		for (final Pile source : sources) {
			if (moved >= limit) {
				break;
			}
			if (source.isEmpty()) {
				continue;
			}
			final int want = Math.min(limit - moved, source.count);
			final int inserted = hold.insert(source.itemId, source.nbt, source.maxCount, want);
			if (inserted <= 0) {
				continue;
			}
			source.count -= inserted;
			moved += inserted;
			if (source.count <= 0) {
				source.itemId = "";
				source.nbt = "";
				source.count = 0;
			}
		}
		return moved;
	}

	/**
	 * @return items moved out of the hold
	 */
	public static int unload(TrainCargo hold, List<Pile> destinations, int limit) {
		if (limit <= 0) {
			return 0;
		}
		int moved = 0;
		while (moved < limit) {
			final CargoStack stack = hold.peek();
			if (stack == null) {
				break;
			}
			Pile match = null;
			Pile empty = null;
			int room = 0;
			for (final Pile pile : destinations) {
				if (!pile.isEmpty() && pile.itemId.equals(stack.itemId()) && pile.nbt.equals(stack.nbt()) && pile.count < pile.maxCount) {
					match = pile;
					room = pile.maxCount - pile.count;
					break;
				}
				if (empty == null && pile.isEmpty()) {
					empty = pile;
				}
			}
			final Pile destination = match != null ? match : empty;
			if (destination == null) {
				break;
			}
			if (match == null) {
				room = Math.min(empty.maxCount, stack.maxCount());
			}
			if (room <= 0) {
				break;
			}
			final int want = Math.min(limit - moved, Math.min(room, stack.count()));
			final int extracted = hold.extract(stack.itemId(), stack.nbt(), want);
			if (extracted <= 0) {
				break;
			}
			if (destination.isEmpty()) {
				destination.itemId = stack.itemId();
				destination.nbt = stack.nbt();
				destination.maxCount = Math.min(destination.maxCount, stack.maxCount());
				destination.count = extracted;
			} else {
				destination.count += extracted;
			}
			moved += extracted;
		}
		return moved;
	}
}
