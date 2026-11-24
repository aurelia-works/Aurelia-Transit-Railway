package org.mtr.aurelia.vehicle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.mtr.aurelia.Operators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Road vehicles: the entity, the car item, the settings the server owns and the packets. New ids only, registered from
 * the mod initializer, not from MTR's lists.
 */
public final class RoadVehicles {

	public static final Logger LOGGER = LoggerFactory.getLogger("ATR Road Vehicles");
	/** Server to client: the settings. */
	public static final Identifier SYNC = new Identifier("mtr", "atr_road_vehicles");
	/** Client to server: settings from the panel. */
	public static final Identifier SET = new Identifier("mtr", "atr_road_vehicles_set");
	/** Client to server: the driver's keys, one byte of {@link #THROTTLE}, {@link #BRAKE}, {@link #LEFT}, {@link #RIGHT}. */
	public static final Identifier INPUT = new Identifier("mtr", "atr_road_vehicle_input");
	public static final int THROTTLE = 1;
	public static final int BRAKE = 2;
	public static final int LEFT = 4;
	public static final int RIGHT = 8;

	public static final EntityType<RoadVehicleEntity> ENTITY = Registry.register(Registries.ENTITY_TYPE, new Identifier("mtr", "road_vehicle"),
			EntityType.Builder.<RoadVehicleEntity>create(RoadVehicleEntity::new, SpawnGroup.MISC)
					.setDimensions((float) RoadVehicleKind.CAR.width, (float) RoadVehicleKind.CAR.height)
					.maxTrackingRange(10)
					.trackingTickInterval(1)
					.build("road_vehicle"));
	/**
	 * The retired road car. It stays registered so old worlds and inventories keep the stack, but it no longer places a
	 * car, has no recipe and is not in the creative tabs.
	 */
	public static final Item CAR_ITEM = Registry.register(Registries.ITEM, new Identifier("mtr", "road_car"), new RetiredCarItem(new Item.Settings().maxCount(1)));

	private static RoadVehicleSettings serverSettings = RoadVehicleSettings.DEFAULT;
	private static RoadVehicleSettings clientSettings = RoadVehicleSettings.DEFAULT;

	private RoadVehicles() {
	}

	public static void register() {
		BikeShare.register();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> serverSettings = RoadVehicleSettings.load(FabricLoader.getInstance().getConfigDir()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
		ServerPlayNetworking.registerGlobalReceiver(SET, (server, player, handler, buf, response) -> {
			final RoadVehicleSettings requested = RoadVehicleSettings.read(buf);
			server.execute(() -> applySettings(server, player, requested));
		});
		ServerPlayNetworking.registerGlobalReceiver(INPUT, (server, player, handler, buf, response) -> {
			final int flags = buf.readByte();
			server.execute(() -> {
				if (player.getVehicle() instanceof RoadVehicleEntity vehicle && vehicle.driver() == player) {
					vehicle.setInput(flags);
				}
			});
		});
	}

	/** The settings this side uses: the server's own, or what the client last heard. */
	public static RoadVehicleSettings settings(boolean client) {
		return client ? clientSettings : serverSettings;
	}

	public static void setClientSettings(RoadVehicleSettings settings) {
		clientSettings = settings.clamped();
	}

	/** Operators, the single-player host and creative-mode players ({@link Operators#isOperatorOrCreative}). */
	public static boolean mayEdit(ServerPlayerEntity player) {
		return Operators.isOperatorOrCreative(player);
	}

	private static void applySettings(MinecraftServer server, ServerPlayerEntity player, RoadVehicleSettings requested) {
		if (mayEdit(player)) {
			serverSettings = requested;
			serverSettings.save(FabricLoader.getInstance().getConfigDir());
			PlayerLookup.all(server).forEach(RoadVehicles::send);
		} else {
			send(player);
		}
	}

	private static void send(ServerPlayerEntity player) {
		final PacketByteBuf buf = PacketByteBufs.create();
		serverSettings.write(buf);
		ServerPlayNetworking.send(player, SYNC, buf);
	}
}
