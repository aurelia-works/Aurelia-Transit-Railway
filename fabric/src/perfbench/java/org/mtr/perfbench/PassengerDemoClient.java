package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.mtr.aurelia.client.PassengerDrawSettings;
import org.mtr.core.data.Siding;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mod.passenger.PassengerService;

/**
 * Dev-only ready-to-test passenger instance, on a copy of the bench world. The bench has bare track and a manual train,
 * so this builds a real platform beside the rail, switches the train to automatic, regenerates the depot, fills the
 * platform through the real service and leaves the game running to walk around and watch.
 *
 * <p>Run with {@code ./gradlew :fabric:runPassengerDemo}. It uses {@code run/perf/saves/ATR Passenger Demo}.
 */
public final class PassengerDemoClient implements ClientModInitializer {

	static final boolean ACTIVE = Boolean.getBoolean("atr.passengerdemo");
	private static final int SETUP_TICKS = 80;
	private static final int TRAIN_TICKS = 100;
	private static final int DEMO_TICKS = 160;

	/** True once the platform has been filled. */
	static volatile boolean ready;

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
			PassengerDrawSettings.setDrawDistance(PassengerDrawSettings.DEFAULT.drawDistance());
			PassengerDrawSettings.setDetailDistance(PassengerDrawSettings.DEFAULT.detailDistance());
			PassengerDrawSettings.setMaxPerPlace(PassengerDrawSettings.DEFAULT.maxPerPlace());
			send(client, "gamerule doDaylightCycle true", "time set day", "weather clear", "fill 258 -60 2 294 -60 9 mtr:platform", "tp @s 276 -59 8 facing 276 -58 0");
			automaticTrains();
			stage = 1;
		} else if (stage == 1 && ticks >= TRAIN_TICKS) {
			send(client, "mtr clearVehicles allDepots", "mtr generatePath allDepots");
			stage = 2;
		} else if (stage == 2 && ticks >= DEMO_TICKS) {
			send(client, "atr_passengers demo 40");
			client.player.sendMessage(Text.literal("Passenger demo ready. The train comes to this platform and people board it.").formatted(Formatting.GREEN), false);
			client.player.sendMessage(Text.literal("Open the Aurelia panel's Passengers tab for counts, colours and size. /atr_passengers clear stops it.").formatted(Formatting.GRAY), false);
			ready = true;
			stage = 3;
		}
	}

	/** MTR only schedules departures for automatic sidings; a manual train waits for a driver. */
	private static void automaticTrains() {
		final ObjectImmutableList<Simulator> simulators = PassengerService.simulators();
		if (simulators == null) {
			return;
		}
		for (final Simulator simulator : simulators) {
			simulator.run(() -> {
				for (final Siding siding : simulator.sidings) {
					if (siding.getIsManual()) {
						siding.setIsManual(false);
					}
				}
			});
		}
	}

	private static void send(MinecraftClient client, String... commands) {
		for (final String command : commands) {
			client.player.networkHandler.sendCommand(command);
		}
	}
}
