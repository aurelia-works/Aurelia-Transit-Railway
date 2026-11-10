package org.mtr.aurelia.crew;

import java.util.Locale;

/**
 * The job a player takes on the railway. A driver drives (MTR's driver key); a guard rides the train and rings
 * "ready to start" to the driver once the doors are shut; a dispatcher on the platform gives "right away" to the train
 * standing there; a signaller holds signals at danger. Roles last for the session. Pure logic.
 */
public enum CrewRole {
	NONE("none"),
	DRIVER("driver"),
	GUARD("guard"),
	DISPATCHER("dispatcher"),
	SIGNALLER("signaller");

	private final String id;

	CrewRole(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** The role named {@code id}, or null. */
	public static CrewRole byId(String id) {
		final String wanted = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
		for (final CrewRole role : values()) {
			if (role.id.equals(wanted)) {
				return role;
			}
		}
		return null;
	}

	public static CrewRole byIndex(int index) {
		return index >= 0 && index < values().length ? values()[index] : NONE;
	}

	/** The next role in order, wrapping round, for the panel's cycle button. */
	public CrewRole next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
