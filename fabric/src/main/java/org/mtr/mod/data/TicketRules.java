package org.mtr.mod.data;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Ticket currency and free-barrier rules that do not need a Minecraft registry.
 * Item ids follow Minecraft 1.20's rules: {@code namespace:path}, a missing namespace means {@code minecraft},
 * and only {@code [a-z0-9_.-]} (plus {@code /} in the path) is accepted.
 */
public final class TicketRules {

	public static final String DEFAULT_CURRENCY_ID = "minecraft:emerald";
	public static final String AIR_ID = "minecraft:air";

	private TicketRules() {
	}

	public enum Problem {
		NONE, BLANK, INVALID_ID, AIR, UNKNOWN_ITEM
	}

	/** The id that will actually be spent, plus why the configured text was or was not used. */
	public static final class CurrencyResolution {
		public final String id;
		public final Problem problem;
		public final String configured;

		CurrencyResolution(String id, Problem problem, String configured) {
			this.id = id;
			this.problem = problem;
			this.configured = configured;
		}
	}

	/**
	 * How a barrier opens when passage is free. Null is not used here: callers treat a null return from
	 * {@link #freePassage} as "charge the normal fare".
	 */
	public static final class FreePassage {
		/** False only for an exit-only barrier, which plays the exit sound. */
		public final boolean entering;
		public final boolean concessionary;

		FreePassage(boolean entering, boolean concessionary) {
			this.entering = entering;
			this.concessionary = concessionary;
		}
	}

	/**
	 * @param itemExists true when the id is registered and is not air. Called only for a syntactically valid, non-air id.
	 */
	public static CurrencyResolution resolve(@Nullable String configured, Predicate<String> itemExists) {
		if (configured == null || configured.isBlank()) {
			return new CurrencyResolution(DEFAULT_CURRENCY_ID, Problem.BLANK, configured == null ? "" : configured);
		}
		final String id = canonicalItemId(configured);
		if (id == null) {
			return new CurrencyResolution(DEFAULT_CURRENCY_ID, Problem.INVALID_ID, configured);
		}
		if (AIR_ID.equals(id)) {
			return new CurrencyResolution(DEFAULT_CURRENCY_ID, Problem.AIR, configured);
		}
		if (!itemExists.test(id)) {
			return new CurrencyResolution(DEFAULT_CURRENCY_ID, Problem.UNKNOWN_ITEM, configured);
		}
		return new CurrencyResolution(id, Problem.NONE, configured);
	}

	/**
	 * @return {@code namespace:path}, or null when {@code raw} is not a valid item id
	 */
	@Nullable
	public static String canonicalItemId(@Nullable String raw) {
		if (raw == null) {
			return null;
		}
		final String trimmed = raw.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		final int colon = trimmed.indexOf(':');
		final String namespace;
		final String path;
		if (colon < 0) {
			namespace = "minecraft";
			path = trimmed;
		} else {
			path = trimmed.substring(colon + 1);
			namespace = colon == 0 ? "minecraft" : trimmed.substring(0, colon);
		}
		if (!isNamespace(namespace) || !isPath(path)) {
			return null;
		}
		return namespace + ":" + path;
	}

	/**
	 * @param alreadyEntered whether the player already holds an entry record. Used only to pick the sound on a bidirectional gate. The record itself is not changed.
	 * @return the open-without-charging result, or null when the barrier should run the normal fare
	 */
	@Nullable
	public static FreePassage freePassage(boolean configuredFree, boolean isEntrance, boolean isExit, boolean alreadyEntered, boolean creative) {
		if (!configuredFree) {
			return null;
		}
		final boolean entering;
		if (isEntrance && isExit) {
			entering = !alreadyEntered;
		} else {
			// Exit-only blocks use the exit sound. Entrance-only blocks use the entry sound.
			entering = isEntrance || !isExit;
		}
		return new FreePassage(entering, creative);
	}

	private static boolean isNamespace(String value) {
		if (value.isEmpty()) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			if (!isNamespaceChar(value.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean isPath(String value) {
		if (value.isEmpty()) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			final char character = value.charAt(i);
			if (character != '/' && !isNamespaceChar(character)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isNamespaceChar(char character) {
		return character == '_' || character == '-' || character == '.' || character >= 'a' && character <= 'z' || character >= '0' && character <= '9';
	}
}
