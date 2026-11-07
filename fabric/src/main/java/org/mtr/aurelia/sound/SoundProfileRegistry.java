package org.mtr.aurelia.sound;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The profiles the resource reload found. Replaced as a whole on every reload. A missing profile id resolves to null,
 * which means "leave the train's own sound alone".
 */
public final class SoundProfileRegistry {

	private static volatile Map<String, SoundProfile> profiles = Collections.emptyMap();
	private static volatile List<SoundProfile> ordered = Collections.emptyList();
	private static volatile int version;

	private SoundProfileRegistry() {
	}

	public static void replace(Map<String, SoundProfile> next) {
		final List<SoundProfile> list = new ArrayList<>(next.values());
		list.sort(Comparator.comparing(profile -> profile.id));
		final Map<String, SoundProfile> map = new LinkedHashMap<>();
		for (final SoundProfile profile : list) {
			map.put(profile.id, profile);
		}
		profiles = Collections.unmodifiableMap(map);
		ordered = Collections.unmodifiableList(list);
		version++;
	}

	public static int version() {
		return version;
	}

	public static List<SoundProfile> all() {
		return ordered;
	}

	@Nullable
	public static SoundProfile find(@Nullable String id) {
		return id == null || id.isBlank() ? null : profiles.get(id);
	}

	/**
	 * The profile after {@code current} in id order, or the first one. An empty string means no profile; after the last
	 * profile the cycle returns there. An unknown id restarts at the first profile.
	 */
	public static String nextId(@Nullable String current) {
		final List<SoundProfile> list = ordered;
		if (list.isEmpty()) {
			return "";
		}
		if (current == null || current.isBlank()) {
			return list.get(0).id;
		}
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id.equals(current)) {
				return i + 1 < list.size() ? list.get(i + 1).id : "";
			}
		}
		return list.get(0).id;
	}
}
