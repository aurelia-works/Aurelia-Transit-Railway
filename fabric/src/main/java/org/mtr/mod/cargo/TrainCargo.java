package org.mtr.mod.cargo;

import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The items a train is carrying. One slot holds one stack. Capacity grows with the number of cars and never shrinks,
 * so a shorter consist does not throw cargo away.
 *
 * <p>Transport Simulation Core 4.0.5 has no cargo field on {@code Siding} or {@code Vehicle} (the published schema and
 * the bundled jar were both checked). This object is the hold those fields would have been. It is stored beside the
 * simulation save, keyed by the vehicle id the core already writes, so an old world still loads and a vanilla MTR
 * server ignores the extra file.
 */
public final class TrainCargo {

	public static final int SLOTS_PER_CAR = 9;
	public static final int DEFAULT_MAX_STACK = 64;

	private int slots;
	private final List<CargoStack> stacks = new ArrayList<>();

	public TrainCargo(int slots) {
		this.slots = Math.max(1, slots);
	}

	public static int slotsForCars(int cars) {
		final int count = cars <= 0 ? 1 : cars;
		return count * SLOTS_PER_CAR;
	}

	public synchronized int slots() {
		return slots;
	}

	public synchronized void ensureSlots(int slots) {
		if (slots > this.slots) {
			this.slots = slots;
		}
	}

	public synchronized boolean isEmpty() {
		return stacks.isEmpty();
	}

	public synchronized int itemCount() {
		int total = 0;
		for (final CargoStack stack : stacks) {
			total += stack.count();
		}
		return total;
	}

	public synchronized CargoStack peek() {
		return stacks.isEmpty() ? null : stacks.get(0).copy();
	}

	public synchronized List<CargoStack> copyStacks() {
		final List<CargoStack> copy = new ArrayList<>(stacks.size());
		for (final CargoStack stack : stacks) {
			copy.add(stack.copy());
		}
		return copy;
	}

	/**
	 * @return how many items actually fit
	 */
	public synchronized int insert(String itemId, String nbt, int maxCount, int count) {
		if (count <= 0 || itemId == null || itemId.isEmpty()) {
			return 0;
		}
		final String storedNbt = nbt == null ? "" : nbt;
		final int limit = maxCount <= 0 ? DEFAULT_MAX_STACK : maxCount;
		int left = count;
		for (final CargoStack stack : stacks) {
			if (left == 0) {
				break;
			}
			if (stack.matches(itemId, storedNbt) && stack.count() < stack.maxCount()) {
				final int move = Math.min(stack.maxCount() - stack.count(), left);
				stack.setCount(stack.count() + move);
				left -= move;
			}
		}
		while (left > 0 && stacks.size() < slots) {
			final int move = Math.min(limit, left);
			stacks.add(new CargoStack(itemId, storedNbt, move, limit));
			left -= move;
		}
		return count - left;
	}

	/**
	 * @return how many items were taken from the matching stacks, front to back
	 */
	public synchronized int extract(String itemId, String nbt, int count) {
		if (count <= 0) {
			return 0;
		}
		int left = count;
		final Iterator<CargoStack> iterator = stacks.iterator();
		while (iterator.hasNext() && left > 0) {
			final CargoStack stack = iterator.next();
			if (!stack.matches(itemId, nbt)) {
				continue;
			}
			final int move = Math.min(stack.count(), left);
			stack.setCount(stack.count() - move);
			left -= move;
			if (stack.count() <= 0) {
				iterator.remove();
			}
		}
		return count - left;
	}

	public synchronized JsonObject toJson() {
		final JsonObject object = new JsonObject();
		object.addProperty("slots", slots);
		final JsonArray array = new JsonArray();
		for (final CargoStack stack : stacks) {
			final JsonObject stackObject = new JsonObject();
			stackObject.addProperty("item", stack.itemId());
			stackObject.addProperty("count", stack.count());
			stackObject.addProperty("max", stack.maxCount());
			if (!stack.nbt().isEmpty()) {
				stackObject.addProperty("nbt", stack.nbt());
			}
			array.add(stackObject);
		}
		object.add("stacks", array);
		return object;
	}

	public static TrainCargo fromJson(JsonObject object) {
		final int slots = object.has("slots") ? Math.max(1, object.get("slots").getAsInt()) : SLOTS_PER_CAR;
		final TrainCargo cargo = new TrainCargo(slots);
		if (!object.has("stacks") || !object.get("stacks").isJsonArray()) {
			return cargo;
		}
		for (final JsonElement element : object.getAsJsonArray("stacks")) {
			if (!element.isJsonObject()) {
				continue;
			}
			final JsonObject stack = element.getAsJsonObject();
			if (!stack.has("item") || !stack.has("count")) {
				continue;
			}
			final int max = stack.has("max") ? stack.get("max").getAsInt() : DEFAULT_MAX_STACK;
			final String nbt = stack.has("nbt") ? stack.get("nbt").getAsString() : "";
			cargo.insert(stack.get("item").getAsString(), nbt, max, stack.get("count").getAsInt());
		}
		return cargo;
	}
}
