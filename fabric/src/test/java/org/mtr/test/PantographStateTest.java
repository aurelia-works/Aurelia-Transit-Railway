package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mtr.mod.resource.PantographEnd;
import org.mtr.mod.resource.PantographMotion;
import org.mtr.mod.resource.PantographState;

public final class PantographStateTest {

	private static final long SECOND = 1_000_000_000L;
	private static final double EPSILON = 1E-9;

	private final PantographState state = new PantographState();

	@BeforeEach
	public void reset() {
		state.clear();
	}

	@Test
	public void aCarSeenOnRouteStartsUpAndOneAtTheDepotStartsDown() {
		Assertions.assertEquals(1, state.update(1, 0, PantographEnd.BOTH, true, 0, 2), EPSILON);
		Assertions.assertEquals(0, state.update(2, 0, PantographEnd.BOTH, false, 0, 2), EPSILON);
	}

	@Test
	public void leavingTheDepotRaisesOverTheRaiseTime() {
		state.update(1, 0, PantographEnd.BOTH, false, 0, 2);
		Assertions.assertEquals(PantographMotion.ease(0.5), state.update(1, 0, PantographEnd.BOTH, true, SECOND, 2), EPSILON);
		Assertions.assertEquals(1, state.update(1, 0, PantographEnd.BOTH, true, 2 * SECOND, 2), EPSILON);
		Assertions.assertEquals(1, state.update(1, 0, PantographEnd.BOTH, true, 9 * SECOND, 2), EPSILON, "stays up");
	}

	@Test
	public void returningToTheDepotLowersAgain() {
		state.update(1, 0, PantographEnd.BOTH, true, 0, 2);
		Assertions.assertEquals(PantographMotion.ease(0.5), state.update(1, 0, PantographEnd.BOTH, false, SECOND, 2), EPSILON);
		Assertions.assertEquals(0, state.update(1, 0, PantographEnd.BOTH, false, 3 * SECOND, 2), EPSILON);
	}

	@Test
	public void partsOfOneCarInTheSameFrameAgree() {
		state.update(1, 0, PantographEnd.BOTH, false, 0, 2);
		final double first = state.update(1, 0, PantographEnd.BOTH, true, SECOND, 2);
		Assertions.assertEquals(first, state.update(1, 0, PantographEnd.BOTH, true, SECOND, 2), EPSILON);
	}

	@Test
	public void carsAndEndsMoveOnTheirOwn() {
		state.update(1, 0, PantographEnd.FRONT, true, 0, 2);
		state.update(1, 0, PantographEnd.REAR, false, 0, 2);
		state.update(1, 1, PantographEnd.FRONT, false, 0, 2);
		Assertions.assertEquals(3, state.size());
		Assertions.assertEquals(1, state.update(1, 0, PantographEnd.FRONT, true, SECOND, 2), EPSILON);
		Assertions.assertEquals(0, state.update(1, 0, PantographEnd.REAR, false, SECOND, 2), EPSILON);
	}

	@Test
	public void carsNotSeenForAWhileAreForgotten() {
		state.update(1, 0, PantographEnd.BOTH, true, 0, 2);
		state.update(2, 0, PantographEnd.BOTH, true, 100 * SECOND, 2);
		Assertions.assertEquals(1, state.size());
	}
}
