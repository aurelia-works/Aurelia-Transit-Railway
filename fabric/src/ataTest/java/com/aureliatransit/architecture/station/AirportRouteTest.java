package com.aureliatransit.architecture.station;

import com.aureliatransit.architecture.station.AirportRoute.Cell;
import com.aureliatransit.architecture.station.AirportRoute.Gate;
import com.aureliatransit.architecture.station.AirportRoute.Kind;
import com.aureliatransit.architecture.station.AirportRoute.Leg;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The airplane route of an airport: per gate, the legs from remote stand to runway to gate and back out, as pure geometry. */
class AirportRouteTest {

	/** A full-size airport (REAL dimensions): 30 wide, 1500 long, five gates, a terminal. */
	private static final AirportGeometry REAL = AirportGeometry.of(new AirportSpec(1, 1500, 30, 5, true));

	/** A compact airport (narrow runway): its siding cannot hold a plane. */
	private static final AirportGeometry COMPACT = AirportGeometry.of(new AirportSpec(1, 1500, AirportSpec.MIN_RUNWAY_WIDTH, 5, true));

	private static Cell first(Leg leg) {
		return leg.cells().get(0);
	}

	private static Cell last(Leg leg) {
		return leg.cells().get(leg.cells().size() - 1);
	}

	@Test
	void everyGateGetsALoop() {
		final AirportRoute route = AirportRoute.of(REAL);
		assertEquals("", route.problem());
		assertEquals(5, route.gates().size());
		for (final Gate gate : route.gates()) {
			assertEquals(12, gate.legs().size(), "gate " + gate.index());
			assertFalse(gate.platform().isEmpty());
		}
	}

