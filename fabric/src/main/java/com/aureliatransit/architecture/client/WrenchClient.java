package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import com.aureliatransit.architecture.station.world.WrenchMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the one wrench: a key (V by default) and sneak + mouse wheel change the mode of a wrench in either hand,
 * tell the server so its next click plans in that mode, and show the mode on the action bar (switchable on the dev panel).
 */
public final class WrenchClient {

	private static KeyBinding modeKey;
	private static KeyBinding menuKey;

	private WrenchClient() {
	}

	public static void init() {
		modeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.wrench_mode",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.mtr"));
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.atr_menu",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.mtr"));
		ClientTickEvents.END_CLIENT_TICK.register(WrenchClient::tick);
	}

	private static void tick(MinecraftClient client) {
		while (modeKey != null && modeKey.wasPressed()) {
			cycle(client, 1);
		}
		while (menuKey != null && menuKey.wasPressed()) {
			if (client.currentScreen == null) {
				AtrMenuScreen.open(null, AtrMenuScreen.Tab.BUILD);
			}
		}
	}

	/**
	 * Sneak + wheel, from the mouse mixin. Returns true when it switched the wrench mode, so the hotbar does not scroll too.
	 */
	public static boolean onScroll(double amount) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen != null || client.player == null || !client.player.isSneaking() || amount == 0) {
			return false;
		}
		return cycle(client, amount > 0 ? 1 : -1);
	}

	private static boolean cycle(MinecraftClient client, int step) {
		if (client.player == null) {
			return false;
		}
		final Hand hand = wrenchHand(client);
		if (hand == null) {
			return false;
		}
		final ItemStack stack = client.player.getStackInHand(hand);
		final WrenchMode mode = StationWrenchItem.cycleMode(stack, step);
		if (ClientPlayNetworking.canSend(ModPackets.STATION_WRENCH)) {
			ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, StationWrenchItem.Action.MODE,
					StationWrenchItem.description(stack), StationWrenchItem.recipe(stack).encode(), mode));
		}
		if (WrenchSettings.actionBar()) {
			client.player.sendMessage(mode.label(), true);
		}
		return true;
	}

	/** The hand holding any wrench (the new one or either old id), main hand first. */
	public static Hand wrenchHand(MinecraftClient client) {
		if (client.player == null) {
			return null;
		}
		for (final Hand hand : Hand.values()) {
			if (client.player.getStackInHand(hand).getItem() instanceof StationWrenchItem) {
				return hand;
			}
		}
		return null;
	}
}
