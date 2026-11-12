package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

/**
 * The airplane route of an airport as pure geometry, in the local (u, v) cells of {@link AirportGeometry}: for each gate, the paths its
 * plane takes on the ground. Nothing here touches MTR or the world; the world turns the legs into rails.
 * <p>
 * The runway is one way, +v, and runway 0 only (a second runway is not routed here). The taxiway is taxied one way too, -v. A plane
 * lands at the threshold, rolls nearly the whole runway to the far link, leaves it there and taxis down the taxiway to a stand. It
 * turns in and rolls nose-in to the stop line. To leave, it is pushed back tail-first out along its lead-in and round the turn onto
 * the taxiway, its tail toward +v, so it ends facing the way it taxis; it stops on that stretch (MTR reverses only on a turn-back
 * rail), then taxis forward down to the near link, turns onto the runway and takes off. A plane flies from gate to gate; its remote
 * stand (the siding, half a stand pitch from the gate) is where it waits before its first departure, and it leaves it the same way.
 * <p>
 * Each gate's legs, in the order a plane uses them:
 * <ol>
 * <li>TAXI: down the taxiway to the remote stand's row;</li>
 * <li>SIDING: the remote stand, nose-in from the taxiway to the stop line;</li>
 * <li>PUSH_BACK: the taxiway stretch a plane pushed off the remote stand stops on, +v from where its lead-in joins the taxiway;</li>
 * <li>TAXI: from that join down the taxiway to the near link;</li>
 * <li>LINK: across to the runway at the near link, on a quarter-circle when the fillet fits;</li>
 * <li>TAKE_OFF: the take-off roll from that curve to the far end;</li>
 * <li>the flight: not a cell, from the far end back to the threshold;</li>
 * <li>LANDING: the landing roll from the threshold to the far link's curve;</li>
 * <li>LINK: off the runway at the far link, to the taxiway;</li>
 * <li>TAXI: down the taxiway to the stand's row;</li>
 * <li>GATE: the lead-in and the platform along the stand's row, nose at {@link AirportGeometry#stopU()};</li>
 * <li>PUSH_BACK: the stretch a plane pushed off the gate stops on;</li>
 * <li>TAXI: from the gate's join down to the near link's first node, where the take-off link of leg 5 starts.</li>
 * </ol>
 * The legs touch end to start except across the flight and the two push-backs, where the plane reverses. Every stand row, siding row
 * and push-back stretch must lie between the two links, or there is no route ({@link #problem()} says so). A stand's straight must
 * hold a plane ({@link #PLANE_LENGTH}); a compact airport's does not, so it gets no route either.
 *
 * @param gates   one set of legs per gate, in stand order; empty when there is no route
 * @param problem why there is no route, or the empty string
 */
public record AirportRoute(List<Gate> gates, String problem) {

	/** A plane's length in blocks: the spike's a320 car. A siding shorter than this cannot hold one. */
	public static final int PLANE_LENGTH = 30;

	/** One cell of the airport's grid: {@code u} across the airport, {@code v} along the runway. */
	public record Cell(int u, int v) {

		/** True when the other cell is the next one along a rail: one step in u or v. */
		public boolean adjacent(Cell other) {
			return Math.abs(u - other.u) + Math.abs(v - other.v) == 1;
		}
	}

	/** Taxiway cells a pushed-back plane covers before it stops and taxis forward: its length and the turn's two cells. */
	public static final int PUSH_BACK_LENGTH = PLANE_LENGTH + 2;

	/** LEAD_IN is the turn from the taxiway into a stand; legs never carry it, the rails give it to that corner. */
	public enum Kind {
		SIDING, LEAD_IN, PUSH_BACK, TAXI, LINK, TAKE_OFF, LANDING, GATE
	}

	/** A straight run of cells, in the order the plane flies them. */
	public record Leg(Kind kind, List<Cell> cells) {
	}

	/**
	 * A gate's loop.
	 *
	 * @param index    the stand's number from 0
	 * @param row      the platform's row (v)
	 * @param sidingRow the siding's row (v)
	 * @param platform the platform's cells: the GATE leg from the nose to the apron's edge
	 * @param legs     the legs in the order a plane uses them, from the taxiway into the remote stand
	 */
	public record Gate(int index, int row, int sidingRow, List<Cell> platform, List<Leg> legs) {
	}

