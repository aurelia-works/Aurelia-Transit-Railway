package org.mtr.aurelia.crew;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.driving.DriverSounds;
import org.mtr.mod.block.BlockSignalBase;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.data.VehicleExtension;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The crew key and what the client hears. One key does the role's job: a guard rings ready to start for the train they
 * ride, a dispatcher gives right away to the trains standing near them, a signaller holds or releases the signal they
 * look at. Bells heard for the train this player rides go to its {@link CrewTrain}, which the cab HUD reads; held
 * signals are what {@code SignalLookahead} and {@code PathSignals} show red.
 */
public final class CrewClient {

	/** How far a signaller can point at a signal. */
	private static final double POINT_REACH = 64;
	private static final double STANDSTILL = 0.1;
	private static final long SECOND_BELL_MILLIS = 350;
	private static final int MAX_TRAINS = 64;

	private static KeyBinding crewKey;
	private static CrewRole role = CrewRole.NONE;
	private static final Set<Long> HOLDS = new HashSet<>();
	private static final Map<Long, CrewTrain> TRAINS = new HashMap<>();
	private static long secondBellAt = -1;

	private CrewClient() {
	}

	public static void register() {
		crewKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.crew.signal", GLFW.GLFW_KEY_G, "category.mtr.keybinding"));
		ClientTickEvents.END_CLIENT_TICK.register(CrewClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(CrewServer.BELL_HEARD, (client, handler, buf, response) -> {
			final CrewBell bell = CrewBell.byIndex(buf.readVarInt());
			final long vehicleId = buf.readLong();
			final String from = buf.readString(64);
			client.execute(() -> heard(bell, vehicleId, from));
		});
		ClientPlayNetworking.registerGlobalReceiver(CrewServer.HOLDS, (client, handler, buf, response) -> {
			final int count = Math.min(buf.readVarInt(), CrewServer.MAX_HOLDS);
			final long[] held = new long[count];
			for (int i = 0; i < count; i++) {
				held[i] = buf.readLong();
			}
			client.execute(() -> {
				HOLDS.clear();
				for (final long pos : held) {
					HOLDS.add(pos);
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(CrewServer.ROLE, (client, handler, buf, response) -> {
			final CrewRole received = CrewRole.byIndex(buf.readVarInt());
			client.execute(() -> role = received);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			role = CrewRole.NONE;
			HOLDS.clear();
			TRAINS.clear();
			secondBellAt = -1;
		}));
	}

	/** This player's role, as the server last said. */
	public static CrewRole role() {
		return role;
	}

	/** True when a signaller holds the signal block at {@code pos} at danger. */
	public static boolean held(BlockPos pos) {
		return held(pos.asLong());
	}

	/** {@link #held(BlockPos)} for a packed block position. */
	public static boolean held(long pos) {
		return !HOLDS.isEmpty() && HOLDS.contains(pos);
	}

	/** What the crew of the train with this id have signalled, or null when none have. */
	@Nullable
	public static CrewTrain train(long vehicleId) {
		return TRAINS.get(vehicleId);
	}

	private static void tick(MinecraftClient client) {
		if (secondBellAt >= 0 && System.currentTimeMillis() >= secondBellAt) {
			secondBellAt = -1;
			DriverSounds.bell();
		}
		if (crewKey == null) {
			return;
		}
		while (crewKey.wasPressed()) {
			if (client.player != null) {
				act(client, client.player);
			}
		}
	}

	private static void act(MinecraftClient client, ClientPlayerEntity player) {
		switch (role) {
			case GUARD -> {
				final VehicleExtension vehicle = riddenVehicle();
				if (vehicle == null) {
					player.sendMessage(Text.translatable("gui.aurelia.crew.not_on_train"), true);
				} else if (vehicle.persistentVehicleData.getDoorValue() > 0.01 || vehicle.vehicleExtraData.getDoorMultiplier() > 0) {
					player.sendMessage(Text.translatable("gui.aurelia.crew.doors_open"), true);
				} else {
					sendBell(CrewBell.READY_TO_START, vehicle.getId());
				}
			}
			case DISPATCHER -> sendBell(CrewBell.RIGHT_AWAY, -1);
			case SIGNALLER -> {
				final HitResult hit = player.raycast(POINT_REACH, 0, false);
				if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && client.world != null
						&& client.world.getBlockState(blockHit.getBlockPos()).getBlock() instanceof BlockSignalBase) {
					final PacketByteBuf buf = PacketByteBufs.create();
					buf.writeBlockPos(blockHit.getBlockPos());
					ClientPlayNetworking.send(CrewServer.HOLD, buf);
				} else {
					player.sendMessage(Text.translatable("gui.aurelia.crew.hold.look"), true);
				}
			}
			default -> player.sendMessage(Text.translatable("gui.aurelia.crew.pick_role"), true);
		}
	}

	private static void sendBell(CrewBell bell, long vehicleId) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(bell.ordinal());
		buf.writeLong(vehicleId);
		ClientPlayNetworking.send(CrewServer.BELL, buf);
	}

	/** A bell rung near this player: it counts when it is for the train this player rides. */
	private static void heard(@Nullable CrewBell bell, long vehicleId, String from) {
		final ClientPlayerEntity player = MinecraftClient.getInstance().player;
		final VehicleExtension vehicle = riddenVehicle();
		if (bell == null || player == null || vehicle == null) {
			return;
		}
		if (bell == CrewBell.RIGHT_AWAY) {
			// for the trains standing at the platforms round the dispatcher
			if (vehicle.getSpeed() * 1000 > STANDSTILL) {
				return;
			}
		} else if (vehicle.getId() != vehicleId) {
			return;
		}
		if (TRAINS.size() > MAX_TRAINS && !TRAINS.containsKey(vehicle.getId())) {
			TRAINS.clear();
		}
		TRAINS.computeIfAbsent(vehicle.getId(), id -> new CrewTrain()).bell(bell);
		DriverSounds.bell();
		if (bell == CrewBell.READY_TO_START) {
			secondBellAt = System.currentTimeMillis() + SECOND_BELL_MILLIS;
		}
		player.sendMessage(Text.translatable(bell == CrewBell.READY_TO_START ? "gui.aurelia.crew.heard.ready" : "gui.aurelia.crew.heard.right_away", from), true);
	}

	@Nullable
	private static VehicleExtension riddenVehicle() {
		if (!VehicleRidingMovement.isRidingAnyVehicle()) {
			return null;
		}
		for (final VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
			if (VehicleRidingMovement.isRiding(vehicle.getId())) {
				return vehicle;
			}
		}
		return null;
	}
}
