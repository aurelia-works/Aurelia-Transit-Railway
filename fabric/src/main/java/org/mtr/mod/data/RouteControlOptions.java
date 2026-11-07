package org.mtr.mod.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import org.mtr.aurelia.route.RouteControl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * One route's route-control data (waypoints, platform choice per stop, fallback to a free platform), keyed by route
 * id. Kept in its own saved file (not in MTR's route data, which ATR must not reshape), so old worlds and MTR itself
 * never see it: a route with no entry loads as {@link RouteControl#EMPTY}.
 */
public final class RouteControlOptions {

	private final Map<Long, RouteControl> controls = new HashMap<>();

	/** What this client was last sent by the server. */
	private static volatile Map<Long, RouteControl> client = Map.of();

	public RouteControl get(long routeId) {
		return controls.getOrDefault(routeId, RouteControl.EMPTY);
	}

	/** @return whether anything changed */
	public boolean set(long routeId, RouteControl control) {
		if (control == null || control.isEmpty()) {
			return controls.remove(routeId) != null;
		}
		if (control.equals(controls.get(routeId))) {
			return false;
		}
		controls.put(routeId, control);
		return true;
	}

	public Map<Long, RouteControl> all() {
		return Collections.unmodifiableMap(controls);
	}

	public NbtCompound toNbt(NbtCompound nbt) {
		final NbtList list = new NbtList();
		controls.forEach((routeId, control) -> {
			final NbtCompound entry = new NbtCompound();
			entry.putLong("route", routeId);
			entry.putBoolean("fallback", control.fallback());
			final NbtList waypointList = new NbtList();
			control.waypoints().forEach(waypoint -> {
				final NbtCompound waypointNbt = new NbtCompound();
				waypointNbt.putLong("node", waypoint.nodeId());
				waypointNbt.putInt("after", waypoint.afterStop());
				waypointList.add(waypointNbt);
			});
			entry.put("waypoints", waypointList);
			final NbtList platformList = new NbtList();
			control.platformChoices().forEach((stop, platformId) -> {
				final NbtCompound choiceNbt = new NbtCompound();
				choiceNbt.putInt("stop", stop);
				choiceNbt.putLong("platform", platformId);
				platformList.add(choiceNbt);
			});
			entry.put("platforms", platformList);
			list.add(entry);
		});
		nbt.put("routes", list);
		return nbt;
	}

	public static RouteControlOptions fromNbt(NbtCompound nbt) {
		final RouteControlOptions options = new RouteControlOptions();
		final NbtList list = nbt.getList("routes", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			final NbtCompound entry = list.getCompound(i);
			final java.util.List<RouteControl.Waypoint> waypoints = new java.util.ArrayList<>();
			for (final NbtElement element : entry.getList("waypoints", NbtElement.COMPOUND_TYPE)) {
				final NbtCompound waypointNbt = (NbtCompound) element;
				final long node = waypointNbt.getLong("node");
				final int after = waypointNbt.getInt("after");
				if (node != 0 && after >= 0) {
					waypoints.add(new RouteControl.Waypoint(node, after));
				}
			}
			final Map<Integer, Long> choices = new HashMap<>();
			for (final NbtElement element : entry.getList("platforms", NbtElement.COMPOUND_TYPE)) {
				final NbtCompound choiceNbt = (NbtCompound) element;
				final int stop = choiceNbt.getInt("stop");
				final long platform = choiceNbt.getLong("platform");
				if (stop >= 0 && platform != 0) {
					choices.put(stop, platform);
				}
			}
			final RouteControl control = new RouteControl(waypoints, choices, entry.getBoolean("fallback"));
			if (!control.isEmpty()) {
				options.controls.put(entry.getLong("route"), control);
			}
		}
		return options;
	}

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(controls.size());
		controls.forEach((routeId, control) -> {
			buf.writeLong(routeId);
			writeControl(buf, control);
		});
	}

	public static Map<Long, RouteControl> read(PacketByteBuf buf) {
		final int count = buf.readVarInt();
		final Map<Long, RouteControl> read = new HashMap<>();
		for (int i = 0; i < count; i++) {
			final long routeId = buf.readLong();
			final RouteControl control = readControl(buf);
			if (!control.isEmpty()) {
				read.put(routeId, control);
			}
		}
		return read;
	}

	public static void writeControl(PacketByteBuf buf, RouteControl control) {
		buf.writeBoolean(control.fallback());
		buf.writeVarInt(control.waypoints().size());
		control.waypoints().forEach(waypoint -> {
			buf.writeLong(waypoint.nodeId());
			buf.writeVarInt(waypoint.afterStop());
		});
		buf.writeVarInt(control.platformChoices().size());
		control.platformChoices().forEach((stop, platformId) -> {
			buf.writeVarInt(stop);
			buf.writeLong(platformId);
		});
	}

	public static RouteControl readControl(PacketByteBuf buf) {
		final boolean fallback = buf.readBoolean();
		final int waypointCount = buf.readVarInt();
		final java.util.List<RouteControl.Waypoint> waypoints = new java.util.ArrayList<>();
		for (int i = 0; i < waypointCount; i++) {
			final long node = buf.readLong();
			final int after = buf.readVarInt();
			if (node != 0 && after >= 0) {
				waypoints.add(new RouteControl.Waypoint(node, after));
			}
		}
		final int choiceCount = buf.readVarInt();
		final Map<Integer, Long> choices = new HashMap<>();
		for (int i = 0; i < choiceCount; i++) {
			final int stop = buf.readVarInt();
			final long platform = buf.readLong();
			if (stop >= 0 && platform != 0) {
				choices.put(stop, platform);
			}
		}
		return new RouteControl(waypoints, choices, fallback);
	}

	public static void setClient(Map<Long, RouteControl> value) {
		client = Map.copyOf(value);
	}

	public static RouteControl client(long routeId) {
		return client.getOrDefault(routeId, RouteControl.EMPTY);
	}
}
