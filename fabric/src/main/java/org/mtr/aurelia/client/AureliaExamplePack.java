package org.mtr.aurelia.client;

import net.minecraft.util.Identifier;

/**
 * The generic example private pack, a built-in resource pack that is off by default. It shows every section with
 * made-up names, so the format can be tried without touching a real pack. See {@code docs/private-packs.md}.
 */
public final class AureliaExamplePack {

	public static final Identifier ID = new Identifier("mtr", "example_private_pack");
	private static final BuiltinPack PACK = new BuiltinPack(ID);

	private AureliaExamplePack() {
	}

	public static void register() {
		PACK.register("options.mtr.example_private_pack");
	}

	public static boolean enabled() {
		return PACK.enabled();
	}

	/** Turns the pack on or off, saves the choice to options.txt and reloads resources. */
	public static void toggle() {
		PACK.toggle();
	}
}
