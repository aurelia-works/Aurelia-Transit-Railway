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

/**
 * Minecraft calls whose shape differs between 1.20.1 and 1.20.4. This is the 1.20.1 side, which makes the same calls ATR
 * always made; {@code src/mc1.20.4/java} holds the 1.20.4 side under the same names.
 */
public final class Compat {

	private Compat() {
	}

	public static <T extends PersistentState> T getOrCreate(PersistentStateManager manager, Function<NbtCompound, T> read, Supplier<T> create, String id) {
		return manager.getOrCreate(read, create, id);
	}

	public static NbtCompound readNbt(DataInput input) throws IOException {
		return NbtIo.read(input);
	}

	/** How far below the seat a riding passenger sits. */
	public static double ridingOffset(Entity passenger, Entity vehicle) {
		return passenger.getHeightOffset();
	}
}
