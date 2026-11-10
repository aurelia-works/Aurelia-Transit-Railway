package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Platforms, track bed, rail nodes, bay ends and the doorways beside the building.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanPlatforms extends PlanState {

	PlanPlatforms(StationSpec spec) {
		super(spec);
	}

	/**
	 * A big number beside each platform's pier, seen as you come off the stairs, hung right under whatever is above it (the
	 * deck, or a girder under it). Planned after the roofs, so it never takes a girder's cell; off the middle when a lift
	 * landing or the structure has the middle. A platform numbered over its gates at a terminus head gets no second one.
	 */
	void numberSigns() {
		if (!site.bridge()) {
			return;
		}
		final int v = site.bridgeV() - site.stairSide();
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
			if (!site.served().contains(band.number()) || gateNumbered(band)) {
				continue;
			}
			final List<Integer> numbers = layout.platformNumbers(band);
			if (numbers.size() == 2) {
				// an island: each face's number over its own half, so "1" hangs on the side trains for platform 1 stand
				final int mid = (band.uMin() + band.uMax()) / 2;
				numberSignIn(band, mid, band.uMin(), v, numbers.get(0));
				numberSignIn(band, mid + 1, band.uMax(), v, numbers.get(1));
			} else if (numbers.size() == 1) {
				numberSignIn(band, band.uMin(), band.uMax(), v, numbers.get(0));
			}
		}
	}

	/** One number plate hung over columns {@code from}..{@code to} (either order), nearest the middle of that run first. */
	void numberSignIn(TrackLayout.PlatformBand band, int from, int to, int v, int number) {
		final int lo = Math.min(from, to);
		final int hi = Math.max(from, to);
		final int mid = (lo + hi) / 2;
		for (int d = 0; d <= hi - lo + 1; d++) {
			final int u = mid + (d % 2 == 0 ? d / 2 : -(d + 1) / 2);
			if (u < lo || u > hi || isEdge(band, u)) {
				continue;
			}
			final int y = underside(u, v) - 1;
			if (y >= StationGeometry.WALK_Y + 2 && numberSign(u, y, v, LocalDir.PLUS_V, Integer.toString(number))) {
				return;
			}
		}
	}

	/**
	 * The lowest thing hanging over cell (u, v) under its roof: a light strip or batten counts, so a plate hangs below it
	 * where people see it instead of in the roof void above it. -1 when nothing covers the cell.
	 */
	int underside(int u, int v) {
		final int roof = ceiling(u, v);
		for (int y = StationGeometry.WALK_Y + 1; y < roof; y++) {
			if (cells.containsKey(new Cell(u, y, v))) {
				return y;
			}
		}
		return roof;
	}

	/** A big platform number plate hung from the structure above; double-sided, so it reads from both ways. */
	boolean numberSign(int u, int y, int v, LocalDir front, String number) {
		final Cell cell = new Cell(u, y, v);
		if (cells.containsKey(cell) || air.contains(cell)) {
			skip(Role.FIXTURE, NUMBER_SIGN, cell);
			return false;
		}
		cells.put(cell, new PlannedBlock(u, y, v, Role.FIXTURE, NUMBER_SIGN, front, number, ""));
		return true;
	}

	void floors() {
		for (final TrackLayout.PlatformBand street : site.streets()) {
			// the street entrance: paved like the platforms, railed off along the track, which nobody boards from here
			final int trackSide = street.trackRight() ? street.uMax() : street.uMin();
			for (int v = street.vFrom(); v <= street.vTo(); v++) {
				for (int u = street.uMin(); u <= street.uMax(); u++) {
					put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
				}
				// a real station's square is walled off from the track under its hall instead (see PlanBridges.plateaus)
				if (!site.plateau(street)) {
					put(Role.RAILING, trackSide, StationGeometry.WALK_Y, v, street.trackRight() ? LocalDir.PLUS_U : LocalDir.MINUS_U);
				}
			}
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			// a high platform carries its tactile warning in the edge cell; a low or standard one keeps the same edge layout and
			// the tactile warning is part of the edge texture
			final boolean tactileEdge = spec.tactile();
			final String edgeId = spec.features().edgeLines() ? palette.edgeId(tactileEdge) : palette.surfaceId();
			final String guidance = spec.tactile() ? palette.guidanceId() : null;
			for (int v = band.vFrom(); v <= band.vTo(); v++) {
				for (int u = band.uMin(); u <= band.uMax(); u++) {
					if (u == band.uMin() && band.trackLeft()) {
						put(Role.PLATFORM_EDGE, u, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U, edgeId);
					} else if (u == band.uMax() && band.trackRight()) {
						put(Role.PLATFORM_EDGE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U, edgeId);
					} else if (guidance != null && band.uMax() - band.uMin() >= 4 && u == band.uMin() + 1 && band.trackLeft()) {
						put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U, guidance);
					} else if (guidance != null && band.uMax() - band.uMin() >= 4 && u == band.uMax() - 1 && band.trackRight()) {
						put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U, guidance);
					} else {
						put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
					}
				}
			}
		}
	}

	/**
	 * The platforms (and footbridge street landings) that ramp down at their ends for step-free access. A platform between
	 * tracks (an island, or side platforms back to back) is left out when it has a way off that does not cross a track (a
	 * footbridge, an underpass, the station building's bridge or a terminus's concourse): its ramp would only lead down onto
	 * the track bed between the tracks (Enviro, 10-20: "some of the platform logic doesn't make sense"). It gets an end
	 * railing instead.
	 */
	List<TrackLayout.PlatformBand> rampedBands() {
		// underground the ends are the box's walls, and nobody walks the tunnels
		if (layout.rampLength() == 0 || spec.options().underground()) {
			return List.of();
		}
		final List<TrackLayout.PlatformBand> all = site.street() != null ? site.bridgeBands(layout) : layout.platforms();
		// a hub's outer platforms run past its head with the through tracks; their end ramps stay their step-free way on
		if (spec.hub() || !spec.footbridge() && !spec.underpass() && !site.building() && !layout.terminus()) {
			return all;
		}
		return all.stream().filter(band -> !betweenTracks(band)).toList();
	}

	/** A track runs somewhere on each side of the band. */
	final boolean betweenTracks(TrackLayout.PlatformBand band) {
		return layout.tracks().stream().anyMatch(t -> t.u() < band.uMin()) && layout.tracks().stream().anyMatch(t -> t.u() > band.uMax());
	}

	void ramps() {
		for (final TrackLayout.PlatformBand band : rampedBands()) {
			// the steps down from a street entrance get a parapet each side: a cheek wall level with the entrance and a rail
			// on it, so their open sides (onto the ground and the track) are guarded like a real station's entrance steps
			if (site.streets().contains(band) && band.uMax() - band.uMin() + 1 >= 5) {
				final List<Integer> rows = new ArrayList<>(List.of(band.vFrom() - 2, band.vFrom() - 1));
				if (!layout.terminus()) {
					rows.addAll(List.of(band.vTo() + 1, band.vTo() + 2));
				}
				for (final int u : new int[]{band.uMin(), band.uMax()}) {
					final LocalDir outward = u == band.uMin() ? LocalDir.MINUS_U : LocalDir.PLUS_U;
					for (final int v : rows) {
						put(Role.WALL, u, StationGeometry.PLATFORM_Y, v, outward, PlanBridges.PLATEAU_WALL);
						put(Role.RAILING, u, StationGeometry.WALK_Y, v, outward);
					}
				}
			}
			for (int u = band.uMin(); u <= band.uMax(); u++) {
				put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, band.vFrom() - 2, LocalDir.MINUS_V);
				put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, band.vFrom() - 1, LocalDir.MINUS_V);
				if (!layout.terminus()) { // a terminus's platforms run level into its concourse
					put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, band.vTo() + 1, LocalDir.PLUS_V);
					put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, band.vTo() + 2, LocalDir.PLUS_V);
				}
			}
		}
	}

	/**
	 * A handrail across each end of every platform that has no ramp at that end, edge cells too, so nobody walks off the end
	 * onto the tracks: on the platform's last row of floor (past its run, the floor goes on to the buffer stops), wherever
	 * there is a drop beyond it. A terminus's platforms run on into its concourse at the head end and stay open there. Planned
	 * last, so it only takes cells nothing else wanted.
	 */
	void endFences() {
		// a deep station's platforms end in the bore's walls, or open into its concourse
		if (spec.options().deepStation()) {
			return;
		}
		final Set<TrackLayout.PlatformBand> ramped = new HashSet<>(rampedBands());
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			for (int u = band.uMin(); u <= band.uMax(); u++) {
				for (final int step : new int[]{-1, 1}) {
					// a ramp's end stays open (a terminus's platforms have one only at the far end)
					if (ramped.contains(band) && (step < 0 || !layout.terminus())) {
						continue;
					}
					// along the floor to its last row inside the platforms' zone; floor going on past the zone (a terminus's
					// concourse) leaves the end open
					int v = step < 0 ? band.vFrom() : band.vTo();
					while (v + step >= layout.vMin() && v + step < layout.platformLength() && floorAt(u, v + step)) {
						v += step;
					}
					if (!floorAt(u, v + step) && at(u, StationGeometry.PLATFORM_Y, v + step) == null) {
						put(Role.RAILING, u, StationGeometry.WALK_Y, v, step < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V);
					}
				}
			}
		}
	}

	/**
	 * The fence behind each buffer stop carried on sideways across the pit, past the track's clearance, wherever the pit
	 * beside the buffer is open and floor stands behind it (between two buffered tracks, nothing else closes that gap).
	 * Planned once every floor is down.
	 */
	void pitEndFences() {
		for (final TrackLayout.Track track : layout.tracks()) {
			if (!layout.buffered(track)) {
				continue;
			}
			final int buffer = layout.trackEnd(track) + 1;
			for (final int step : new int[]{-1, 1}) {
				int u = track.u() + step * (StationGeometry.ENVELOPE_HALF_WIDTH + 1);
				while (at(u, StationGeometry.PLATFORM_Y, buffer) == null && floorAt(u, buffer + 1)
						&& put(Role.RAILING, u, StationGeometry.WALK_Y, buffer + 1, LocalDir.PLUS_V, OVERRUN_FENCE)) {
					u += step;
				}
			}
		}
	}

	/** True when a platform's floor (or the floor it runs on into) is at {@code (u, PLATFORM_Y, v)}. */
	private boolean floorAt(int u, int v) {
		final PlannedBlock block = at(u, StationGeometry.PLATFORM_Y, v);
		return block != null && block.role().flatFloor();
	}

	void railNodes() {
		if (!spec.railNodes()) {
			return;
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, layout.platformFrom(track), LocalDir.PLUS_V);
			put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, layout.platformTo(track), LocalDir.PLUS_V);
			// a dead end's last node, at the end of its overrun before the buffer stop: the build lays a turn-back rail to it
			if (layout.buffered(track) && layout.trackEnd(track) > layout.platformTo(track)) {
				put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, layout.trackEnd(track), LocalDir.PLUS_V);
			}
		}
	}

	/**
	 * One solid layer at y = -1 under the whole railway: ballast under every track's clearance for its whole run and across
	 * the gaps between neighbouring tracks, with a lit strip in the row at the foot of each platform wall along that
	 * platform, and black concrete under every platform floor and ramp, so the platforms stand on something and the bed
	 * has no holes.
	 */
	/**
	 * A tram stop's street: the street track block (to lay the MTR rail on) under each track's centre and dark paving across
	 * its envelope, the whole run of the stop, as on a hub's forecourt.
	 */
	void streetTrack() {
		for (final TrackLayout.Track track : layout.tracks()) {
			for (int v = layout.vMin(); v <= layout.trackEnd(track); v++) {
				for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
					if (!taken(u, -1, v)) {
						put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, u == track.u() ? Planning.TRAM_TRACK : Planning.LOT_MARK);
					}
				}
			}
		}
		// and the street between neighbouring tracks
		for (final Box gap : bedGaps()) {
			for (int u = gap.uMin(); u <= gap.uMax(); u++) {
				for (int v = gap.vMin(); v <= gap.vMax(); v++) {
					if (!taken(u, -1, v)) {
						put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, Planning.LOT_MARK);
					}
				}
			}
		}
	}

	/** A tram stop's sign at each end of each platform, by the edge the tram stops at. */
	void tramSigns() {
		if (!spec.tram() || spec.hub()) {
			return;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final int u = band.trackLeft() ? band.uMin() + 1 : band.uMax() - 1;
			for (final int v : new int[]{band.vFrom() + 1, band.vTo() - 1}) {
				if (!taken(u, StationGeometry.WALK_Y, v)) {
					put(Role.FIXTURE, u, StationGeometry.WALK_Y, v, band.trackLeft() ? LocalDir.MINUS_U : LocalDir.PLUS_U, Planning.TRAM_STOP_SIGN);
				}
			}
		}
	}

	void trackBed() {
		if (spec.tram() && !spec.hub()) {
			streetTrack();
			return;
		}
		final TrackBed bed = spec.trackBed();
		if (bed == TrackBed.NONE) {
			return;
		}
		for (final PlannedBlock block : List.copyOf(cells.values())) {
			if (block.y() == StationGeometry.PLATFORM_Y && (block.role().platformFloor() || block.role() == Role.RAMP_LOWER || block.role() == Role.RAMP_UPPER)) {
				put(Role.TRACK_BED, block.u(), -1, block.v(), LocalDir.PLUS_V, PLATFORM_FILL);
			}
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			for (final int side : new int[]{-1, 1}) {
				if (side < 0 ? track.platformLeft() : track.platformRight()) {
					for (int v = layout.platformFrom(track); v <= layout.platformTo(track); v++) {
						// the channel against the wall, under the platform's lip
						put(Role.TRACK_BED, track.u() + side, -1, v, side < 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U, bed.light());
					}
				}
			}
			for (int v = layout.vMin(); v <= layout.trackEnd(track); v++) {
				for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
					put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, bed.blockId());
				}
			}
		}
		for (final Box gap : bedGaps()) {
			for (int u = gap.uMin(); u <= gap.uMax(); u++) {
				for (int v = gap.vMin(); v <= gap.vMax(); v++) {
					put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, bed.blockId());
				}
			}
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			for (final int side : new int[]{-1, 1}) {
				if (!cessSide(track, side)) {
					continue;
				}
				// past the sleeper ends: the ballast shoulder, then the cess, a walkway of concrete cable-trough lids
				final int shoulder = track.u() + side * (StationGeometry.ENVELOPE_HALF_WIDTH + 1);
				for (int v = layout.vMin(); v <= layout.trackEnd(track); v++) {
					put(Role.TRACK_BED, shoulder, -1, v, LocalDir.PLUS_V, bed.blockId());
					put(Role.TRACK_BED, shoulder + side, -1, v, LocalDir.PLUS_V, CESS_PATH);
				}
			}
		}
	}

	/** The cess walkway beside an outer track: concrete trough lids. */
	static final String CESS_PATH = "minecraft:smooth_stone";

	/** True when the layout left room for a shoulder and cess on this side of the track (an open outer side). */
	boolean cessSide(TrackLayout.Track track, int side) {
		if (StationGeometry.cess(spec) == 0 || (side < 0 ? track.platformLeft() : track.platformRight())) {
			return false;
		}
		final int u0 = track.u() + side * (StationGeometry.ENVELOPE_HALF_WIDTH + 1);
		final int u1 = track.u() + side * (StationGeometry.ENVELOPE_HALF_WIDTH + StationGeometry.CESS);
		if (Math.min(u0, u1) < 0 || Math.max(u0, u1) >= layout.width()) {
			return false;
		}
		// nothing else in those columns: no other track's clearance, no platform, no street
		for (final TrackLayout.Track other : layout.tracks()) {
			if (other != track && Math.abs(other.u() - u0) <= StationGeometry.ENVELOPE_HALF_WIDTH + StationGeometry.CESS) {
				return false;
			}
		}
		final List<TrackLayout.PlatformBand> bands = new ArrayList<>(layout.platforms());
		bands.addAll(site.streets());
		return bands.stream().noneMatch(band -> band.uMax() >= Math.min(u0, u1) && band.uMin() <= Math.max(u0, u1));
	}

	/**
	 * The strips between two neighbouring tracks' clearances where no platform stands (y = -1), as far as both tracks run (to
	 * their buffer stops): ballast fills them so the bed is one surface from wall to wall.
	 */
	List<Box> bedGaps() {
		final List<TrackLayout.Track> tracks = new ArrayList<>(layout.tracks());
		tracks.sort(Comparator.comparingInt(TrackLayout.Track::u));
		final List<Box> gaps = new ArrayList<>();
		for (int i = 0; i + 1 < tracks.size(); i++) {
			final TrackLayout.Track a = tracks.get(i);
			final TrackLayout.Track b = tracks.get(i + 1);
			final int u0 = a.u() + StationGeometry.ENVELOPE_HALF_WIDTH + 1;
			final int u1 = b.u() - StationGeometry.ENVELOPE_HALF_WIDTH - 1;
			if (u0 > u1 || layout.platforms().stream().anyMatch(band -> band.uMin() <= u1 && band.uMax() >= u0)) {
				continue;
			}
			gaps.add(new Box(u0, -1, layout.vMin(), u1, -1, Math.min(bedEnd(a), bedEnd(b))));
		}
		return gaps;
	}

	/** The last row of ballast along a track: its end, or the buffer stop's row past it ({@link #bayEnds}). */
	private int bedEnd(TrackLayout.Track track) {
		return layout.trackEnd(track) + (layout.buffered(track) ? 1 : 0);
	}

	/**
	 * One buffer stop across a track's clearance at row v, its front towards the train (-v): the driver's left end at the lower
	 * u, the right end at the higher, the cells between the middle.
	 */
	void bufferStop(TrackLayout.Track track, int y, int v) {
		final int u0 = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH;
		final int u1 = track.u() + StationGeometry.ENVELOPE_HALF_WIDTH;
		for (int u = u0; u <= u1; u++) {
			final String part = u0 == u1 ? "single" : u == u0 ? "left" : u == u1 ? "right" : "middle";
			put(Role.BUFFER_STOP, u, y, v, LocalDir.MINUS_V, palette.blockId(Role.BUFFER_STOP), "part=" + part);
		}
	}

	/**
	 * Each dead-end track's end: past its platform the track runs on {@link StationGeometry#overrun} rows to its last rail
	 * node, then a buffer stop across its clearance at rail level, on ballast, and a fence across the row behind it when
	 * the station goes on. Level floor carries the platforms on beside the overruns (as a terminus's concourse meets its
	 * platform ends); {@link #overrunFences} fences it along the pits.
	 */
	void bayEnds() {
		final TrackBed bed = spec.trackBed();
		for (final TrackLayout.Track track : layout.tracks()) {
			if (!layout.buffered(track)) {
				continue;
			}
			final int buffer = layout.trackEnd(track) + 1;
			if (bed != TrackBed.NONE) {
				for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
					put(Role.TRACK_BED, u, -1, buffer, LocalDir.PLUS_V, bed.blockId());
				}
			}
			bufferStop(track, StationGeometry.PLATFORM_Y, buffer);
			// the fence across the end, behind the buffer, when the station goes on past it
			for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; buffer < layout.vMax() && u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
				put(Role.RAILING, u, StationGeometry.WALK_Y, buffer + 1, LocalDir.PLUS_V, OVERRUN_FENCE);
			}
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			overrunFloor(band);
		}
	}

	/**
	 * The last row of the floor carrying a platform on past its end: to the last buffer of the buffered tracks along it,
	 * and at a terminus to the head (a hub's platforms between through tracks too); -1 if it stops at its end.
	 */
	int overrunTo(TrackLayout.PlatformBand band) {
		int to = -1;
		for (final TrackLayout.Track track : layout.tracks()) {
			final boolean beside = track.u() == band.uMin() - StationGeometry.EDGE_OFFSET || track.u() == band.uMax() + StationGeometry.EDGE_OFFSET;
			if (beside && layout.buffered(track)) {
				to = Math.max(to, layout.trackEnd(track) + 1);
			}
		}
		if (layout.terminus() && band.number() != 0) {
			to = Math.max(to, layout.platformLength() - 1);
		}
		return to;
	}

	/**
	 * The floor carrying a platform on past its end (see {@link #overrunTo}): the platform's own paving from side to side
	 * (Jah, 10-21: one platform floor, not concourse tile between paved edges), without the edge line that marks where to
	 * board. {@link #overrunFences} fences the sides that face a track once the footbridges are planned.
	 */
	private void overrunFloor(TrackLayout.PlatformBand band) {
		final int to = overrunTo(band);
		for (int v = band.vTo() + 1; v <= to; v++) {
			for (int u = band.uMin(); u <= band.uMax(); u++) {
				final boolean left = u == band.uMin() && band.trackLeft();
				final boolean right = u == band.uMax() && band.trackRight();
				put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, left ? LocalDir.MINUS_U : right ? LocalDir.PLUS_U : LocalDir.PLUS_V);
			}
		}
	}

	/**
	 * The fence along each side of the floor past a platform's end that faces a track, so nobody steps down beside an
	 * overrun or a through track. Only where two cells stay free between the fences and a row of roof columns (a narrower
	 * floor keeps its paving open, as at the platform beside it), and from two rows past the last thing standing in the
	 * middle of the floor (a footbridge stair and its walls), so the lanes round it still lead on.
	 */
	void overrunFences() {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final int to = overrunTo(band);
			final int sides = (band.trackLeft() ? 1 : 0) + (band.trackRight() ? 1 : 0);
			final RoofType roof = band.number() == 0 ? RoofType.NONE : StationGeometry.roof(spec, band.number());
			final int columns = roof == RoofType.NONE ? 0 : StationPlanner.columnLines(band, roof).size();
			if (to < 0 || sides == 0 || band.uMax() - band.uMin() + 1 - sides - columns < 2) {
				continue;
			}
			int from = band.vTo() + 1;
			for (int v = band.vTo() + 1; v <= to; v++) {
				for (int u = band.uMin() + 1; u < band.uMax(); u++) {
					if (taken(u, StationGeometry.WALK_Y, v)) {
						from = v + 2;
					}
				}
			}
			for (int v = from; v <= to; v++) {
				if (band.trackLeft() && !taken(band.uMin(), StationGeometry.WALK_Y, v)) {
					put(Role.RAILING, band.uMin(), StationGeometry.WALK_Y, v, LocalDir.MINUS_U, OVERRUN_FENCE);
				}
				if (band.trackRight() && !taken(band.uMax(), StationGeometry.WALK_Y, v)) {
					put(Role.RAILING, band.uMax(), StationGeometry.WALK_Y, v, LocalDir.PLUS_U, OVERRUN_FENCE);
				}
			}
		}
	}

	/** The fence round a track's overrun: station fencing, which stops players like any railing (1.5 blocks). */
	static final String OVERRUN_FENCE = "aurelia_transit_architecture:station_fence";

	/**
	 * True for a row of a building's walls that carries windows: tall windows through the ground storey (y = 2..5), and two
	 * rows in the middle of the first floor and of every storey above it, with a solid band at each floor and under the roof.
	 */
	static boolean windowRow(int y) {
		if (y < StationGeometry.UPPER_FLOOR_Y) {
			return y >= 2 && y <= StationGeometry.UPPER_FLOOR_Y - 2;
		}
		if (y < StationGeometry.BUILDING_ROOF_Y) {
			return y >= StationGeometry.UPPER_WALK_Y + 1 && y <= StationGeometry.BUILDING_ROOF_Y - 2;
		}
		final int k = Math.floorMod(y - StationGeometry.BUILDING_ROOF_Y, StationGeometry.STOREY);
		return k == 2 || k == 3;
	}

	/** True when v is the centre of a facade bay. */
	boolean isBayCentre(int v) {
		if (!site.building()) {
			return false;
		}
		for (int bay = 0; bay < site.bays(); bay++) {
			if (site.bayCentre(bay) == v) {
				return true;
			}
		}
		return false;
	}

	/** True when a platform runs along the building's platform wall, so a doorway in it leads onto that platform. */
	boolean platformBesideBuilding() {
		return layout.platforms().stream().anyMatch(band -> band.uMin() == layout.buildingWidth());
	}

	/**
	 * A doorway three wide centred on {@code centre} in the platform wall at {@code uP}; where a track rather than a
	 * platform runs along that wall, a window instead, so nobody can step out onto the track.
	 */
	void platformDoorway(int uP, int centre, String window) {
		for (int v = centre - 1; v <= centre + 1; v++) {
			if (platformBesideBuilding()) {
				for (int y = 1; y <= 3; y++) {
					keepClear(uP, y, v);
				}
				doorV.add(v);
			} else {
				for (int y = 2; y <= 3; y++) {
					put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, window);
				}
			}
		}
	}

	/**
	 * Planned last, on cells nothing else wanted. The ballast shoulder beside an open outer track is raised half a block, level
	 * with the sleeper tops, like a real ballast profile; the open side along a street entrance gets its shoulder and cess
	 * wherever the street, its steps and its lift leave room; and past each end of an island the ground between its two tracks
	 * is ballast, instead of grass running up to the platform's end wall.
	 */
	void shoulders() {
		final TrackBed bed = spec.trackBed();
		if (bed == TrackBed.NONE || StationGeometry.cess(spec) == 0) {
			return;
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			for (final int side : new int[]{-1, 1}) {
				if (side < 0 ? track.platformLeft() : track.platformRight()) {
					continue;
				}
				final int shoulder = track.u() + side * (StationGeometry.ENVELOPE_HALF_WIDTH + 1);
				final boolean streetSide = side < 0 && site.street() != null && !cessSide(track, side) && layout.buildingWidth() == 0
						&& layout.tracks().stream().noneMatch(other -> other.u() < track.u()) && shoulder - 1 >= 0;
				for (int v = layout.vMin(); v <= layout.trackEnd(track); v++) {
					if (streetSide && freeColumn(shoulder, v) && freeColumn(shoulder - 1, v)) {
						put(Role.TRACK_BED, shoulder, -1, v, LocalDir.PLUS_V, bed.blockId());
						put(Role.TRACK_BED, shoulder - 1, -1, v, LocalDir.PLUS_V, CESS_PATH);
						extraSupport.add(new Box(shoulder, -2, v, shoulder, -2, v));
					}
					final PlannedBlock under = at(shoulder, -1, v);
					if (under != null && under.role() == Role.TRACK_BED && under.blockId().equals(bed.blockId()) && free(shoulder, 0, v)
							&& free(shoulder, 1, v)) {
						put(Role.TRACK_BED, shoulder, 0, v, LocalDir.PLUS_V, bed.shoulder());
					}
				}
			}
		}
		final Set<TrackLayout.PlatformBand> ramped = new HashSet<>(rampedBands());
		final int bedTo = layout.tracks().stream().mapToInt(this::bedEnd).min().orElse(-1);
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (ramped.contains(band) || !betweenTracks(band)) {
				continue;
			}
			for (int v = layout.vMin(); v <= bedTo; v++) {
				if (v >= band.vFrom() && v <= band.vTo()) {
					continue;
				}
				for (int u = band.uMin(); u <= band.uMax(); u++) {
					if (freeColumn(u, v)) {
						put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, bed.blockId());
						extraSupport.add(new Box(u, -2, v, u, -2, v));
					}
				}
			}
		}
	}

	/** Nothing planned or kept clear at the cell. */
	boolean free(int u, int y, int v) {
		final Cell cell = new Cell(u, y, v);
		return !cells.containsKey(cell) && !air.contains(cell);
	}

	/** Nothing planned or kept clear from the bed up to head height at that column and row. */
	private boolean freeColumn(int u, int v) {
		for (int y = -1; y <= StationGeometry.WALK_Y + 1; y++) {
			if (!free(u, y, v)) {
				return false;
			}
		}
		return true;
	}

	/** Rows between the overhead line's masts: two canopy bays. */
	static final int MAST_SPACING = 2 * StationGeometry.COLUMN_SPACING;
	private static final int[] MAST_SHIFTS = {0, -2, 2, -4, 4, -6, 6};
	/** The contact wire's cell: the wire runs mid-block, about 5.5 m over the rail, just over the track clearance. */
	static final int WIRE_Y = StationGeometry.ENVELOPE_HEIGHT;
	static final String MAST = "aurelia_transit_architecture:catenary_mast";
	/** The messenger's deepest sag mid-span, as the contact wire's kind number (ContactWireKind.MAX_LEVEL). */
	static final int WIRE_SAG_LEVELS = 6;
	/** The cantilever from a mast to the hanger over its track: root at the mast, spans, tip over the wire. */
	static final String ARM = "aurelia_transit_architecture:catenary_arm";
	static final String WIRE = "aurelia_transit_architecture:contact_wire";
	static final String LINESIDE_FENCE = "aurelia_transit_architecture:lineside_fence";
	static final String NOISE_BARRIER = "aurelia_transit_architecture:noise_barrier";

	/** A station in the open, where lineside masts and fences stand: not under a hall, in a box, in the street or at a hub. */
	boolean lineside() {
		return spec.roof() != RoofType.HALL && !spec.hub() && !spec.options().underground() && !spec.tram()
				&& StationGeometry.boxMargin(spec) == 0 && !layout.tracks().isEmpty();
	}

	/**
	 * The overhead line: a mast every {@link #MAST_SPACING} rows on each open outer side, on the ballast shoulder inside the
	 * lineside fence (and between two tracks with no
	 * platform between them), an arm across to the track's centre at the top, a hanger at its end, and the contact wire along
	 * every track a mast reaches. Planned after everything else, on free cells only, so a canopy or a stair is never moved
	 * for it; a mast row with no room is left out.
	 */
	void overheadLine() {
		if (!spec.features().overheadLine() || !lineside()) {
			return;
		}
		final Map<TrackLayout.Track, TreeSet<Integer>> wired = new HashMap<>();
		for (final TrackLayout.Track track : layout.tracks()) {
			for (final int side : new int[]{-1, 1}) {
				if ((side < 0 ? track.platformLeft() : track.platformRight())
						|| layout.tracks().stream().anyMatch(other -> other != track && Integer.signum(other.u() - track.u()) == side)) {
					continue;
				}
				// on the ballast shoulder where the track has a cess, so the fence or noise screen on the cess's outer edge runs
				// on unbroken behind it (a mast in the fence line broke the run: Jah 11-09); else at the edge of the layout
				final int mast = track.u() + side * (StationGeometry.ENVELOPE_HALF_WIDTH + (cessSide(track, side) ? 1 : StationGeometry.CESS));
				if (mast < 0 || mast >= layout.width()) {
					continue;
				}
				for (final int row : mastRows(track)) {
					// a mast row with no room (a bridge's stairs, a lift) shifts a few rows along rather than leaving a long span
					for (final int shift : MAST_SHIFTS) {
						final int v = row + shift;
						if (v >= layout.vMin() && v <= layout.trackEnd(track) && mastFits(mast, v, List.of(track.u()))) {
							mast(mast, v, List.of(track.u()));
							wired.computeIfAbsent(track, t -> new TreeSet<>()).add(v);
							break;
						}
					}
				}
			}
		}
		// a mast between two neighbouring tracks with nothing between them carries an arm to each
		final List<TrackLayout.Track> sorted = new ArrayList<>(layout.tracks());
		sorted.sort(Comparator.comparingInt(TrackLayout.Track::u));
		for (int i = 0; i + 1 < sorted.size(); i++) {
			final TrackLayout.Track a = sorted.get(i);
			final TrackLayout.Track b = sorted.get(i + 1);
			if (a.platformRight() || b.platformLeft() || b.u() - a.u() < 2 * StationGeometry.ENVELOPE_HALF_WIDTH + 3) {
				continue;
			}
			final int mast = (a.u() + b.u()) / 2;
			for (final int v : mastRows(a.u() < b.u() && layout.trackEnd(a) < layout.trackEnd(b) ? a : b)) {
				if (mastFits(mast, v, List.of(a.u(), b.u()))) {
					mast(mast, v, List.of(a.u(), b.u()));
					wired.computeIfAbsent(a, t -> new TreeSet<>()).add(v);
					wired.computeIfAbsent(b, t -> new TreeSet<>()).add(v);
				}
			}
		}
		wired.forEach(this::contactWire);
	}

	/**
	 * The wires along a track from its first mast to its last, so no end hangs in the air: the messenger sags towards the
	 * middle of each span, and droppers hang the contact wire from it at even spacing, one at every mast.
	 */
	private void contactWire(TrackLayout.Track track, TreeSet<Integer> masts) {
		if (masts.size() < 2) {
			return;
		}
		for (int v = masts.first(); v <= masts.last(); v++) {
			final int from = masts.floor(v);
			final Integer next = masts.higher(v);
			final int to = next == null ? from : next;
			final int span = to - from;
			final int at = v - from;
			final double x = span == 0 ? 0 : 2.0 * at / span - 1;
			final int level = (int) Math.round(WIRE_SAG_LEVELS * (1 - x * x));
			final int droppers = Math.max(2, Math.round(span / 3f));
			boolean dropper = at == 0;
			for (int i = 1; i < droppers && !dropper; i++) {
				dropper = at == Math.round((float) i * span / droppers);
			}
			if (free(track.u(), WIRE_Y, v)) {
				put(Role.FIXTURE, track.u(), WIRE_Y, v, LocalDir.PLUS_V, WIRE, "kind=" + (dropper ? "dropper_" : "level_") + level);
			}
		}
	}

	/** The rows a track's masts stand on: two in from its start, every {@link #MAST_SPACING}, and two in from its end. */
	private List<Integer> mastRows(TrackLayout.Track track) {
		final List<Integer> rows = new ArrayList<>();
		final int last = layout.trackEnd(track) - 2;
		int v = layout.vMin() + 2;
		for (; v <= last; v += MAST_SPACING) {
			rows.add(v);
		}
		if (!rows.isEmpty() && last - rows.get(rows.size() - 1) > MAST_SPACING / 2) {
			rows.add(last);
		}
		return rows;
	}

	/** Room for a mast at {@code (u, v)} up to the arm, the arm across to each track and its hanger. */
	private boolean mastFits(int u, int v, List<Integer> tracks) {
		if (mastBase(u, v) < 0) {
			return false;
		}
		for (int y = mastBase(u, v); y <= WIRE_Y + 1; y++) {
			if (!free(u, y, v)) {
				return false;
			}
		}
		for (final int t : tracks) {
			for (int c = Math.min(u, t); c <= Math.max(u, t); c++) {
				if (c != u && !free(c, WIRE_Y + 1, v)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The lowest cell of a mast at {@code (u, v)}: on the ground, or on a platform's paving or the ballast shoulder; -1 when
	 * none of those is there.
	 */
	private int mastBase(int u, int v) {
		if (free(u, StationGeometry.PLATFORM_Y, v)) {
			return StationGeometry.PLATFORM_Y;
		}
		final PlannedBlock floor = at(u, StationGeometry.PLATFORM_Y, v);
		return floor != null && (floor.role() == Role.PLATFORM_SURFACE || floor.role() == Role.TRACK_BED) ? StationGeometry.WALK_Y : -1;
	}

	private void mast(int u, int v, List<Integer> tracks) {
		final int base = mastBase(u, v);
		for (int y = base; y <= WIRE_Y + 1; y++) {
			put(Role.FIXTURE, u, y, v, LocalDir.PLUS_U, MAST);
		}
		if (base == StationGeometry.PLATFORM_Y) {
			extraSupport.add(new Box(u, -1, v, u, -1, v));
		}
		// a cantilever from the mast to each track: the root joins the mast's flange, the tip hangs the wire
		for (final int t : tracks) {
			final int step = Integer.signum(t - u);
			final LocalDir toTrack = step > 0 ? LocalDir.PLUS_U : LocalDir.MINUS_U;
			for (int c = u + step; c != t + step; c += step) {
				final String kind = c == t ? "tip" : c == u + step ? "root" : "span";
				put(Role.FIXTURE, c, WIRE_Y + 1, v, toTrack, ARM, "kind=" + kind);
			}
		}
	}

	/** Near a street entrance's steps, where people walk down onto the ground: no mast or fence in their way. */
	private boolean nearStreet(int u, int v) {
		for (final TrackLayout.PlatformBand street : site.streets()) {
			if (u >= street.uMin() - 1 && u <= street.uMax() + 1 && v >= street.vFrom() - 6 && v <= street.vTo() + 6) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A lineside fence along the outer edge of each cess, so the railway is closed off from the land beside it, with a gap
	 * where people come down from a street entrance. Its mesh panel is on the cess block's outer edge, so the cess stays
	 * walkable.
	 */
	void linesideFence() {
		if (!spec.features().fence() || !lineside() || StationGeometry.cess(spec) == 0) {
			return;
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			for (final int side : new int[]{-1, 1}) {
				final int u = track.u() + side * (StationGeometry.ENVELOPE_HALF_WIDTH + StationGeometry.CESS);
				// a Dutch line runs between noise screens, except on the side a street entrance opens onto
				final int s = side;
				final boolean screen = spec.style() == StationStyle.DUTCH && site.streets().stream()
						.noneMatch(street -> s < 0 ? street.uMax() < track.u() : street.uMin() > track.u());
				for (int v = layout.vMin(); v <= layout.trackEnd(track); v++) {
					final PlannedBlock cess = at(u, -1, v);
					if (cess != null && CESS_PATH.equals(cess.blockId()) && free(u, StationGeometry.PLATFORM_Y, v) && free(u, StationGeometry.WALK_Y, v)
							&& !nearStreet(u, v)) {
						final LocalDir out = side < 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U;
						if (screen && free(u, StationGeometry.WALK_Y + 1, v)) {
							// an absorbent base with glass above it, on the cess's outer edge like the fence
							put(Role.FIXTURE, u, StationGeometry.PLATFORM_Y, v, out, NOISE_BARRIER, "kind=solid");
							put(Role.FIXTURE, u, StationGeometry.WALK_Y, v, out, NOISE_BARRIER, "kind=glass");
							put(Role.FIXTURE, u, StationGeometry.WALK_Y + 1, v, out, NOISE_BARRIER, "kind=glass");
						} else {
							put(Role.RAILING, u, StationGeometry.PLATFORM_Y, v, out, LINESIDE_FENCE);
						}
					}
				}
			}
		}
	}

	/**
	 * What makes a street entrance's steps read as a way into the station: the operator's name pylon at the foot of each flight,
	 * a paved landing there where the station lays its own ground (a track bed), and two check-in poles at the top of the
	 * flight. Planned late, on free cells only.
	 */
	void streetEntranceFittings() {
		for (final TrackLayout.PlatformBand street : site.streets()) {
			if (site.plateau(street)) {
				continue;
			}
			final int outer = street.trackRight() ? street.uMin() : street.uMax();
			boolean checkIn = false;
			// sealed only where the steps end on the station's own landing (a cess): with no bed the ground at their foot is the
			// world's, left alone, and a walkway closed along its side might have no way on
			final boolean seal = !spec.options().openFareLine() && StationGeometry.cess(spec) > 0;
			for (final int dir : layout.terminus() ? new int[]{-1} : new int[]{-1, 1}) {
				final int top = dir < 0 ? street.vFrom() : street.vTo();
				final PlannedBlock step = at(street.uMin() + 1, StationGeometry.PLATFORM_Y, top + dir);
				if (step == null || step.role() != Role.RAMP_UPPER && step.role() != Role.RAMP_LOWER) {
					continue;
				}
				final int foot = top + 3 * dir;
				final LocalDir out = dir < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
				if (StationGeometry.cess(spec) > 0) {
					for (int v = foot; v != foot + 3 * dir; v += dir) {
						for (int u = street.uMin(); u <= street.uMax(); u++) {
							if (inFootprint(v) && free(u, -1, v) && free(u, StationGeometry.PLATFORM_Y, v)) {
								put(Role.FLOOR, u, -1, v, out, Planning.LOT_PAVING);
							}
						}
					}
				}
				// the pylon faces across the landing, like the ticket machine beside it: facing the street it looked straight into
				// the machine's side (Jah 11-09)
				final LocalDir across = outer == street.uMin() ? LocalDir.PLUS_U : LocalDir.MINUS_U;
				if (inFootprint(foot) && free(outer, StationGeometry.PLATFORM_Y, foot) && free(outer, StationGeometry.WALK_Y, foot)) {
					putText(Role.FIXTURE, outer, StationGeometry.PLATFORM_Y, foot, across, PlanUnderground.PYLON, "NONE:" + spec.name());
					put(Role.FIXTURE, outer, StationGeometry.WALK_Y, foot, across, PlanUnderground.PYLON, "half=upper");
				}
				if (inFootprint(foot + dir)) {
					ticketMachine(outer, StationGeometry.PLATFORM_Y, foot + dir, outer == street.uMin() ? LocalDir.PLUS_U : LocalDir.MINUS_U);
				}
				for (final int u : new int[]{street.uMin() + 1, street.uMax() - 1}) {
					if (free(u, StationGeometry.WALK_Y, top) && free(u, StationGeometry.WALK_Y + 1, top)) {
						put(Role.FIXTURE, u, StationGeometry.WALK_Y, top, out, CARD_READER);
						checkIn = true;
					}
				}
				// the way past the poles: a check-in lane in every open cell of the row, shut until the passenger taps in (Jah
				// 11-09: tapping is required, nobody gets past without it); an open fare line leaves the row open
				if (seal) {
					for (int u = street.uMin(); u <= street.uMax(); u++) {
						final PlannedBlock floor = at(u, StationGeometry.PLATFORM_Y, top);
						if (floor != null && floor.role().platformFloor() && free(u, StationGeometry.WALK_Y, top) && free(u, StationGeometry.WALK_Y + 1, top)) {
							put(Role.GATE, u, StationGeometry.WALK_Y, top, out, CHECK_IN_LANE);
						}
					}
				}
			}
			if (checkIn && seal) {
				// glass along the walkway's open side, so the check-in poles are the only way on (Jah 11-09: "nobody gets in around
				// the readers by any means; close the open sides with glass")
				final LocalDir outward = outer == street.uMin() ? LocalDir.MINUS_U : LocalDir.PLUS_U;
				for (int v = street.vFrom() + 1; v < street.vTo(); v++) {
					final PlannedBlock floor = at(outer, StationGeometry.PLATFORM_Y, v);
					if (floor != null && floor.role().platformFloor() && free(outer, StationGeometry.WALK_Y, v)) {
						put(Role.BARRIER, outer, StationGeometry.WALK_Y, v, outward, Planning.GLASS_BARRIER, "edge=true");
					}
				}
			}
		}
	}

	private boolean inFootprint(int v) {
		return v >= layout.vMin() && v <= layout.vMax();
	}

	/** An NS-style check-in pole: tap in and out at the way onto the platforms. */
	static final String CARD_READER = "aurelia_transit_architecture:card_reader";
	/** The way past a row of check-in poles, shut until the passenger taps in. */
	static final String CHECK_IN_LANE = "aurelia_transit_architecture:check_in_lane";
}
