package org.mtr.aurelia.announce;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How an onboard announcement is worded. Clips stay on the keys {@link AnnouncerLogic} already chose
 * ({@code next_<slug>}, {@code terminus}, {@code chime}), so a voice pack keeps matching either style.
 * {@link #STANDARD} is the Announcer's own English. {@link #BRITISH} follows the same pattern as the platform
 * speakers' British style ("We are now arriving at…", "This train terminates here").
 */
public enum OnboardWording {
	STANDARD,
	BRITISH;

	public static OnboardWording byId(String id) {
		if (id == null) {
			return STANDARD;
		}
		for (final OnboardWording wording : values()) {
			if (wording.name().equalsIgnoreCase(id) || wording.name().toLowerCase(Locale.ROOT).equals(id.toLowerCase(Locale.ROOT))) {
				return wording;
			}
		}
		return "british".equalsIgnoreCase(id) ? BRITISH : STANDARD;
	}

	public OnboardWording next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Chat lines for this wording. The clip list is left untouched. */
	public List<String> lines(Announcement announcement) {
		if (this == STANDARD || announcement == null) {
			return announcement == null ? List.of() : announcement.lines();
		}
		final List<String> lines = new ArrayList<>();
		for (final String line : announcement.lines()) {
			lines.add(british(line));
		}
		return List.copyOf(lines);
	}

	/** One action-bar line: next stop, then interchange or terminus when the announcement says so. */
	public String actionBar(Announcement announcement) {
		final List<String> lines = lines(announcement);
		if (lines.isEmpty()) {
			return "";
		}
		if (lines.size() == 1) {
			return lines.get(0);
		}
		return lines.get(0) + " " + lines.get(lines.size() - 1);
	}

	private static String british(String line) {
		if (line.startsWith("The next station is ")) {
			return "We are calling at " + line.substring("The next station is ".length());
		}
		if (line.startsWith("Arriving at ")) {
			return "We are now arriving at " + line.substring("Arriving at ".length());
		}
		if (line.equals("This is the last stop.")) {
			return "This train terminates here.";
		}
		if (line.startsWith("This is the last stop.")) {
			return "This train terminates here. Please take all of your belongings with you.";
		}
		if (line.startsWith("Change here for ")) {
			return "Change here for " + line.substring("Change here for ".length());
		}
		if (line.startsWith("This train continues as ")) {
			return "This train will continue as " + line.substring("This train continues as ".length());
		}
		return line;
	}
}
