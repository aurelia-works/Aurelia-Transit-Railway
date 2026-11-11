package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A platform or building outline drawn by hand in the station editor: a closed line of points in the plan (u across the
 * tracks, v along them). In words it reads {@code platform shape 2/4 12/4 12/40 2/40} (with {@code open} for no canopy) or
 * {@code building shape 20/0 34/0 34/16 20/16 height 8}; the parser takes it out of the description and keeps it in
 * {@link StationOptions#shapes()}. An airport's shapes are an apron, a taxiway, a runway or a terminal
 * ({@code runway shape 0/0 30/0 30/400 0/400}), filled by {@link PlanFreehand}.
 *
 * <p>Shapes are a layer of their own ({@link PlanFreehand}), planned after the station: they fill only the cells nothing else
 * took, so a station without shapes plans exactly as before. The cells a shape covers are those whose centre lies inside the
 * outline or on it. No Minecraft classes.
 *
 * @param open   a platform with no canopy (lamp posts instead of canopy lights); ignored for a building
 * @param height a building's wall height in blocks, from the floor to under its roof; 0 for a platform
 */
public record DrawnShape(Kind kind, List<DrawnTrack.Point> points, boolean open, int height) {

	/** What a shape is filled with. */
	public enum Kind {
		/** Platform floor, a yellow edge where it meets a track, a canopy on columns (or lamp posts) and benches. */
		PLATFORM,
		/** Floor, walls with windows and a doorway, a flat roof with light panels in it. */
		BUILDING,
		/** An airport's concrete apron, in slabs, with floodlight masts. */
		APRON,
		/** An airport's asphalt taxiway, with yellow edge lines and blue edge lights. */
		TAXIWAY,
		/** An airport's black runway, with white edge lines and white edge lights. */
		RUNWAY,
		/** An airport terminal: glass walls, a doorway and a flat roof with light panels. */
		TERMINAL;

		/** The word the shape is written with. */
		public String word() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** True for the kinds an airport draws; the others are a railway station's. */
		public boolean airport() {
			return this == APRON || this == TAXIWAY || this == RUNWAY || this == TERMINAL;
		}

		/** True for the kinds with walls, whose words carry a height. */
		public boolean walled() {
			return this == BUILDING || this == TERMINAL;
		}

		/** The widest a shape of this kind may be, in blocks, either way. */
		public int span() {
			return airport() ? AIRPORT_SPAN : MAX_SPAN;
		}

		/** How far past the station's (or airport's) cells a shape of this kind may reach, in blocks. */
		public int reach() {
			return airport() ? AIRPORT_REACH : REACH;
		}
	}

	public static final int MAX_SHAPES = 8;
	public static final int MAX_POINTS = 32;
	/** The widest a shape may be, in blocks, either way. */
	public static final int MAX_SPAN = 96;
	/** How far past the station a shape's cells may reach, in blocks: about what the editor's plan shows round it. */
	public static final int REACH = 64;
	/** The widest an airport's shape may be: a runway of a game-scale airport. */
	public static final int AIRPORT_SPAN = 480;
	/** How far past the airport an apron, taxiway, runway or terminal may reach. */
	public static final int AIRPORT_REACH = 160;
	/** The fewest cells a shape may cover. */
	public static final int MIN_CELLS = 6;
	public static final int MIN_HEIGHT = 4;
	public static final int MAX_HEIGHT = 16;
	public static final int DEFAULT_HEIGHT = 6;

	private static final Pattern WORDS = Pattern.compile(
			"(?i)\\b(platform|building|apron|taxiway|runway|terminal) shape((?:\\s+-?\\d{1,4}/-?\\d{1,4})+)(\\s+open\\b)?(?:\\s+height\\s+(\\d{1,2})\\b)?");
	private static final Pattern POINT = Pattern.compile("(-?\\d{1,4})/(-?\\d{1,4})");

	public DrawnShape {
		points = List.copyOf(points);
		height = kind.walled() ? Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height == 0 ? DEFAULT_HEIGHT : height)) : 0;
		open = kind == Kind.PLATFORM && open;
	}

	/** This shape in words. */
	public String words() {
		final StringBuilder out = new StringBuilder(kind.word()).append(" shape");
		for (final DrawnTrack.Point point : points) {
			out.append(' ').append(point.u()).append('/').append(point.v());
		}
		if (open) {
			out.append(" open");
		}
		if (kind.walled()) {
			out.append(" height ").append(height);
		}
		return out.toString();
	}

	/** Several shapes in words, joined with commas; empty for none. */
	public static String words(List<DrawnShape> shapes) {
		final List<String> parts = new ArrayList<>();
		for (final DrawnShape shape : shapes) {
			if (shape.points().size() >= 3) {
				parts.add(shape.words());
			}
		}
		return String.join(", ", parts);
	}

	/** The drawn shapes in {@code text}, in order, at most {@link #MAX_SHAPES} with at most {@link #MAX_POINTS} points each. */
	public static List<DrawnShape> parse(String text) {
		final List<DrawnShape> shapes = new ArrayList<>();
		if (text == null) {
			return shapes;
		}
		final Matcher matcher = WORDS.matcher(text);
		while (matcher.find() && shapes.size() < MAX_SHAPES) {
			final Kind kind = Kind.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
			final List<DrawnTrack.Point> points = new ArrayList<>();
			final Matcher point = POINT.matcher(matcher.group(2));
			while (point.find() && points.size() < MAX_POINTS) {
				final int u = Integer.parseInt(point.group(1));
				final int v = Integer.parseInt(point.group(2));
				if (Math.abs(u) <= DrawnTrack.MAX_REACH && Math.abs(v) <= DrawnTrack.MAX_REACH) {
					points.add(new DrawnTrack.Point(u, v));
				}
			}
			final int height = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
			if (points.size() >= 3) {
				shapes.add(new DrawnShape(kind, points, matcher.group(3) != null, height));
			}
		}
		return shapes;
	}

	/** {@code text} without its drawn shapes (each replaced by a space), for the rest of the parser. */
	public static String strip(String text) {
		return WORDS.matcher(text).replaceAll(" ");
	}

	// ---- geometry ---------------------------------------------------------------------------------------------------

	/** The cell key of (u, v), as {@link #cells} and {@link #cellsOf} hold them. */
	public static long key(int u, int v) {
		return ((long) u << 32) ^ (v & 0xFFFFFFFFL);
	}

	public static int u(long key) {
		return (int) (key >> 32);
	}

	public static int v(long key) {
		return (int) key;
	}

	/** {uMin, uMax, vMin, vMax} of the outline's points. */
	public int[] bounds() {
		final int[] box = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
		for (final DrawnTrack.Point p : points) {
			box[0] = Math.min(box[0], p.u());
			box[1] = Math.max(box[1], p.u());
			box[2] = Math.min(box[2], p.v());
			box[3] = Math.max(box[3], p.v());
		}
		return box;
	}

	/** The cells the shape covers, in v then u order: every cell whose centre is inside the closed outline or on it. */
	public Set<Long> cells() {
		final Set<Long> cells = new LinkedHashSet<>();
		if (points.size() < 3) {
			return cells;
		}
		final int[] box = bounds();
		if (box[1] - box[0] > kind.span() || box[3] - box[2] > kind.span()) {
			return cells;
		}
		for (int v = box[2]; v <= box[3]; v++) {
			for (int u = box[0]; u <= box[1]; u++) {
				if (onOutline(u, v) || inside(u, v)) {
					cells.add(key(u, v));
				}
			}
		}
		return cells;
	}

	/** Every cell any of {@code shapes} covers. */
	public static Set<Long> cellsOf(List<DrawnShape> shapes) {
		final Set<Long> all = new HashSet<>();
		for (final DrawnShape shape : shapes) {
			all.addAll(shape.cells());
		}
		return all;
	}

	/**
	 * What keeps this shape from being built, or null when it can be: three points or more, no wider than {@link #MAX_SPAN},
	 * at least {@link #MIN_CELLS} cells, and within {@link #REACH} blocks of the station's cells {@code station} = {uMin, uMax,
	 * vMin, vMax}.
	 */
	public String check(int[] station) {
		if (points.size() < 3) {
			return "a drawn " + kind.word() + " needs three points";
		}
		final int[] box = bounds();
		if (box[1] - box[0] > kind.span() || box[3] - box[2] > kind.span()) {
			return "a drawn " + kind.word() + " may be at most " + kind.span() + " blocks across";
		}
		if (cells().size() < MIN_CELLS) {
			return "a drawn " + kind.word() + " needs at least " + MIN_CELLS + " blocks inside it";
		}
		final int reach = kind.reach();
		if (station[0] <= station[1] && (box[0] < station[0] - reach || box[1] > station[1] + reach || box[2] < station[2] - reach
				|| box[3] > station[3] + reach)) {
			return "a drawn " + kind.word() + " must stay within " + reach + " blocks of the " + (kind.airport() ? "airport" : "station");
		}
		return null;
	}

	/** True when cell (u, v) lies on one of the outline's sides (the closing side included). */
	private boolean onOutline(int u, int v) {
		final int n = points.size();
		for (int i = 0; i < n; i++) {
			final DrawnTrack.Point a = points.get(i);
			final DrawnTrack.Point b = points.get((i + 1) % n);
			final long cross = (long) (b.u() - a.u()) * (v - a.v()) - (long) (b.v() - a.v()) * (u - a.u());
			final long length = Math.max(Math.abs(b.u() - a.u()), Math.abs(b.v() - a.v()));
			// within about half a cell of the side's line (measured across its longer axis, as a drawn line is), between its ends
			if (length == 0 ? u == a.u() && v == a.v()
					: 2 * Math.abs(cross) <= length && u >= Math.min(a.u(), b.u()) && u <= Math.max(a.u(), b.u()) && v >= Math.min(a.v(), b.v()) && v <= Math.max(a.v(), b.v())) {
				return true;
			}
		}
		return false;
	}

	/** True when the centre of cell (u, v) is inside the closed outline (even-odd rule). */
	private boolean inside(int u, int v) {
		boolean in = false;
		final int n = points.size();
		for (int i = 0, j = n - 1; i < n; j = i++) {
			final DrawnTrack.Point a = points.get(i);
			final DrawnTrack.Point b = points.get(j);
			if ((a.v() > v) != (b.v() > v)) {
				final double crossU = a.u() + (double) (v - a.v()) * (b.u() - a.u()) / (b.v() - a.v());
				if (u < crossU) {
					in = !in;
				}
			}
		}
		return in;
	}
}
