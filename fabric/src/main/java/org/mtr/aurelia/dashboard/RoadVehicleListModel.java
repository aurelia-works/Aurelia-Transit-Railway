package org.mtr.aurelia.dashboard;

import org.mtr.aurelia.jump.JumpTarget;
import org.mtr.aurelia.overlap.Point;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Pure filtering, sorting, indexing, and formatting logic for the Bus & Road dashboard's vehicle and interchange lists.
 * Supports all road vehicle kinds (buses, cars, taxis, vans, bikes, and future {@code RoadVehicleKind} entries).
 */
public final class RoadVehicleListModel {

	public enum Filter {
		ALL("all", Set.of()),
		BUS("bus", Set.of("bus", "coach", "minibus", "shuttle", "articulated_bus", "double_decker_bus")),
		CAR("car", Set.of("car", "sedan", "hatchback", "suv", "coupe")),
		TAXI("taxi", Set.of("taxi", "cab")),
		VAN("van", Set.of("van", "truck", "lorry", "delivery", "pickup")),
		BICYCLE("bicycle", Set.of("bicycle", "bike", "ebike")),
		ACTIVE("active", Set.of());

		public final String key;
		private final Set<String> matchingKinds;

		Filter(String key, Set<String> matchingKinds) {
			this.key = key;
			this.matchingKinds = matchingKinds;
		}

		public String translationKey() {
			return "gui.aurelia.dashboard.road.filter." + key;
		}

		public boolean matchesKind(String kindId) {
			if (this == ALL || this == ACTIVE) {
				return true;
			}
			if (kindId == null || kindId.isEmpty()) {
				return false;
			}
			final String lower = kindId.toLowerCase(Locale.ROOT);
			if (matchingKinds.contains(lower)) {
				return true;
			}
			return switch (this) {
				case BUS -> lower.contains("bus") || lower.contains("coach");
				case CAR -> lower.contains("car");
				case TAXI -> lower.contains("taxi") || lower.contains("cab");
				case VAN -> lower.contains("van") || lower.contains("truck");
				case BICYCLE -> lower.contains("bike") || lower.contains("bicycle");
				default -> false;
			};
		}

		public boolean matches(RoadVehicleRow row) {
			if (row == null) {
				return false;
			}
			if (this == ALL) {
				return true;
			}
			if (this == ACTIVE) {
				return row.passengers() > 0 || Math.abs(row.speedKmh()) >= 0.5;
			}
			return matchesKind(row.kindId());
		}
	}

	public record RoadVehicleRow(
			int entityId,
			String kindId,
			String kindLabel,
			double speedKmh,
			String driverName,
			int passengers,
			int capacity,
			boolean localRiding,
			boolean hired,
			int colour,
			float yaw,
			double x,
			double y,
			double z,
			double distance
	) {
		public long roundedSpeedKmh() {
			return Math.round(Math.abs(speedKmh));
		}

		public int blockX() {
			return (int) Math.floor(x);
		}

		public int blockY() {
			return (int) Math.floor(y);
		}

		public int blockZ() {
			return (int) Math.floor(z);
		}

		public String listLabel() {
			final String prefix = localRiding ? "★ " : "";
			final long kmh = roundedSpeedKmh();
			final String status = kmh > 0
					? kmh + " km/h"
					: passengers + "/" + Math.max(1, capacity);
			return String.format(Locale.ROOT, "%s%s · %s (%dm)", prefix, kindLabel, status, Math.round(distance));
		}

		public String coordinatesLabel() {
			return String.format(Locale.ROOT, "%d, %d, %d (%d m)", blockX(), blockY(), blockZ(), Math.round(distance));
		}
	}

	public record StationInterchangeRow(
			long stationId,
			String name,
			long zone,
			int color,
			int platforms,
			int routes,
			int modesCount,
			double x,
			double z,
			double distance
	) {
		public boolean isInterchange() {
			return routes >= 2 || modesCount >= 2;
		}

		public int blockX() {
			return (int) Math.round(x);
		}

		public int blockZ() {
			return (int) Math.round(z);
		}
	}

	public record StationRouteInfo(int routeCount, Set<TransportMode> modes) {
		public static final StationRouteInfo EMPTY = new StationRouteInfo(0, Set.of());
	}

	private RoadVehicleListModel() {
	}

	/** Converts {@code RoadVehicleEntity.speed()} (metres per tick, 20 ticks/s) to km/h. */
	public static double speedKmhFromMetresPerTick(double metresPerTick) {
		return Math.abs(metresPerTick) * 72.0;
	}

