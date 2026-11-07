package org.mtr.aurelia.passenger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.client.PassengerLook;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class PassengerLookTest {

	/** A person must not change clothes between frames, and a crowd must not be one person repeated. */
	@Test
	public void aCrowdIsVariedAndEachPersonStaysTheSame() {
		final Set<PassengerLook.Pose> poses = EnumSet.noneOf(PassengerLook.Pose.class);
		final Set<Long> people = new HashSet<>();
		for (int i = 0; i < 40; i++) {
			final PassengerLook look = PassengerLook.of(7L, i);
			Assertions.assertEquals(look, PassengerLook.of(7L, i));
			poses.add(look.pose());
			people.add(look.skinSeed());
		}
		Assertions.assertEquals(PassengerLook.Pose.values().length, poses.size());
		Assertions.assertEquals(40, people.size());
	}
}
