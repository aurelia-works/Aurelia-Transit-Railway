package org.mtr.mod.cargo;

/**
 * One stack of cargo on a train. Identity is the item id plus its NBT, so a named stack does not merge into a plain one.
 */
public final class CargoStack {

	private final String itemId;
	private final String nbt;
	private final int maxCount;
	private int count;

	public CargoStack(String itemId, String nbt, int count, int maxCount) {
		this.itemId = itemId == null ? "" : itemId;
		this.nbt = nbt == null ? "" : nbt;
		this.maxCount = maxCount <= 0 ? TrainCargo.DEFAULT_MAX_STACK : maxCount;
		this.count = Math.max(0, Math.min(count, this.maxCount));
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

	public boolean matches(String itemId, String nbt) {
		return this.itemId.equals(itemId == null ? "" : itemId) && this.nbt.equals(nbt == null ? "" : nbt);
	}

	public CargoStack copy() {
		return new CargoStack(itemId, nbt, count, maxCount);
	}

	void setCount(int count) {
		this.count = count;
	}
}
