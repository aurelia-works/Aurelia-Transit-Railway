package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mod.resource.DoorShare;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Parts both door states use are linked once per build; uploads are split into those and the rest. */
class DoorShareTest {

	private static final class Part {
		private final String name;

		private Part(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private final Part body = new Part("body");
	private final Part seats = new Part("seats");
	private final Part doorsClosed = new Part("doors closed");
	private final Part doorsOpen = new Part("doors open");

	private static Map<Part, DoorShare<Part>> link(Map<String, List<Part>> open, Map<String, List<Part>> closed) {
		return DoorShare.link(open, closed);
	}

	@Test
	void openPartsInsideClosedListAreShared() {
		final Map<Part, DoorShare<Part>> shares = link(Map.of("always", List.of(body, seats)), Map.of("always", List.of(body, seats, doorsClosed)));
		assertSame(shares.get(body), shares.get(seats));
		assertNull(shares.get(doorsClosed));

		final DoorShare.Split<Part> split = DoorShare.split(List.of(body, seats, doorsClosed), shares::get);
		assertSame(shares.get(body), split.share);
		assertEquals(List.of(body, seats), split.common);
		assertEquals(List.of(doorsClosed), split.rest);
	}

	@Test
	void listsThatDifferAreNotShared() {
		final Map<Part, DoorShare<Part>> shares = link(Map.of("always", List.of(body, doorsOpen)), Map.of("always", List.of(body, doorsClosed)));
		assertTrue(shares.isEmpty());
		assertNull(DoorShare.split(List.of(body, doorsOpen), shares::get).share);
	}

	@Test
	void partInTwoConditionsGetsNoShareOfItsOwn() {
		final Map<String, List<Part>> open = new HashMap<>();
		open.put("lit", List.of(body));
		open.put("unlit", List.of(body, seats));
		final Map<Part, DoorShare<Part>> shares = link(open, Map.of("lit", List.of(body), "unlit", List.of(body, seats)));
		assertNull(shares.get(body));
		// The "lit" upload has no share; the "unlit" one still shares its exact part set through seats
		assertNull(DoorShare.split(List.of(body), shares::get).share);
		final DoorShare.Split<Part> unlit = DoorShare.split(List.of(body, seats), shares::get);
		assertSame(shares.get(seats), unlit.share);
		assertEquals(List.of(body, seats), unlit.common);
	}

	@Test
	void uploadMissingACommonPartIsNotShared() {
		final Map<Part, DoorShare<Part>> shares = link(Map.of("always", List.of(body, seats)), Map.of("always", List.of(body, seats, doorsClosed)));
		final DoorShare.Split<Part> split = DoorShare.split(List.of(body, doorsClosed), shares::get);
		assertNull(split.share);
		assertEquals(List.of(body, doorsClosed), split.unique);
	}

	@Test
	void bothDoorStatesGetTheSameCommonOrder() {
		final Map<Part, DoorShare<Part>> shares = link(Map.of("always", List.of(seats, body)), Map.of("always", List.of(body, doorsClosed, seats)));
		final DoorShare.Split<Part> open = DoorShare.split(List.of(seats, body), shares::get);
		final DoorShare.Split<Part> closed = DoorShare.split(List.of(body, doorsClosed, seats), shares::get);
		assertSame(open.common, closed.common);
	}

	@Test
	void partListedTwiceIsUploadedOnce() {
		final Map<Part, DoorShare<Part>> shares = new IdentityHashMap<>();
		assertEquals(List.of(body), DoorShare.split(List.of(body, body), shares::get).unique);
	}

	@Test
	void nothingUploadedYet() {
		final Map<Part, DoorShare<Part>> shares = link(Map.of("always", List.of(body)), Map.of("always", List.of(body, doorsClosed)));
		assertNull(shares.get(body).uploaded());
	}
}
