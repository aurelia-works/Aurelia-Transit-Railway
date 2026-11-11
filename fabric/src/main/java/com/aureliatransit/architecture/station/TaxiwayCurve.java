package com.aureliatransit.architecture.station;

import com.aureliatransit.architecture.station.AirportGeometry.Area;
import com.aureliatransit.architecture.station.AirportRoute.Cell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The quarter-circle where a taxiway meets a runway. The square link stays where it is; this rounds the corner the plane
 * actually flies, and fills the grass the rectangle left outside that curve. One radius for the whole field, so the painted
 * line and the rail are the same arc. Below 2 the old straight link is kept: a compact corner has no room to bend.
 */
public final class TaxiwayCurve {
	private TaxiwayCurve() {
	}

	/** Longest centreline radius the route will emit. A drawn join can be larger; the route's own cap is the taxiway width. */
	static final int MAX_MATCH = 80;

	private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private static final Map<AirportGeometry, Set<Long>> EXTRA = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Integer, List<Cell>> RELATIVE = new HashMap<>();

	/**
	 * Centreline radius in cells, or 0 when a fillet would eat the straight throat or a stand's siding. The inner edge is then
	 * about half a taxiway wide. The outer bulge stays short of the runway end.
	 */
	public static int radius(AirportGeometry g) {
		final int taxi = g.dims().taxi();
		final int half = taxi / 2;
		final int span = g.runwayCentre(0) - g.taxiCentre();
		int r = taxi;
		r = Math.min(r, span / 2 - 1);
		r = Math.min(r, g.dims().linkInset() + half);
		if (!g.standV().isEmpty() && g.links().size() >= 2) {
			final int pitch = g.dims().standPitch();
			int low = Integer.MAX_VALUE;
			int high = Integer.MIN_VALUE;
			for (final int stand : g.standV()) {
				final int siding = stand + pitch / 2;
				low = Math.min(low, siding);
				high = Math.max(high, siding);
			}
			final int m0 = centre(g.links().get(0));
			final int m1 = centre(g.links().get(1));
			r = Math.min(r, low - m0 - 2);
			r = Math.min(r, m1 - high - 2);
		}
		return r >= 2 ? r : 0;
	}

	/** Take-off link: off the taxiway, across the throat, onto the runway. The shared tangent cell is not repeated. */
	public static List<Cell> ontoRunway(int tc, int rc, int m, int r) {
		final List<Cell> out = new ArrayList<>();
		out.addAll(arc(new Cell(tc, m + r), 0, -1, r));
		for (int u = tc + r + 1; u <= rc - r - 1; u++) {
			out.add(new Cell(u, m));
		}
		out.addAll(arc(new Cell(rc - r, m), 1, 0, r));
		return out;
	}

	/** Exit link: off the runway, back across the throat, onto the taxiway. */
	public static List<Cell> offRunway(int tc, int rc, int m, int r) {
		final List<Cell> out = new ArrayList<>();
		out.addAll(arc(new Cell(rc, m - r), 0, 1, r));
		for (int u = rc - r - 1; u >= tc + r + 1; u--) {
			out.add(new Cell(u, m));
		}
		out.addAll(arc(new Cell(tc + r, m), -1, 0, r));
		return out;
	}

	/**
	 * True when row {@code v} is beside a fillet, so the taxiway's runway-side edge line has to break. The apron-side line stays:
	 * the curve opens toward the runway only.
	 */
	public static boolean opensEdge(AirportGeometry g, int v) {
		final int r = radius(g);
		if (r < 2) {
			return false;
		}
		final int reach = r + g.dims().taxi() / 2;
		for (final int[] link : g.links()) {
			if (Math.abs(v - centre(link)) <= reach) {
				return true;
			}
		}
		return false;
	}

	/** Cells of every fillet centreline at one link, throat included, runway columns left out by the caller. */
	public static List<Cell> centreline(AirportGeometry g, int[] link) {
		final List<Cell> out = new ArrayList<>();
		final Set<Long> seen = new HashSet<>();
		for (final List<Cell> line : polylines(g, link)) {
			for (final Cell cell : line) {
				if (seen.add(key(cell.u(), cell.v()))) {
					out.add(cell);
				}
			}
		}
		return out;
	}

