package com.aureliatransit.architecture.block;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/**
 * MTR's brush is the one tool for editing placed blocks: a sign's text, a display's settings, a style or a car number all
 * change with the brush in hand, so an empty hand (or a block about to be placed) never opens an editor by accident.
 * Matched by item id so the mod does not reach into MTR's item classes.
 */
public final class MtrBrush {
	public static final Identifier ID = new Identifier("mtr", "brush");

	private MtrBrush() {
	}

	public static boolean held(PlayerEntity player, Hand hand) {
		return Registries.ITEM.getId(player.getStackInHand(hand).getItem()).equals(ID);
	}
}