	@Test
	void theLegsAreConnectedExceptTheFlightAndThePushBacks() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			final List<Leg> legs = gate.legs();
			for (final Leg leg : legs) {
				for (int i = 1; i < leg.cells().size(); i++) {
					assertTrue(leg.cells().get(i - 1).adjacent(leg.cells().get(i)), "gate " + gate.index() + " " + leg.kind() + " at " + i);
				}
			}
			for (int k = 1; k < legs.size(); k++) {
				final Leg before = legs.get(k - 1);
				final Leg after = legs.get(k);
				if (before.kind() == Kind.TAKE_OFF && after.kind() == Kind.LANDING) {
					continue; // the flight: the plane leaves the far end and lands at the threshold, off the grid
				}
				if (before.kind() == Kind.PUSH_BACK || after.kind() == Kind.PUSH_BACK) {
					continue; // the plane reverses: pushed back from the stand's end, then pulled forward from the push-back's far end
				}
				assertTrue(last(before).adjacent(first(after)), "gate " + gate.index() + " joins " + before.kind() + " to " + after.kind());
			}
		}
	}

	/** Jah 11-12: the push-back should be real. A plane is pushed tail-first out of a stand and round onto the taxiway, then taxis forward. */
	@Test
	void eachStandIsLeftByAPushBackOntoTheTaxiway() {
		final int tc = REAL.taxiCentre();
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			final List<Leg> legs = gate.legs();
			for (final int stand : new int[]{1, 9}) {
				final Leg in = legs.get(stand);
				final Leg push = legs.get(stand + 1);
				final Leg out = legs.get(stand + 2);
				assertEquals(REAL.stopU(), last(in).u(), "the nose stops at the stop line");
				assertEquals(Kind.PUSH_BACK, push.kind());
				assertEquals(new Cell(tc, last(in).v() + 1), first(push), "pushed round the stand's corner onto the taxiway");
				assertTrue(last(push).v() > first(push).v(), "pushed tail-first, against the way it taxis");
				assertTrue(push.cells().size() > AirportRoute.PLANE_LENGTH, "pushed until the whole plane is straight on the taxiway");
				assertEquals(Kind.TAXI, out.kind());
				assertEquals(first(push), first(out), "taxis forward from where the push-back began");
			}
			assertEquals(Kind.SIDING, legs.get(1).kind());
			assertEquals(Kind.GATE, legs.get(9).kind());
			assertEquals(gate.sidingRow(), legs.get(1).cells().get(0).v());
		}
	}

	@Test
	void theRouteNeverCrossesTheTerminal() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			for (final Leg leg : gate.legs()) {
				for (final Cell cell : leg.cells()) {
					assertFalse(REAL.inTerminal(cell.u(), cell.v()), gate.index() + " " + leg.kind() + " " + cell);
					assertTrue(cell.u() > REAL.terminalU1(), gate.index() + " " + leg.kind() + " " + cell + " is on the terminal side");
				}
			}
		}
	}

	@Test
	void theSidingHoldsAPlane() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			final Leg siding = gate.legs().get(1);
			assertTrue(siding.cells().size() >= AirportRoute.PLANE_LENGTH, "siding of gate " + gate.index());
		}
	}

	@Test
	void theRunwayIsEnteredOnlyAtTheNearLinkAndLeftOnlyAtTheFarLink() {
		final AirportRoute route = AirportRoute.of(REAL);
		final int[] nearLink = REAL.links().get(0);
		final int takeOffRow = (nearLink[0] + nearLink[1]) / 2;
		final int[] farLink = REAL.links().get(1);
		final int exitRow = (farLink[0] + farLink[1]) / 2;
		for (final Gate gate : route.gates()) {
			final List<Leg> legs = gate.legs();
			final Leg takeOffLink = legs.get(4);
			final Leg exitLink = legs.get(7);
			assertEquals(Kind.LINK, takeOffLink.kind());
			assertEquals(Kind.LINK, exitLink.kind());
			final int reach = TaxiwayCurve.radius(REAL);
			assertTrue(reach >= 2, "the real fillet has a radius");
			boolean takeOffOnRow = false;
			for (final Cell cell : takeOffLink.cells()) {
				assertTrue(Math.abs(cell.v() - takeOffRow) <= reach, "take-off link stays at that link " + cell);
				if (cell.v() == takeOffRow) {
					takeOffOnRow = true;
				}
			}
			assertTrue(takeOffOnRow, "the take-off link still crosses on the link row");
			boolean exitOnRow = false;
			for (final Cell cell : exitLink.cells()) {
				assertTrue(Math.abs(cell.v() - exitRow) <= reach, "exit link stays at that link " + cell);
				if (cell.v() == exitRow) {
					exitOnRow = true;
				}
			}
			assertTrue(exitOnRow, "the exit link still crosses on the link row");
			final Leg takeOff = legs.get(5);
			final Leg landing = legs.get(6);
			assertEquals(new Cell(REAL.runwayCentre(0), REAL.runwayV1()), last(takeOff), "the take-off roll ends at the far end");
			assertEquals(new Cell(REAL.runwayCentre(0), REAL.runwayV0()), first(landing), "the landing roll starts at the threshold");
		}
	}

	/** Review 10-27: the planes took off and stopped in about 16 blocks of a 1,500-block runway. */
	@Test
	void takeOffAndLandingUseNearlyTheWholeRunway() {
		final int runway = REAL.spec().runwayLength();
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			assertTrue(gate.legs().get(5).cells().size() >= runway * 9 / 10, "take-off roll of gate " + gate.index());
			assertTrue(gate.legs().get(6).cells().size() >= runway * 9 / 10, "landing roll of gate " + gate.index());
		}
	}

	/** The taxiway is flown one way (-v), so no plane meets another head on. */
	@Test
	void theTaxiwayIsFlownOneWay() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			for (final Leg leg : gate.legs()) {
				if (leg.kind() == Kind.TAXI) {
					assertTrue(last(leg).v() < first(leg).v(), "gate " + gate.index() + " taxis -v");
				}
			}
		}
	}

	@Test
	void platformsAreOnTheirRowsBetweenTheNoseAndTheApron() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			for (final Cell cell : gate.platform()) {
				assertEquals(gate.row(), cell.v());
				assertTrue(cell.u() >= REAL.stopU() && cell.u() <= REAL.apronU1(), gate.index() + " " + cell);
			}
		}
	}

	@Test
	void sidingsAndPlatformsNeverShareARow() {
		final List<Integer> rows = new ArrayList<>();
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			assertFalse(rows.contains(gate.sidingRow()), "siding row " + gate.sidingRow());
			rows.add(gate.sidingRow());
		}
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			assertFalse(rows.contains(gate.row()), "platform row " + gate.row() + " is a siding row");
			rows.add(gate.row());
		}
	}

	@Test
	void aCompactAirportHasNoRouteBecauseItsSidingIsTooShort() {
		final AirportRoute route = AirportRoute.of(COMPACT);
		assertTrue(route.gates().isEmpty());
		assertTrue(route.problem().contains("siding"), route.problem());
	}
}
