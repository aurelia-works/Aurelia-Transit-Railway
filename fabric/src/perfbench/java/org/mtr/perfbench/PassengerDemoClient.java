package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.mtr.aurelia.client.PassengerDrawSettings;
import org.mtr.core.data.Lift;
import org.mtr.core.data.LiftFloor;
import org.mtr.core.data.Position;
import org.mtr.core.data.Siding;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mod.Init;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.MinecraftClientData;
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
			send(client, "gamerule doDaylightCycle true", "time set day", "weather clear", "fill 258 -60 2 294 -60 9 mtr:platform",
					// Pillars between the waiting spots and the doors: a straight walk along the platform goes through them
					"fill 268 -59 2 268 -56 4 minecraft:stone_bricks", "fill 274 -59 2 274 -56 4 minecraft:stone_bricks", "fill 280 -59 2 280 -56 4 minecraft:stone_bricks", "fill 286 -59 2 286 -56 4 minecraft:stone_bricks",
					// The train's next stop, far enough off to need loading first
					"forceload add 128 2 164 16",
					"tp @s 276 -59 8 facing 276 -58 0");
			exits(client, 0, false);
			automaticTrains();
			stage = 1;
		} else if (stage == 1 && ticks >= TRAIN_TICKS) {
			send(client, "fill 128 -60 2 164 -60 9 mtr:platform",
					"fill 138 -59 2 138 -56 4 minecraft:stone_bricks", "fill 144 -59 2 144 -56 4 minecraft:stone_bricks", "fill 150 -59 2 150 -56 4 minecraft:stone_bricks", "fill 156 -59 2 156 -56 4 minecraft:stone_bricks",
					"mtr clearVehicles allDepots", "mtr generatePath allDepots");
			exits(client, -130, true);
			stage = 2;
		} else if (stage == 2 && ticks >= DEMO_TICKS) {
			lift(client, 282);
			lift(client, 152);
			send(client, "atr_passengers demo 40");
			client.player.sendMessage(Text.literal("Passenger demo ready. The train comes to this platform and people board it.").formatted(Formatting.GREEN), false);
			client.player.sendMessage(Text.literal("Open the Aurelia panel's Passengers tab for counts, colours and size. /atr_passengers clear stops it.").formatted(Formatting.GRAY), false);
			ready = true;
			stage = 3;
		}
	}

	/** A deck three up behind the platform, reached by stairs at x 262, an escalator at x 270 and a lift at x 282. */
	private static void exits(MinecraftClient client, int dx, boolean escalatorUp) {
		send(client, String.format("fill %d -57 13 %d -57 16 minecraft:stone_bricks", 258 + dx, 294 + dx));
		send(client, String.format("fill %d -60 10 %d -60 12 mtr:platform", 258 + dx, 294 + dx));
		for (int i = 0; i < 3; i++) {
			send(client, String.format("fill %d %d %d %d %d %d minecraft:stone_brick_stairs[facing=south]", 262 + dx, -59 + i, 10 + i, 263 + dx, -59 + i, 10 + i));
		}
		for (int i = 0; i < 4; i++) {
			final int y = -60 + i;
			final int z = 10 + i;
			final String step = "mtr:escalator_step[facing=south,direction=" + escalatorUp + ",status=true,side=";
			final String side = "mtr:escalator_side[facing=south,side=";
			send(client,
					String.format("setblock %d %d %d %sright]", 270 + dx, y, z, step),
					String.format("setblock %d %d %d %sleft]", 271 + dx, y, z, step),
					String.format("setblock %d %d %d %sright]", 270 + dx, y + 1, z, side),
					String.format("setblock %d %d %d %sleft]", 271 + dx, y + 1, z, side));
		}
		send(client, String.format("fill %d -59 13 %d -54 13 mtr:lift_track_1[facing=north]", 282 + dx, 282 + dx),
				String.format("setblock %d -59 13 mtr:lift_track_floor_1[facing=north]", 282 + dx),
				String.format("setblock %d -54 13 mtr:lift_track_floor_1[facing=north]", 282 + dx));
	}

	/** The same call MTR's lift refresher makes: a 2 by 2 car in front of the stops, doors to the platform. */
	private static void lift(MinecraftClient client, int x) {
		final ObjectArrayList<LiftFloor> floors = new ObjectArrayList<>();
		floors.add(new LiftFloor(new Position(x, -59, 13), "0", ""));
		floors.add(new LiftFloor(new Position(x, -54, 13), "1", ""));
		final Lift lift = new Lift(new MinecraftClientData());
		lift.setFloors(floors);
		lift.setDimensions(3, 2, 2, 0.5, 0, -1);
		lift.setIsDoubleSided(false);
		lift.setStyle(CustomResourceLoader.DEFAULT_LIFT_TRANSPARENT_ID);
		lift.setAngle(Angle.N);
		final ServerWorld world = client.getServer().getOverworld();
		client.getServer().execute(() -> Init.sendMessageC2S("generate_by_lift", new org.mtr.mapping.holder.MinecraftServer(world.getServer()), new org.mtr.mapping.holder.World(world), lift, null, null));
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