	/**
	 * The holding-position bar across the centreline cell nearest {@link AirportGeometry#holdU()}. Two solid lines on the runway
	 * side, a gap, two dashed lines toward the taxiway. On the straight throat this is the old bar.
	 */
	public static List<Cell> hold(AirportGeometry g, int[] link) {
		final int r = radius(g);
		if (r < 2) {
			return List.of();
		}
		final int m = centre(link);
		final int holdU = g.holdU();
		Cell best = null;
		int bestDist = Integer.MAX_VALUE;
		int bestSu = 0;
		int bestSv = 0;
		boolean bestAcross = false;
		for (final List<Cell> line : polylines(g, link)) {
			for (int i = 0; i < line.size(); i++) {
				final Cell cell = line.get(i);
				int su = 0;
				int sv = 0;
				if (i + 1 < line.size() && cell.adjacent(line.get(i + 1))) {
					su = Integer.signum(line.get(i + 1).u() - cell.u());
					sv = Integer.signum(line.get(i + 1).v() - cell.v());
				} else if (i > 0 && cell.adjacent(line.get(i - 1))) {
					su = Integer.signum(cell.u() - line.get(i - 1).u());
					sv = Integer.signum(cell.v() - line.get(i - 1).v());
				}
				final int dist = Math.abs(cell.u() - holdU);
				final boolean across = Math.abs(su) > Math.abs(sv);
				if (best == null || dist < bestDist || (dist == bestDist && across && !bestAcross)) {
					best = cell;
					bestDist = dist;
					bestSu = su;
					bestSv = sv;
					bestAcross = across;
				}
			}
		}
		if (best == null) {
			return List.of();
		}
		if (bestSu == 0 && bestSv == 0) {
			bestSu = 1;
		}
		return bar(best, bestSu, bestSv, m, g.dims().taxi() / 2, g.width(), g.length());
	}

	/** Fillet cells the square taxiway, link, runway, shoulder and blast pad do not already cover. */
	public static Set<Long> extraCells(AirportGeometry g) {
		final Set<Long> hit = EXTRA.get(g);
		if (hit != null) {
			return hit;
		}
		final Set<Long> built = buildExtra(g);
		EXTRA.put(g, built);
		return built;
	}

	/** Extra cells with a side still on the grass: the new pavement edge. */
	public static List<Cell> boundary(AirportGeometry g) {
		final Set<Long> extra = extraCells(g);
		final List<Integer> exits = g.rapidExits();
		final List<Cell> out = new ArrayList<>();
		for (final long cell : extra) {
			final int u = u(cell);
			final int v = v(cell);
			if (!paved(g, extra, exits, u + 1, v) || !paved(g, extra, exits, u - 1, v) || !paved(g, extra, exits, u, v + 1)
					|| !paved(g, extra, exits, u, v - 1)) {
				out.add(new Cell(u, v));
			}
		}
		return out;
	}

	/** One-row taxiway areas for {@link AirportGeometry#areas()}, so pavement, support and headroom follow the curve. */
	public static List<Area> pavement(AirportGeometry g) {
		final Map<Integer, List<Integer>> rows = new HashMap<>();
		for (final long cell : extraCells(g)) {
			final int v = v(cell);
			List<Integer> us = rows.get(v);
			if (us == null) {
				us = new ArrayList<>();
				rows.put(v, us);
			}
			us.add(u(cell));
		}
		final List<Area> out = new ArrayList<>();
		for (final Map.Entry<Integer, List<Integer>> row : rows.entrySet()) {
			final List<Integer> us = row.getValue();
			Collections.sort(us);
			final int v = row.getKey();
			int start = us.get(0);
			int prev = start;
			for (int i = 1; i < us.size(); i++) {
				final int u = us.get(i);
				if (u > prev + 1) {
					out.add(strip(start, prev, v));
					start = u;
				}
				prev = u;
			}
			out.add(strip(start, prev, v));
		}
		return out;
	}

	/**
	 * How many cells of a quarter-arc begin at {@code cs[i]}, or 0. The longest match wins. A one-step gate or siding corner is
	 * not an arc, so those rails stay a single step.
	 */
	public static int arcCells(List<Cell> cs, int i) {
		if (i + 1 >= cs.size()) {
			return 0;
		}
		final int du = Integer.signum(cs.get(i + 1).u() - cs.get(i).u());
		final int dv = Integer.signum(cs.get(i + 1).v() - cs.get(i).v());
		if (Math.abs(du) + Math.abs(dv) != 1) {
			return 0;
		}
		int best = 0;
		for (int r = 2; r <= MAX_MATCH; r++) {
			final List<Cell> arc = relative(du, dv, r);
			if (i + arc.size() > cs.size()) {
				break;
			}
			final Cell end = arc.get(arc.size() - 1);
			final Cell at = cs.get(i + arc.size() - 1);
			if (at.u() - cs.get(i).u() != end.u() || at.v() - cs.get(i).v() != end.v()) {
				continue;
			}
			boolean same = true;
			for (int k = 0; k < arc.size(); k++) {
				if (cs.get(i + k).u() - cs.get(i).u() != arc.get(k).u() || cs.get(i + k).v() - cs.get(i).v() != arc.get(k).v()) {
					same = false;
					break;
				}
			}
			if (same) {
				best = arc.size();
			}
		}
		return best;
	}