	/**
	 * Indexes station route counts and transport modes in a single {@code O(routes × platforms)} pass,
	 * counting each distinct route serving a station once even if a loop route visits that station twice.
	 */
	public static Map<Long, StationRouteInfo> indexStationRoutes(@Nullable Iterable<Route> routes) {
		if (routes == null) {
			return Map.of();
		}
		final Map<Long, Integer> counts = new HashMap<>();
		final Map<Long, EnumSet<TransportMode>> modesByStation = new HashMap<>();
		for (final Route route : routes) {
			if (route == null) {
				continue;
			}
			final Set<Long> seenStations = new HashSet<>();
			for (final RoutePlatformData rp : route.getRoutePlatforms()) {
				if (rp != null && rp.platform != null && rp.platform.area != null) {
					final long stationId = rp.platform.area.getId();
					if (seenStations.add(stationId)) {
						counts.merge(stationId, 1, Integer::sum);
						modesByStation.computeIfAbsent(stationId, k -> EnumSet.noneOf(TransportMode.class))
								.add(route.getTransportMode());
					}
				}
			}
		}
		final Map<Long, StationRouteInfo> result = new HashMap<>(counts.size());
		for (final Map.Entry<Long, Integer> entry : counts.entrySet()) {
			final long stationId = entry.getKey();
			final EnumSet<TransportMode> modes = modesByStation.get(stationId);
			result.put(stationId, new StationRouteInfo(
					entry.getValue(),
					modes == null ? Set.of() : Collections.unmodifiableSet(modes)
			));
		}
		return result;
	}

	/**
	 * Builds {@link StationInterchangeRow}s in {@code O(stations + routes × platforms)} using {@link #indexStationRoutes}.
	 */
	public static List<StationInterchangeRow> buildStationRows(
			@Nullable Iterable<Station> stations,
			@Nullable Iterable<Route> routes,
			double refX,
			double refZ,
			@Nullable Function<String, String> nameFormatter
	) {
		final Map<Long, StationRouteInfo> routeIndex = indexStationRoutes(routes);
		final List<StationInterchangeRow> rows = new ArrayList<>();
		if (stations == null) {
			return rows;
		}
		for (final Station station : stations) {
			if (station == null) {
				continue;
			}
			final double sx;
			final double sz;
			if (AreaBase.validCorners(station) && station.getCenter() != null) {
				final Position center = station.getCenter();
				sx = center.getX();
				sz = center.getZ();
			} else if (!station.savedRails.isEmpty()) {
				final Position mid = station.savedRails.iterator().next().getMidPosition();
				sx = mid.getX();
				sz = mid.getZ();
			} else {
				continue;
			}

			final EnumSet<TransportMode> modes = EnumSet.noneOf(TransportMode.class);
			for (final SavedRailBase<?, ?> rail : station.savedRails) {
				modes.add(rail.getTransportMode());
			}
			final StationRouteInfo routeInfo = routeIndex.getOrDefault(station.getId(), StationRouteInfo.EMPTY);
			modes.addAll(routeInfo.modes());

			final String rawName = station.getName() == null ? "" : station.getName();
			final String displayName = nameFormatter != null ? nameFormatter.apply(rawName) : rawName;
			rows.add(new StationInterchangeRow(
					station.getId(),
					displayName,
					station.getZone1(),
					station.getColor(),
					station.savedRails.size(),
					routeInfo.routeCount(),
					modes.size(),
					sx,
					sz,
					Math.hypot(sx - refX, sz - refZ)
			));
		}
		return rows;
	}

	/**
	 * Computes the {@code /tp} command for a station: stands one block above the first platform midpoint via
	 * {@link JumpTarget#standOn} (safe for elevated and underground stations), falling back to {@code fallbackPlayerY}
	 * only when {@code station.savedRails} is empty.
	 */
	public static String stationTeleportCommand(@Nullable Station station, StationInterchangeRow row, double fallbackPlayerY) {
		if (station != null && !station.savedRails.isEmpty()) {
			final List<Point> mids = new ArrayList<>(station.savedRails.size());
			for (final SavedRailBase<?, ?> rail : station.savedRails) {
				final Position mid = rail.getMidPosition();
				mids.add(new Point(mid.getX(), mid.getY(), mid.getZ()));
			}
			final Optional<Point> standOn = JumpTarget.standOn(mids);
			if (standOn.isPresent()) {
				return JumpTarget.command(standOn.get());
			}
		}
		return String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f", row.x() + 0.5, fallbackPlayerY, row.z() + 0.5);
	}