	/** The route of an airport, or none with the reason. */
	public static AirportRoute of(AirportGeometry g) {
		final int count = g.spec().gates();
		if (!g.spec().present() || count < 1) {
			return none("no gates to route");
		}
		final int stop = g.stopU();
		final int tc = g.taxiCentre();
		final int rc = g.runwayCentre(0);
		final int v0 = g.runwayV0();
		final int v1 = g.runwayV1();
		final int m0 = centre(g.links().get(0));
		// links: [near the threshold (take-off), near the far end (exit), and a middle one on a long runway, which is not routed]
		final int m1 = centre(g.links().get(1));
		// blocks of a stand's straight rail: from the stop line to the cell before the turn off the taxiway (AirportRails cuts that corner)
		final int siding = tc - 1 - stop;
		if (siding < PLANE_LENGTH) {
			return none("the siding would be " + siding + " blocks; a plane is " + PLANE_LENGTH);
		}
		// one radius for every gate: the paint and the rail have to be the same arc
		final int radius = TaxiwayCurve.radius(g);
		final List<Gate> gates = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			final int c = g.standV().get(i);
			final int s = c + g.dims().standPitch() / 2;
			if (!(v0 < m0 && m0 < c && c < s && s < m1 && m1 < v1)) {
				return none("stand " + (i + 1) + " at row " + c + " is not between the take-off link " + m0 + " and the exit link " + m1);
			}
			// the push-back off the remote stand ends furthest up the taxiway; it has to stop short of the exit link's turn
			if (s + 1 + PUSH_BACK_LENGTH >= arriveRow(m1, radius)) {
				return none("stand " + (i + 1) + "'s push-back would run into the exit link at row " + m1);
			}
			final List<Leg> legs = loop(stop, tc, rc, c, s, m0, m1, v0, v1, radius);
			final List<Cell> platform = new ArrayList<>();
			for (final Cell cell : legs.stream().filter(leg -> leg.kind() == Kind.GATE).findFirst().orElseThrow().cells()) {
				if (cell.u() <= g.apronU1()) {
					platform.add(cell);
				}
			}
			gates.add(new Gate(i, c, s, List.copyOf(platform), List.copyOf(legs)));
		}
		return new AirportRoute(List.copyOf(gates), "");
	}

	private static AirportRoute none(String why) {
		return new AirportRoute(List.of(), why);
	}

	private static int centre(int[] link) {
		return (link[0] + link[1]) / 2;
	}

	/** The taxiway cell where a straight taxiway rail meets the exit link: the arc's end, or past the one-step corner. */
	private static int arriveRow(int m1, int radius) {
		return radius < 2 ? m1 - 1 : m1 - radius;
	}

	private static List<Leg> loop(int stop, int tc, int rc, int c, int s, int m0, int m1, int v0, int v1, int radius) {
		// the taxiway rail into the take-off link: in one run with the link it ends on the corner (or the cell before the arc); on its
		// own it must end on the link's first node, the cell before the corner (or the arc's first cell)
		final int intoLink = radius < 2 ? m0 : m0 + radius + 1;
		final int linkNode = radius < 2 ? m0 + 1 : m0 + radius;
		final int arrive = arriveRow(m1, radius);
		// in one run with the exit link the taxiway starts on the cell past the arc's end
		final int afterLink = radius < 2 ? arrive : arrive - 1;
		final List<Leg> legs = new ArrayList<>();
		legs.add(new Leg(Kind.TAXI, runV(tc, arrive, s)));
		legs.add(new Leg(Kind.SIDING, runU(tc - 1, stop, s)));
		legs.add(new Leg(Kind.PUSH_BACK, runV(tc, s + 1, s + 1 + PUSH_BACK_LENGTH)));
		legs.add(new Leg(Kind.TAXI, runV(tc, s + 1, intoLink)));
		if (radius < 2) {
			legs.add(new Leg(Kind.LINK, runU(tc + 1, rc, m0)));
			legs.add(new Leg(Kind.TAKE_OFF, runV(rc, m0 + 1, v1)));
			legs.add(new Leg(Kind.LANDING, runV(rc, v0, m1)));
			legs.add(new Leg(Kind.LINK, runU(rc - 1, tc, m1)));
		} else {
			// the straight stops on the tangent; the link carries both arcs and the throat between them
			legs.add(new Leg(Kind.LINK, TaxiwayCurve.ontoRunway(tc, rc, m0, radius)));
			legs.add(new Leg(Kind.TAKE_OFF, runV(rc, m0 + radius + 1, v1)));
			legs.add(new Leg(Kind.LANDING, runV(rc, v0, m1 - radius - 1)));
			legs.add(new Leg(Kind.LINK, TaxiwayCurve.offRunway(tc, rc, m1, radius)));
		}
		legs.add(new Leg(Kind.TAXI, runV(tc, afterLink, c)));
		legs.add(new Leg(Kind.GATE, runU(tc - 1, stop, c)));
		legs.add(new Leg(Kind.PUSH_BACK, runV(tc, c + 1, c + 1 + PUSH_BACK_LENGTH)));
		legs.add(new Leg(Kind.TAXI, runV(tc, c + 1, linkNode)));
		return legs;
	}

	/** Cells along u at row v, from u {@code from} to u {@code to}, both ends included, in either direction. */
	private static List<Cell> runU(int from, int to, int v) {
		final List<Cell> out = new ArrayList<>();
		final int step = from <= to ? 1 : -1;
		for (int u = from; u != to + step; u += step) {
			out.add(new Cell(u, v));
		}
		return out;
	}

	/** Cells along v at column u, from v {@code from} to v {@code to}, both ends included, in either direction. */
	private static List<Cell> runV(int u, int from, int to) {
		final List<Cell> out = new ArrayList<>();
		final int step = from <= to ? 1 : -1;
		for (int v = from; v != to + step; v += step) {
			out.add(new Cell(u, v));
		}
		return out;
	}
}
