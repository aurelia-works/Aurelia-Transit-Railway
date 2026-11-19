package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The yellow lines round a curved link, as straight strips short enough to follow the arc: the centreline on the same circles
 * as {@link TaxiwayCurve}'s cells and the plane's rail, and the fillet's inner edge lines tangent to the straight edge lines they
 * join. Painted cells would step like a staircase; these are drawn flat on the pavement by a bridge member block. Coordinates
 * are cells, a whole number being a cell's centre.
 */
public final class TaxiwayPaint {
	private TaxiwayPaint() {
	}

	/** One straight piece of line, centre to centre. */
	public record Strip(double u0, double v0, double u1, double v1) {
	}

	/** Longest piece along an arc: on the tightest radius drawn (2) the outer side of a bend opens by under a hundredth. */
	static final double STEP = 1;

	/** True when the fillet's inner edge is round enough to draw as an arc; a tighter corner keeps its painted cells. */
	public static boolean drawsEdges(AirportGeometry g) {
		final int r = TaxiwayCurve.radius(g);
		return r >= 2 && r - g.dims().taxi() / 2 >= 2;
	}

	/**
	 * True when the link's edge line at (u, v) is a painted cell: not beside the fillet, and, when the edges are drawn, short of the
	 * runway's shoulder (the drawn edge line ends where the fillet meets it). {@code side} is +1 for the high edge.
	 */
	public static boolean paintsLinkEdge(AirportGeometry g, Set<Long> extra, int u, int v, int side) {
		if (extra.contains(TaxiwayCurve.key(u, v + side))) {
			return false;
		}
		return !drawsEdges(g) || u < shoulderStart(g);
	}

	/** The centreline's four arcs and the half cells that join them to the straight throat; nothing on the runway. */
	public static List<Strip> centreline(AirportGeometry g, int[] link) {
		final List<Strip> out = new ArrayList<>();
		final int r = TaxiwayCurve.radius(g);
		if (r < 2) {
			return out;
		}
		final int m = (link[0] + link[1]) / 2;
		final int tc = g.taxiCentre();
		final int rc = g.runwayCentre(0);
		final double runway = g.runwayU0().get(0) - 0.5;
		for (final int side : new int[]{1, -1}) {
			arc(out, tc + r, m + side * r, r, Math.PI, Math.PI + side * Math.PI / 2, runway);
			arc(out, rc - r, m + side * r, r, -side * Math.PI / 2, 0, runway);
		}
		out.add(new Strip(tc + r, m, tc + r + 0.5, m));
		if (rc - r - 0.5 < runway) {
			out.add(new Strip(rc - r - 0.5, m, Math.min(rc - r, runway), m));
		}
		return out;
	}

	/**
	 * The fillet's inner edge on both sides of a link: an arc tangent to the taxiway's runway-side edge line and the link's edge
	 * line, an arc from the link's edge line to the shoulder, and straight pieces over the cells the fillet left unpainted.
	 */
	public static List<Strip> edges(AirportGeometry g, int[] link) {
		final List<Strip> out = new ArrayList<>();
		if (!drawsEdges(g)) {
			return out;
		}
		final int r = TaxiwayCurve.radius(g);
		final int half = g.dims().taxi() / 2;
		final int radius = r - half;
		final int m = (link[0] + link[1]) / 2;
		final int rc = g.runwayCentre(0);
		final int lineU = g.taxiU1() - 1;
		final double shoulder = shoulderStart(g) - 0.5;
		final int reach = r + half;
		final Set<Long> extra = TaxiwayCurve.extraCells(g);
		final int gapFrom = g.taxiU1() + 1;
		final int gapTo = g.runwayU0().get(0) - 1;
		for (final int side : new int[]{1, -1}) {
			final int lineV = side > 0 ? link[1] : link[0];
			arc(out, lineU + radius, lineV + side * radius, radius, Math.PI, Math.PI + side * Math.PI / 2, shoulder);
			arc(out, rc - r, lineV + side * radius, radius, -side * Math.PI / 2, 0, shoulder);
			// the taxiway's edge line breaks beside the fillet (TaxiwayCurve.opensEdge); run it on to the arc's tangent
			final double from = lineV + side * radius;
			// and no further than the taxiway itself: a link at its end has grass beyond
			final double to = Math.max(g.taxiV0() - 0.5, Math.min(g.taxiV1() + 0.5, m + side * (reach + 0.5)));
			if ((to - from) * side > 0) {
				out.add(new Strip(lineU, from, lineU, to));
			}
			// and the link's edge line from its painted cells to each arc's tangent
			final int taxiTangent = lineU + radius;
			for (int u = gapFrom; u <= gapTo; u++) {
				if (paintsLinkEdge(g, extra, u, lineV, side)) {
					if (u - 0.5 > taxiTangent) {
						out.add(new Strip(taxiTangent, lineV, u - 0.5, lineV));
					}
					break;
				}
			}
			final double runTangent = Math.min(rc - r, shoulder);
			for (int u = Math.min(rc - r, gapTo); u >= gapFrom; u--) {
				if (paintsLinkEdge(g, extra, u, lineV, side)) {
					if (u + 0.5 < runTangent) {
						out.add(new Strip(u + 0.5, lineV, runTangent, lineV));
					}
					break;
				}
			}
		}
		return out;
	}

	/** First cell of the runway's shoulder (or the runway, with none) beside the link. */
	private static int shoulderStart(AirportGeometry g) {
		return g.runwayU0().get(0) - g.dims().shoulder();
	}

	/** Pieces of the circle round (cu, cv) from angle a0 to a1 (u = cos, v = sin), cut where u passes {@code uMax}. */
	private static void arc(List<Strip> out, double cu, double cv, double radius, double a0, double a1, double uMax) {
		final int n = Math.max(1, (int) Math.ceil(radius * Math.abs(a1 - a0) / STEP));
		double pu = cu + radius * Math.cos(a0);
		double pv = cv + radius * Math.sin(a0);
		for (int i = 1; i <= n; i++) {
			final double a = a0 + (a1 - a0) * i / n;
			final double qu = cu + radius * Math.cos(a);
			final double qv = cv + radius * Math.sin(a);
			if (pu <= uMax && qu <= uMax) {
				out.add(new Strip(pu, pv, qu, qv));
			} else if (pu <= uMax || qu <= uMax) {
				final double t = (uMax - pu) / (qu - pu);
				final double cutV = pv + (qv - pv) * t;
				out.add(pu <= uMax ? new Strip(pu, pv, uMax, cutV) : new Strip(uMax, cutV, qu, qv));
			}
			pu = qu;
			pv = qv;
		}
	}
}