	/**
	 * A curve for a drawn taxiway that meets a drawn runway at a right angle, both solid rectangles. Anything else (an L, two
	 * parallel strips, a shape the station already cut into) is skipped: this is the same quarter-arc, not a second generator.
	 */
	public static List<DrawnJoin> drawn(List<Set<Long>> taxis, List<Set<Long>> runways) {
		final List<DrawnJoin> out = new ArrayList<>();
		for (final Set<Long> taxi : taxis) {
			final int[] tb = bounds(taxi);
			if (tb == null || !solid(taxi, tb) || span(tb, true) == span(tb, false)) {
				continue;
			}
			final boolean taxiAlongV = span(tb, true) > span(tb, false);
			for (final Set<Long> runway : runways) {
				final int[] rb = bounds(runway);
				if (rb == null || !solid(runway, rb) || span(rb, true) == span(rb, false)) {
					continue;
				}
				final boolean runAlongV = span(rb, true) > span(rb, false);
				if (taxiAlongV == runAlongV) {
					continue;
				}
				final int gapU = gap(tb[0], tb[1], rb[0], rb[1]);
				final int gapV = gap(tb[2], tb[3], rb[2], rb[3]);
				final boolean meetV = gapV >= 0 && gapV <= 1 && overlap(tb[0], tb[1], rb[0], rb[1]) >= 2;
				final boolean meetU = gapU >= 0 && gapU <= 1 && overlap(tb[2], tb[3], rb[2], rb[3]) >= 2;
				if (meetV == meetU) {
					continue;
				}
				// The taxiway is the stem: its long axis points at the runway. A runway butting into the side of a taxiway is left.
				if (meetV && taxiAlongV) {
					addJoins(true, taxi, tb, runway, rb, out);
				} else if (meetU && !taxiAlongV) {
					addJoins(false, taxi, tb, runway, rb, out);
				}
			}
		}
		return out;
	}

	/** One drawn fillet: the arc, the pavement it adds, and the straight centreline cells that arc replaces. */
	public record DrawnJoin(List<Cell> arc, List<Cell> extra, List<Cell> suppress) {
	}

	public static long key(int u, int v) {
		return DrawnShape.key(u, v);
	}

	/**
	 * Quarter-arc from {@code start}, first step {@code (du, dv)}, turning left. A Bresenham quadrant stops short of the far
	 * tangent, so the step is the one that keeps moving around the circle and still lands on the end cell.
	 */
	public static List<Cell> arc(Cell start, int du, int dv, int r) {
		final int lu = -dv;
		final int lv = du;
		final int ou = start.u() + lu * r;
		final int ov = start.v() + lv * r;
		final int eu = ou + du * r;
		final int ev = ov + dv * r;
		final List<Cell> path = new ArrayList<>();
		path.add(start);
		int u = start.u() + du;
		int v = start.v() + dv;
		path.add(new Cell(u, v));
		final double startAng = Math.atan2(start.v() - ov, start.u() - ou);
		final double goal = ccw(startAng, Math.atan2(ev - ov, eu - ou));
		int guard = 0;
		while (u != eu || v != ev) {
			final double now = ccw(startAng, Math.atan2(v - ov, u - ou));
			int bu = u;
			int bv = v;
			double best = Double.MAX_VALUE;
			boolean found = false;
			for (final int[] step : STEPS) {
				final int nu = u + step[0];
				final int nv = v + step[1];
				final double prog = ccw(startAng, Math.atan2(nv - ov, nu - ou));
				if (prog + 1e-6 < now) {
					continue;
				}
				if (prog > goal + 0.35 && (nu != eu || nv != ev)) {
					continue;
				}
				final double err = Math.abs(Math.hypot(nu - ou, nv - ov) - r);
				double score = err - 0.25 * (prog - now);
				if (nu == eu && nv == ev) {
					score -= 5;
				}
				if (!found || score < best) {
					found = true;
					best = score;
					bu = nu;
					bv = nv;
				}
			}
			if (!found || guard++ > r * 6) {
				break;
			}
			u = bu;
			v = bv;
			path.add(new Cell(u, v));
		}
		return path;
	}

