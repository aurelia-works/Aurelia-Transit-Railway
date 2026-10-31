package org.mtr.aurelia.depot;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.mod.data.DepotOptions;

/**
 * The per-depot switches live in their own saved file; a world must load them back exactly.
 */
public final class DepotOptionsTest {

	@Test
	public void switchesSurviveASaveAndClearCleanly() {
		final DepotOptions options = new DepotOptions();
		Assertions.assertTrue(options.set(7, DepotOptions.Option.ALL_DOORS, true));
		Assertions.assertTrue(options.set(9, DepotOptions.Option.NO_ANNOUNCEMENTS, true));
		Assertions.assertFalse(options.set(9, DepotOptions.Option.NO_ANNOUNCEMENTS, true), "same value is no change");

		final DepotOptions loaded = DepotOptions.fromNbt(options.toNbt(new NbtCompound()));
		Assertions.assertEquals(new DepotOptions.Flags(true, false), loaded.get(7));
		Assertions.assertEquals(new DepotOptions.Flags(false, true), loaded.get(9));
		Assertions.assertEquals(DepotOptions.Flags.NONE, loaded.get(8));

		// Switching both off forgets the depot, so deleted depots don't pile up in the file
		Assertions.assertTrue(loaded.set(7, DepotOptions.Option.ALL_DOORS, false));
		Assertions.assertFalse(loaded.all().containsKey(7L));

		// A world saved before this feature has no file: everything is off
		Assertions.assertTrue(DepotOptions.fromNbt(new NbtCompound()).all().isEmpty());
	}
}
