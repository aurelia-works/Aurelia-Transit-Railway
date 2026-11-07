package org.mtr.aurelia.announce;

import java.util.List;

/**
 * One announcement: the English lines shown on screen, the legacy clips played in order, and the voice-pack fragments
 * that can replace those clips when a pack has them. A clip is the key after {@code mtr:aureliaex_ann_}, e.g.
 * {@code next_alpha}, {@code terminus} or {@code chime}.
 */
public record Announcement(Kind kind, List<String> lines, List<String> clips, List<VoiceFragment> fragments) {

	public Announcement(Kind kind, List<String> lines, List<String> clips) {
		this(kind, lines, clips, List.of());
	}

	/**
	 * @param key  voice-pack fragment key, e.g. {@code phrase.next_station_is} or {@code station.central}
	 * @param text the words the fragment stands for
	 */
	public record VoiceFragment(String key, String text) {
	}

	public enum Kind {NEXT, ARRIVING, PLATFORM_APPROACHING, PLATFORM_ARRIVING, PLATFORM_DELAYED}
}