	private static List<List<Cell>> polylines(AirportGeometry g, int[] link) {
		final int r = radius(g);
		final List<List<Cell>> out = new ArrayList<>();
		if (r < 2) {
			return out;
		}
		final int m = centre(link);
		final int tc = g.taxiCentre();
		final int rc = g.runwayCentre(0);
		final List<Cell> taxiHigh = arc(new Cell(tc, m + r), 0, -1, r);
		final List<Cell> runHigh = arc(new Cell(rc - r, m), 1, 0, r);
		out.add(taxiHigh);
		out.add(mirrorV(taxiHigh, m));
		out.add(runHigh);
		out.add(mirrorV(runHigh, m));
		final List<Cell> throat = new ArrayList<>();
		for (int u = tc + r + 1; u <= rc - r - 1; u++) {
			throat.add(new Cell(u, m));
		}
		out.add(throat);
		return out;
	}

	private static List<Cell> mirrorV(List<Cell> cells, int m) {
		final List<Cell> out = new ArrayList<>();
		for (final Cell cell : cells) {
			out.add(new Cell(cell.u(), 2 * m - cell.v()));
		}
		return out;
	}

	private static List<Cell> bar(Cell at, int su, int sv, int m, int half, int width, int height) {
		final int pu = -sv;
		final int pv = su == 0 && sv == 0 ? 1 : su;
		final int tu;
		final int tv;
		if (pv != 0 || pu == 0) {
			tu = 1;
			tv = 0;
		} else if (at.v() >= m) {
			tu = 0;
			tv = -1;
		} else {
			tu = 0;
			tv = 1;
		}
		final int alongU = pu == 0 && pv == 0 ? 0 : pu;
		final int alongV = pu == 0 && pv == 0 ? 1 : pv;
		final List<Cell> out = new ArrayList<>();
		for (int along = -half; along <= half; along++) {
			final int bu = at.u() + along * alongU;
			final int bv = at.v() + along * alongV;
			add(out, bu, bv, width, height);
			add(out, bu - tu, bv - tv, width, height);
			if (Math.floorMod(along + half, 3) < 2) {
				add(out, bu - 3 * tu, bv - 3 * tv, width, height);
				add(out, bu - 4 * tu, bv - 4 * tv, width, height);
			}
		}
		return out;
	}

	private static void add(List<Cell> out, int u, int v, int width, int height) {
		if (u >= 0 && v >= 0 && u < width && v < height) {
			out.add(new Cell(u, v));
		}
	}

	private static Set<Long> buildExtra(AirportGeometry g) {
		final Set<Long> extra = new HashSet<>();
		final int r = radius(g);
		if (r < 2) {
			return extra;
		}
		final int half = g.dims().taxi() / 2;
		final int half2 = half * half;
		final List<Integer> exits = g.rapidExits();
		for (final int[] link : g.links()) {
			for (final List<Cell> line : polylines(g, link)) {
				for (final Cell cell : line) {
					for (int dv = -half; dv <= half; dv++) {
						for (int du = -half; du <= half; du++) {
							if (du * du + dv * dv > half2) {
								continue;
							}
							final int u = cell.u() + du;
							final int v = cell.v() + dv;
							if (!taken(g, u, v, exits)) {
								extra.add(key(u, v));
							}
						}
					}
				}
			}
		}
		return extra;
	}

