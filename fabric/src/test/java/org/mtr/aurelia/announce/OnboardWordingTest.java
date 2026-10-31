package org.mtr.aurelia.announce;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnboardWordingTest {

	@Test
	void standardKeepsTheAnnouncerLinesAndClipsAreNotPartOfWording() {
		final Announcement announcement = new Announcement(Announcement.Kind.NEXT,
				List.of("The next station is Alpha.", "Change here for Blue.", "This is the last stop."),
				List.of("next_alpha", "terminus"));
		assertEquals(announcement.lines(), OnboardWording.STANDARD.lines(announcement));
		assertEquals("The next station is Alpha. This is the last stop.", OnboardWording.STANDARD.actionBar(announcement));
	}

	@Test
	void britishRewordsNextArrivingAndTerminusWithoutTouchingInterchanges() {
		final Announcement next = new Announcement(Announcement.Kind.NEXT,
				List.of("The next station is Alpha.", "Change here for Blue.", "This is the last stop."),
				List.of("next_alpha", "terminus"));
		assertEquals(List.of("We are calling at Alpha.", "Change here for Blue.", "This train terminates here."),
				OnboardWording.BRITISH.lines(next));
		final Announcement arriving = new Announcement(Announcement.Kind.ARRIVING,
				List.of("Arriving at Alpha.", "This is the last stop. Please take all your belongings."),
				List.of("chime"));
		assertEquals(List.of("We are now arriving at Alpha.", "This train terminates here. Please take all of your belongings with you."),
				OnboardWording.BRITISH.lines(arriving));
	}

	@Test
	void unknownStyleIdStaysStandard() {
		assertEquals(OnboardWording.STANDARD, OnboardWording.byId(null));
		assertEquals(OnboardWording.STANDARD, OnboardWording.byId("london"));
		assertEquals(OnboardWording.BRITISH, OnboardWording.byId("british"));
	}
}
