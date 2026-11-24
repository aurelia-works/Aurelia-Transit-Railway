package org.mtr.aurelia.client;

import net.minecraft.util.Identifier;

/**
 * The Aurelia block look: a built-in resource pack that repaints MTR's blocks, items, rails and signals under the same ids.
 * Off by default. Minecraft keeps the on/off choice in options.txt with the other resource packs, so no config key is needed,
 * and old worlds are untouched because only textures and models change.
 */
public final class AureliaLookPack {

	public static final Identifier ID = new Identifier("mtr", "aurelia_look");
	private static final BuiltinPack PACK = new BuiltinPack(ID);

	private AureliaLookPack() {
	}

	public static void register() {
		PACK.register("options.mtr.aurelia_block_look.pack");
	}

	public static boolean enabled() {
		return PACK.enabled();
	}

	/** Turns the pack on or off, saves the choice to options.txt and reloads resources. */
	public static void toggle() {
		PACK.toggle();
	}
}
