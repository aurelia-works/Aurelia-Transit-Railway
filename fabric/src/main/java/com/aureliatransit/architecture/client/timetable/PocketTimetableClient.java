package com.aureliatransit.architecture.client.timetable;

import com.aureliatransit.architecture.terminal.MapLine;
import com.aureliatransit.architecture.terminal.MapStop;
import com.aureliatransit.architecture.terminal.StationInfo;
import com.aureliatransit.architecture.terminal.SystemMap;
import com.aureliatransit.architecture.timetable.PocketSheet;
import com.aureliatransit.architecture.timetable.PocketTimetableItem;
import com.aureliatransit.architecture.timetable.PocketTimetableNetworking;
import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.wayfinding.LineBadge;
import com.aureliatransit.architecture.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import org.mtr.aurelia.client.MtrBridge;
import org.mtr.core.data.Platform;

import java.util.ArrayList;
import java.util.List;

/** Prints a pocket timetable from the platform the player is sneak-clicking, using live MTR client data. */
public final class PocketTimetableClient {

	private PocketTimetableClient() {
	}

	public static void init() {
		PocketTimetableItem.openScreen = stack -> {
			final MinecraftClient client = MinecraftClient.getInstance();
			if (client != null) {
				client.setScreen(new PocketTimetableScreen(stack));
			}
		};
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!world.isClient || !player.isSneaking()) {
				return ActionResult.PASS;
			}
			final var held = player.getStackInHand(hand);
			if (!held.isEmpty() && !held.isOf(ModItems.POCKET_TIMETABLE)) {
				return ActionResult.PASS;
			}
			final Platform platform = MtrBridge.platformNear(hit.getBlockPos().getX() + 0.5, hit.getBlockPos().getY() + 0.5, hit.getBlockPos().getZ() + 0.5, 8);
			if (platform == null) {
				return ActionResult.PASS;
			}
			final String name = MtrBridge.displayName(platform.getName());
			final boolean flipToMap = held.isOf(ModItems.POCKET_TIMETABLE) && PocketTimetableItem.read(held).kind() == PocketSheet.Kind.TIMETABLE && !PocketTimetableItem.read(held).lines().isEmpty();
			final PocketSheet.Sheet sheet = flipToMap ? mapOf(name) : timetableOf(name, platform);
			ClientPlayNetworking.send(PocketTimetableNetworking.PRINT, PocketTimetableNetworking.write(sheet));
			return ActionResult.SUCCESS;
		});
	}

	private static PocketSheet.Sheet timetableOf(String name, Platform platform) {
		final List<Long> times = new ArrayList<>();
		if (platform != null) {
			for (final var arrival : MtrBridge.platformArrivals(platform)) {
				final long millis = Math.floorMod(arrival.departureMillis(), org.mtr.aurelia.timetable.Timetable.DAY);
				times.add(millis);
			}
		}
		final StationInfo info = new StationInfo(name, "", platform == null ? List.of() : List.of(new PlatformReference(platform.getId(), name, 0)),
				List.of(), List.of(), "", "", List.of());
		return PocketSheet.timetable(info, times);
	}

	private static PocketSheet.Sheet mapOf(String name) {
		final List<MapLine> lines = new ArrayList<>();
		int index = 0;
		for (final var route : MtrBridge.dashboardData().routes) {
			if (index >= SystemMap.MAX_LINES) {
				break;
			}
			final List<MapStop> stops = new ArrayList<>();
			route.getRoutePlatforms().forEach(data -> {
				if (data.platform != null && stops.size() < SystemMap.MAX_STOPS) {
					final String stop = MtrBridge.displayName(data.platform.getName());
					stops.add(new MapStop(data.platform.getId(), stop.isEmpty() ? data.platform.getName() : stop, stop.equals(name), false));
				}
			});
			if (!stops.isEmpty()) {
				lines.add(new MapLine(route.getId(), new LineBadge(route.getRouteNumber(), route.getColor(), null), route.getName(), stops));
				index++;
			}
		}
		return PocketSheet.lineMap(name, new SystemMap(lines));
	}
}