	/** True when something already paves (u, v), or it sits outside the gap the fillet is allowed to fill. */
	private static boolean taken(AirportGeometry g, int u, int v, List<Integer> exits) {
		if (u < 0 || v < 0 || u >= g.width() || v >= g.length()) {
			return true;
		}
		if (u >= g.taxiU0() && u <= g.taxiU1() && v >= g.taxiV0() && v <= g.taxiV1()) {
			return true;
		}
		final int gapU0 = g.taxiU1() + 1;
		final int gapU1 = g.runwayU0().get(0) - 1;
		for (final int[] link : g.links()) {
			if (u >= gapU0 && u <= gapU1 && v >= link[0] && v <= link[1]) {
				return true;
			}
		}
		for (int i = 0; i < g.spec().runways(); i++) {
			final int ru0 = g.runwayU0().get(i);
			final int ru1 = g.runwayU1(i);
			if (u >= ru0 && u <= ru1 && v >= g.runwayV0() && v <= g.runwayV1()) {
				return true;
			}
			final int shoulder = g.dims().shoulder();
			if (shoulder > 0 && v >= g.runwayV0() && v <= g.runwayV1()
					&& ((u >= ru0 - shoulder && u < ru0) || (u > ru1 && u <= ru1 + shoulder))) {
				return true;
			}
			final int blast = g.dims().blast();
			if (u >= ru0 && u <= ru1 && ((v >= g.runwayV0() - blast && v < g.runwayV0())
					|| (v > g.runwayV1() && v <= g.runwayV1() + blast))) {
				return true;
			}
		}
		if (g.spec().turnPads() && (g.turnPad(false).contains(u, v) || g.turnPad(true).contains(u, v))) {
			return true;
		}
		if (u >= gapU0 && u <= gapU1) {
			final int half = g.exitHalf();
			for (final int start : exits) {
				final int c = g.exitCentre(start, u);
				if (v >= c - half && v <= c + half) {
					return true;
				}
			}
		}
		return u < gapU0 || u > gapU1;
	}

	private static boolean paved(AirportGeometry g, Set<Long> extra, List<Integer> exits, int u, int v) {
		return extra.contains(key(u, v)) || taken(g, u, v, exits);
	}

	private static Area strip(int u0, int u1, int v) {
		return new Area(AirportGeometry.Kind.TAXIWAY, u0, u1, v, v, AirportGeometry.OPEN_CLEARANCE);
	}

	private static synchronized List<Cell> relative(int du, int dv, int r) {
		final int id = ((du + 1) << 16) ^ ((dv + 1) << 8) ^ r;
		final List<Cell> hit = RELATIVE.get(id);
		if (hit != null) {
			return hit;
		}
		final List<Cell> built = arc(new Cell(0, 0), du, dv, r);
		RELATIVE.put(id, built);
		return built;
	}

	private static void addJoins(boolean approachIsV, Set<Long> taxi, int[] tb, Set<Long> runway, int[] rb, List<DrawnJoin> out) {
		final int taxiA0 = approachIsV ? tb[2] : tb[0];
		final int taxiA1 = approachIsV ? tb[3] : tb[1];
		final int taxiC0 = approachIsV ? tb[0] : tb[2];
		final int taxiC1 = approachIsV ? tb[1] : tb[3];
		final int runA0 = approachIsV ? rb[2] : rb[0];
		final int runA1 = approachIsV ? rb[3] : rb[1];
		final int runC0 = approachIsV ? rb[0] : rb[2];
		final int runC1 = approachIsV ? rb[1] : rb[3];
		final boolean stemLow = taxiA1 < runA0;
		if (!stemLow && taxiA0 <= runA1) {
			return;
		}
		final int sign = stemLow ? 1 : -1;
		final int near = stemLow ? taxiA1 : taxiA0;
		final int far = stemLow ? taxiA0 : taxiA1;
		final int runA = (runA0 + runA1) / 2;
		final int taxiC = (taxiC0 + taxiC1) / 2;
		final int taxiW = taxiC1 - taxiC0 + 1;
		final int minR = Math.abs(runA - near);
		final int maxR = Math.min(taxiW, Math.abs(runA - far));
		if (minR > maxR || maxR < 2) {
			return;
		}
		final int roomNeg = taxiC - runC0;
		final int roomPos = runC1 - taxiC;
		emitSide(approachIsV, sign, taxiC, runA, Math.min(maxR, roomNeg), minR, taxiC - Math.min(maxR, roomNeg), taxi, runway, tb, rb, out);
		emitSide(approachIsV, sign, taxiC, runA, Math.min(maxR, roomPos), minR, taxiC + Math.min(maxR, roomPos), taxi, runway, tb, rb, out);
	}

