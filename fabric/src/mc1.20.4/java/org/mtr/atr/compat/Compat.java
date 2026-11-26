package org.mtr.atr.compat;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.io.DataInput;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.Supplier;

/** The Minecraft 1.20.4 side of {@code src/mc1.20.1/java}'s {@code Compat}; the same methods, for 1.20.4's API. */
public final class Compat {

	private Compat() {
	}

	/** No data fixer: Fabric API reads a state saved with none as it is, which is how 1.20.1 read ATR's states. */
	public static <T extends PersistentState> T getOrCreate(PersistentStateManager manager, Function<NbtCompound, T> read, Supplier<T> create, String id) {
		return manager.getOrCreate(new PersistentState.Type<>(create, read, null), id);
	}

	public static NbtCompound readNbt(DataInput input) throws IOException {
		return NbtIo.readCompound(input);
	}

	/** How far below the seat a riding passenger sits. */
	public static double ridingOffset(Entity passenger, Entity vehicle) {
		return passenger.getRidingOffset(vehicle);
	}
}
