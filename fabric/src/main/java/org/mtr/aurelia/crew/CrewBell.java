package org.mtr.aurelia.crew;

/**
 * The two signals the crew give a train before it leaves a platform. Pure logic.
 */
public enum CrewBell {
	/** The guard's two bells to the driver: doors shut, train ready to go. Sent for the train the guard rides. */
	READY_TO_START(CrewRole.GUARD),
	/** The dispatcher's "right away" to the train standing at the platform. */
	RIGHT_AWAY(CrewRole.DISPATCHER);

	private final CrewRole role;

	CrewBell(CrewRole role) {
		this.role = role;
	}

	/** The role that may give this signal. */
	public CrewRole role() {
		return role;
	}

	/** The bell at {@code index}, or null. */
	public static CrewBell byIndex(int index) {
		return index >= 0 && index < values().length ? values()[index] : null;
	}
}