	private static void emitSide(boolean approachIsV, int sign, int taxiC, int runA, int r, int minR, int endAcross, Set<Long> taxi,
			Set<Long> runway, int[] tb, int[] rb, List<DrawnJoin> out) {
		if (r < minR || r < 2) {
			return;
		}
		final int startA = runA - sign * r;
		final Cell start = approachIsV ? new Cell(taxiC, startA) : new Cell(startA, taxiC);
		final int du = approachIsV ? 0 : sign;
		final int dv = approachIsV ? sign : 0;
		final List<Cell> left = arc(start, du, dv, r);
		final List<Cell> right = mirrorAcross(left, approachIsV, taxiC);
		final List<Cell> curve = endsAt(left, approachIsV, endAcross, runA) ? left
				: endsAt(right, approachIsV, endAcross, runA) ? right : null;
		if (curve == null || !inside(rb, curve.get(curve.size() - 1))) {
			return;
		}
		final int half = (approachIsV ? tb[1] - tb[0] : tb[3] - tb[2]) / 2;
		final int half2 = half * half;
		final List<Cell> extra = new ArrayList<>();
		final Set<Long> seen = new HashSet<>();
		for (final Cell cell : curve) {
			for (int dv2 = -half; dv2 <= half; dv2++) {
				for (int du2 = -half; du2 <= half; du2++) {
					if (du2 * du2 + dv2 * dv2 > half2) {
						continue;
					}
					final int u = cell.u() + du2;
					final int v = cell.v() + dv2;
					final long id = key(u, v);
					if (seen.add(id) && !taxi.contains(id) && !runway.contains(id)) {
						extra.add(new Cell(u, v));
					}
				}
			}
		}
		final List<Cell> suppress = new ArrayList<>();
		final int fromA = sign > 0 ? startA + 1 : (approachIsV ? tb[2] : tb[0]);
		final int toA = sign > 0 ? (approachIsV ? tb[3] : tb[1]) : startA - 1;
		for (int a = fromA; a <= toA; a++) {
			final Cell cell = approachIsV ? new Cell(taxiC, a) : new Cell(a, taxiC);
			if (taxi.contains(key(cell.u(), cell.v()))) {
				suppress.add(cell);
			}
		}
		out.add(new DrawnJoin(curve, extra, suppress));
	}

	private static List<Cell> mirrorAcross(List<Cell> cells, boolean approachIsV, int taxiC) {
		final List<Cell> out = new ArrayList<>();
		for (final Cell cell : cells) {
			out.add(approachIsV ? new Cell(2 * taxiC - cell.u(), cell.v()) : new Cell(cell.u(), 2 * taxiC - cell.v()));
		}
		return out;
	}

	private static boolean endsAt(List<Cell> cells, boolean approachIsV, int endAcross, int runA) {
		final Cell end = cells.get(cells.size() - 1);
		final int across = approachIsV ? end.u() : end.v();
		final int approach = approachIsV ? end.v() : end.u();
		return across == endAcross && approach == runA;
	}

	private static boolean inside(int[] box, Cell cell) {
		return cell.u() >= box[0] && cell.u() <= box[1] && cell.v() >= box[2] && cell.v() <= box[3];
	}

	private static int centre(int[] link) {
		return (link[0] + link[1]) / 2;
	}

	private static int u(long key) {
		return DrawnShape.u(key);
	}

	private static int v(long key) {
		return DrawnShape.v(key);
	}

	private static int[] bounds(Set<Long> cells) {
		if (cells.isEmpty()) {
			return null;
		}
		int u0 = Integer.MAX_VALUE;
		int u1 = Integer.MIN_VALUE;
		int v0 = Integer.MAX_VALUE;
		int v1 = Integer.MIN_VALUE;
		for (final long cell : cells) {
			u0 = Math.min(u0, u(cell));
			u1 = Math.max(u1, u(cell));
			v0 = Math.min(v0, v(cell));
			v1 = Math.max(v1, v(cell));
		}
		return new int[] {u0, u1, v0, v1};
	}

	private static boolean solid(Set<Long> cells, int[] box) {
		return (long) cells.size() == (long) (box[1] - box[0] + 1) * (box[3] - box[2] + 1);
	}

	/** Length along v when {@code alongV}, otherwise along u. */
	private static int span(int[] box, boolean alongV) {
		return alongV ? box[3] - box[2] : box[1] - box[0];
	}

	/** Cells between two closed ranges, or -1 when they overlap. */
	private static int gap(int a0, int a1, int b0, int b1) {
		if (a1 < b0) {
			return b0 - a1 - 1;
		}
		if (b1 < a0) {
			return a0 - b1 - 1;
		}
		return -1;
	}

	private static int overlap(int a0, int a1, int b0, int b1) {
		return Math.min(a1, b1) - Math.max(a0, b0) + 1;
	}

	private static double ccw(double from, double to) {
		double d = to - from;
		final double tau = Math.PI * 2;
		d %= tau;
		if (d < 0) {
			d += tau;
		}
		return d;
	}
}
