package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.mtr.aurelia.client.PassengerDrawSettings;

/**
 * Dev-only ready-to-test passenger instance. Boots the bench world, stands the player above the platform, turns the
 * real passenger service on with 40 waiting people, enables drawing, and leaves the game running. No screenshots and no
 * automatic quit: the point is to walk around and watch.
 *
 * <p>Run with {@code ./gradlew :fabric:runPassengerDemo}. It uses {@code run/perf/saves/ATU Test}.
 */
public final class PassengerDemoClient implements ClientModInitializer {

	static final boolean ACTIVE = Boolean.getBoolean("atr.passengerdemo");
	private static final int SETUP_TICKS = 80;
	private static final int DEMO_TICKS = 120;

	private int ticks;
	private int stage;

	@Override
	public void onInitializeClient() {
		if (!ACTIVE) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		ticks++;
		if (stage == 0 && ticks >= SETUP_TICKS) {
			PassengerDrawSettings.setDraw(true);
			send(client, "time set day", "weather clear", "tp @s 276 -58 8 facing 276 -59 0");
			stage = 1;
		} else if (stage == 1 && ticks >= DEMO_TICKS) {
			send(client, "atr_passengers demo 40");
			client.player.sendMessage(Text.literal("Passenger demo ready. 40 people are waiting at this platform.").formatted(Formatting.GREEN), false);
			client.player.sendMessage(Text.literal("Blue boxes = waiting. Orange boxes = riding. /atr_passengers clear stops it.").formatted(Formatting.GRAY), false);
			client.player.sendMessage(Text.literal("Tip: open the Aurelia panel's Passengers tab to watch the counts.").formatted(Formatting.GRAY), false);
			stage = 2;
		}
	}

	private static void send(MinecraftClient client, String... commands) {
		for (final String command : commands) {
			client.player.networkHandler.sendCommand(command);
		}
	}
}
