package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A track drawn by hand in the station editor: a line of points in the plan (u across the tracks, v along them) that starts
 * at one of the station's track ends and runs out from it, laid as real MTR rails between the points when the station is
 * built. In words it reads {@code track path 4/-2 4/-12 10/-40}; the parser takes it out of the description and keeps it in
 * {@link StationOptions#drawn()}.
 *
 * <p>The station's own platform rails must stay straight (#65): a drawn track may only leave a track end straight along the
 * track, for at least {@link #LEAD} blocks, and never cross back into the station. {@link #check} says what is wrong with one;
 * only tracks that pass are laid. No Minecraft classes.
 */
public record DrawnTrack(List<Point> points) {

	/** A point in the plan: u across the tracks, v along them (the plan's own cells). */
	public record Point(int u, int v) {
	}

	/**
	 * One MTR rail between two points of a drawn track, in world blocks. {@code dir1} is the way it leaves the first point and
	 * {@code dir2} the way it leaves the second (back along the rail), each in sixteenths of a turn from east, clockwise seen
	 * from above (0 east, 4 south, 8 west, 12 north), as MTR's 16 rail angles are.
	 */
	public record Leg(int x1, int y1, int z1, int dir1, int x2, int y2, int z2, int dir2) {
	}

	public static final int MAX_TRACKS = 8;
	public static final int MAX_POINTS = 24;
	/** How far from the station a point may be, in blocks: as far as a plan reaches. */
	public static final int MAX_REACH = 512;
	/** The straight run a drawn track keeps from the track end before it may turn, so the platform rail stays straight. */
	public static final int LEAD = 6;
	/** The shortest rail between two points. */
	public static final int MIN_STEP = 4;
	/** The sharpest turn at one point, in degrees: MTR fits one curve between two points, which a hairpin breaks. */
	public static final double MAX_TURN = 50;
	/** How far a first point snaps to a track end. */
	public static final int SNAP = 6;

	private static final Pattern WORDS = Pattern.compile("(?i)\\btrack path((?:\\s+-?\\d{1,4}/-?\\d{1,4})+)");
	private static final Pattern POINT = Pattern.compile("(-?\\d{1,4})/(-?\\d{1,4})");

	public DrawnTrack {
		points = List.copyOf(points);
	}

	/** This track in words: {@code track path u/v u/v ...}. */
	public String words() {
		final StringBuilder out = new StringBuilder("track path");
		for (final Point point : points) {
			out.append(' ').append(point.u()).append('/').append(point.v());
		}
		return out.toString();
	}

	/** Several tracks in words, joined with commas; empty for none. */
	public static String words(List<DrawnTrack> tracks) {
		final List<String> parts = new ArrayList<>();
		for (final DrawnTrack track : tracks) {
			if (track.points().size() >= 2) {
				parts.add(track.words());
			}
		}
		return String.join(", ", parts);
	}

	/** The drawn tracks in {@code text}, in order, at most {@link #MAX_TRACKS} with at most {@link #MAX_POINTS} points each. */
	public static List<DrawnTrack> parse(String text) {
		final List<DrawnTrack> tracks = new ArrayList<>();
		if (text == null) {
			return tracks;
		}
		final Matcher matcher = WORDS.matcher(text);
		while (matcher.find() && tracks.size() < MAX_TRACKS) {
			final List<Point> points = new ArrayList<>();
			final Matcher point = POINT.matcher(matcher.group(1));
			while (point.find() && points.size() < MAX_POINTS) {
				final int u = Integer.parseInt(point.group(1));
				final int v = Integer.parseInt(point.group(2));
				if (Math.abs(u) <= MAX_REACH && Math.abs(v) <= MAX_REACH) {
					points.add(new Point(u, v));
				}
			}
			if (points.size() >= 2) {
				tracks.add(new DrawnTrack(points));
			}
		}
		return tracks;
	}

	/** {@code text} without its drawn tracks (each replaced by a space), for the rest of the parser. */
	public static String strip(String text) {
		return WORDS.matcher(text).replaceAll(" ");
	}

	// ---- geometry ---------------------------------------------------------------------------------------------------

	/** The track ends of a plan a drawn track may start from: per track, its outermost rail node at each end. */
	public static List<Point> anchors(StationPlan plan) {
		final java.util.Map<Integer, int[]> ends = new java.util.TreeMap<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == Role.RAIL_NODE) {
				final int[] range = ends.computeIfAbsent(block.u(), u -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
				range[0] = Math.min(range[0], block.v());
				range[1] = Math.max(range[1], block.v());
			}
		}
		final List<Point> anchors = new ArrayList<>();
		for (final java.util.Map.Entry<Integer, int[]> entry : ends.entrySet()) {
			anchors.add(new Point(entry.getKey(), entry.getValue()[0]));
			if (entry.getValue()[1] != entry.getValue()[0]) {
				anchors.add(new Point(entry.getKey(), entry.getValue()[1]));
			}
		}
		return anchors;
	}

	/** The rail level of a plan's track ends (local y of its rail nodes), or {@link StationGeometry#PLATFORM_Y} without any. */
	public static int railY(StationPlan plan) {
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == Role.RAIL_NODE) {
				return block.y();
			}
		}
		return StationGeometry.PLATFORM_Y;
	}

	/** The track end nearest {@code p} within {@link #SNAP} blocks, or null. */
	public static Point snap(Point p, List<Point> anchors) {
		Point best = null;
		double bestDistance = SNAP + 0.5;
		for (final Point anchor : anchors) {
			final double distance = Math.hypot(anchor.u() - p.u(), anchor.v() - p.v());
			if (distance < bestDistance) {
				best = anchor;
				bestDistance = distance;
			}
		}
		return best;
	}

	/**
	 * The end of the straight lead out of track end {@code start}: {@code distance} blocks on along its track, away from the
	 * track's other end; null for a track with one end.
	 */
	public static Point lead(Point start, List<Point> anchors, int distance) {
		for (final Point other : anchors) {
			if (other.u() == start.u() && other.v() != start.v()) {
				return new Point(start.u(), start.v() + (start.v() > other.v() ? distance : -distance));
			}
		}
		return null;
	}

	/** The station's cells in the plan, {uMin, uMax, vMin, vMax}, from its blocks. */
	static int[] footprint(StationPlan plan) {
		final int[] box = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
		for (final PlannedBlock block : plan.blocks()) {
			box[0] = Math.min(box[0], block.u());
			box[1] = Math.max(box[1], block.u());
			box[2] = Math.min(box[2], block.v());
			box[3] = Math.max(box[3], block.v());
		}
		return box;
	}

	/**
	 * What keeps this track from being laid, or null when it can be: it must start on a track end, run straight out along that
	 * track for {@link #LEAD} blocks, stay out of the station after that, take no step shorter than {@link #MIN_STEP} and no
	 * turn sharper than {@link #MAX_TURN} degrees.
	 */
	public String check(StationPlan plan) {
		return check(anchors(plan), footprint(plan));
	}

	/** {@link #check(StationPlan)} with the plan's track ends and its cells {uMin, uMax, vMin, vMax} given. */
	public String check(List<Point> anchors, int[] box) {
		if (points.size() < 2) {
			return "a drawn track needs two points";
		}
		final Point start = points.get(0);
		if (!anchors.contains(start)) {
			return "a drawn track must start on a track end (" + start.u() + "/" + start.v() + " is not one)";
		}
		final Point lead = points.get(1);
		final int out = outward(start, anchors, box);
		if (lead.u() != start.u() || (lead.v() - start.v()) * out < LEAD) {
			return "a drawn track must leave track end " + start.u() + "/" + start.v() + " straight along the track for " + LEAD
					+ " blocks, so the platform rail stays straight";
		}
		for (int i = 1; i < points.size(); i++) {
			final Point a = points.get(i - 1);
			final Point b = points.get(i);
			if (Math.hypot(b.u() - a.u(), b.v() - a.v()) < MIN_STEP) {
				return "points " + i + " and " + (i + 1) + " are closer than " + MIN_STEP + " blocks";
			}
			// past the straight lead (which runs along the track's own line), every cell must be outside the station
			final int steps = i == 1 ? -1 : Math.max(Math.abs(b.u() - a.u()), Math.abs(b.v() - a.v()));
			for (int s = 0; s <= steps; s++) {
				final int u = a.u() + Math.round((float) (b.u() - a.u()) * s / steps);
				final int v = a.v() + Math.round((float) (b.v() - a.v()) * s / steps);
				if (u >= box[0] && u <= box[1] && v > box[2] && v < box[3]) {
					return "point " + (i + 1) + " takes the drawn track back into the station";
				}
			}
			if (i >= 2) {
				final Point before = points.get(i - 2);
				if (turn(before, a, b) > MAX_TURN) {
					return "the turn at point " + i + " is sharper than " + (int) MAX_TURN + " degrees";
				}
			}
		}
		return null;
	}

	/**
	 * The way out of the station from track end {@code start} along v: +1 from a track's high end, -1 from its low end (a track
	 * with one node: towards the nearer end of the station).
	 */
	private static int outward(Point start, List<Point> anchors, int[] box) {
		for (final Point other : anchors) {
			if (other.u() == start.u() && other.v() != start.v()) {
				return start.v() > other.v() ? 1 : -1;
			}
		}
		return start.v() - box[2] >= box[3] - start.v() ? 1 : -1;
	}

	private static double turn(Point a, Point b, Point c) {
		final double in = Math.atan2(b.v() - a.v(), b.u() - a.u());
		final double out = Math.atan2(c.v() - b.v(), c.u() - b.u());
		double degrees = Math.abs(Math.toDegrees(out - in)) % 360;
		return degrees > 180 ? 360 - degrees : degrees;
	}

	/**
	 * The MTR rails of this track built in {@code frame} at local rail level {@code y}: one between each two points. The way a
	 * rail leaves a point follows the line through the points either side of it (the track end's way out at the start), turned
	 * to the nearest of MTR's 16 angles.
	 */
	public List<Leg> legs(LocalFrame frame, int y) {
		final int n = points.size();
		final int[] x = new int[n];
		final int[] z = new int[n];
		for (int i = 0; i < n; i++) {
			x[i] = frame.x(points.get(i).u(), points.get(i).v());
			z[i] = frame.z(points.get(i).u(), points.get(i).v());
		}
		final int[] dir = new int[n];
		for (int i = 0; i < n; i++) {
			final int from = i == 0 ? 0 : i == n - 1 ? n - 2 : i - 1;
			final int to = i == 0 ? 1 : i == n - 1 ? n - 1 : i + 1;
			dir[i] = compass(x[to] - x[from], z[to] - z[from]);
		}
		final int worldY = frame.y(y);
		final List<Leg> legs = new ArrayList<>();
		for (int i = 0; i + 1 < n; i++) {
			legs.add(new Leg(x[i], worldY, z[i], dir[i], x[i + 1], worldY, z[i + 1], (dir[i + 1] + 8) % 16));
		}
		return legs;
	}

	/** The nearest of 16 compass steps to the way (dx, dz) points: 0 east, 4 south (+z), 8 west, 12 north. */
	static int compass(int dx, int dz) {
		final double degrees = Math.toDegrees(Math.atan2(dz, dx));
		return Math.floorMod((int) Math.round(degrees / 22.5), 16);
	}

	/** The rails of every drawn track of {@code plan} that passes {@link #check}, built in {@code frame}. */
	public static List<Leg> legs(StationPlan plan, LocalFrame frame) {
		final List<Leg> legs = new ArrayList<>();
		final List<Point> anchors = anchors(plan);
		final int[] box = footprint(plan);
		final int y = railY(plan);
		for (final DrawnTrack track : plan.spec().options().drawn()) {
			if (track.check(anchors, box) == null) {
				legs.addAll(track.legs(frame, y));
			}
		}
		return legs;
	}
}