	/**
	 * Filters and sorts road vehicles: the vehicle ridden by the local player always comes first,
	 * then nearest distance ascending, with entity ID as a deterministic tie-breaker.
	 */
	public static List<RoadVehicleRow> filterAndSortVehicles(
			Collection<RoadVehicleRow> rows,
			Filter filter,
			String query
	) {
		final Filter activeFilter = filter == null ? Filter.ALL : filter;
		final String trimmed = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);

		final List<RoadVehicleRow> result = new ArrayList<>();
		for (final RoadVehicleRow row : rows) {
			if (row == null || !activeFilter.matches(row)) {
				continue;
			}
			if (!trimmed.isEmpty() && !matchesVehicleQuery(row, trimmed)) {
				continue;
			}
			result.add(row);
		}
		result.sort(Comparator
				.comparing((RoadVehicleRow row) -> !row.localRiding())
				.thenComparingDouble(RoadVehicleRow::distance)
				.thenComparingInt(RoadVehicleRow::entityId));
		return result;
	}

	public static boolean matchesVehicleFilterAndQuery(RoadVehicleRow row, @Nullable Filter filter, @Nullable String query) {
		if (row == null) {
			return false;
		}
		final Filter activeFilter = filter == null ? Filter.ALL : filter;
		if (!activeFilter.matches(row)) {
			return false;
		}
		final String trimmed = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
		return trimmed.isEmpty() || matchesVehicleQuery(row, trimmed);
	}

	private static boolean matchesVehicleQuery(RoadVehicleRow row, String query) {
		final String searchable = (
				(row.kindId() == null ? "" : row.kindId()) + " | " +
				(row.kindLabel() == null ? "" : row.kindLabel()) + " | " +
				(row.driverName() == null ? "" : row.driverName()) + " | " +
				(row.hired() ? "hired hire bike | " : "") +
				(row.passengers() > 0 ? "occupied driving | " : "parked empty | ") +
				row.blockX() + " " + row.blockZ() + " | " +
				row.blockX() + ", " + row.blockZ() + " | " +
				row.blockX() + " " + row.blockY() + " " + row.blockZ()
		).toLowerCase(Locale.ROOT);
		return searchable.contains(query);
	}

	/**
	 * Filters and sorts stations / bus interchanges nearest-first.
	 */
	public static List<StationInterchangeRow> filterAndSortStations(
			Collection<StationInterchangeRow> rows,
			boolean interchangesOnly,
			String query
	) {
		final String trimmed = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);

		final List<StationInterchangeRow> result = new ArrayList<>();
		for (final StationInterchangeRow row : rows) {
			if (row == null) {
				continue;
			}
			if (interchangesOnly && !row.isInterchange()) {
				continue;
			}
			if (!trimmed.isEmpty()) {
				final String searchable = ((row.name() == null ? "" : row.name())
						+ " | zone " + row.zone()
						+ (row.isInterchange() ? " | interchange" : "")
						+ " | " + row.blockX() + " " + row.blockZ()
						+ " | " + row.blockX() + ", " + row.blockZ())
						.toLowerCase(Locale.ROOT);
				if (!searchable.contains(trimmed)) {
					continue;
				}
			}
			result.add(row);
		}
		result.sort(Comparator
				.comparingDouble(StationInterchangeRow::distance)
				.thenComparing(row -> row.name() == null ? "" : row.name().toLowerCase(Locale.ROOT))
				.thenComparingLong(StationInterchangeRow::stationId));
		return result;
	}

	/**
	 * Builds the ordered list of cycleable filters for the UI button: always {@link Filter#ALL}, any category filters
	 * matching registered or loaded {@code kindId}s, and {@link Filter#ACTIVE}.
	 */
	public static List<Filter> availableFilters(Collection<String> kindIds) {
		final List<Filter> filters = new ArrayList<>();
		filters.add(Filter.ALL);
		for (final Filter candidate : new Filter[]{Filter.BUS, Filter.CAR, Filter.TAXI, Filter.VAN, Filter.BICYCLE}) {
			for (final String kindId : kindIds) {
				if (candidate.matchesKind(kindId)) {
					filters.add(candidate);
					break;
				}
			}
		}
		filters.add(Filter.ACTIVE);
		return filters;
	}
}
