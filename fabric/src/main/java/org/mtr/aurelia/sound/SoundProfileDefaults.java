package org.mtr.aurelia.sound;

import org.mtr.core.data.TransportMode;
import org.mtr.mod.client.CustomResourceLoader;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A vehicle entry in {@code mtr_custom_resources.json} can name its default sound profile with a tag
 * {@code aurelia_sound:<profile id>} (for example {@code aurelia_sound:mtr:uk_emu}). The extra colon keeps MTR's own
 * tag filter ignoring it, so MTR and old packs load as before. The player's own choice still wins over the tag.
 */
public final class SoundProfileDefaults {

	public static final String TAG_PREFIX = "aurelia_sound:";

	private SoundProfileDefaults() {
	}

	/**
	 * The profile id from a vehicle's tags, or empty when no tag names one. The last matching tag wins and a blank
	 * suffix is ignored, so {@code aurelia_sound:} alone changes nothing.
	 */
	public static String fromTags(@Nullable List<String> tags) {
		if (tags == null) {
			return "";
		}
		String result = "";
		for (final String tag : tags) {
			if (tag != null && tag.startsWith(TAG_PREFIX)) {
				final String id = tag.substring(TAG_PREFIX.length()).trim();
				if (!id.isEmpty()) {
					result = id;
				}
			}
		}
		return result;
	}

	/**
	 * The default profile id of one vehicle type, or empty when the type is unknown or names none. Reads the tags of
	 * the loaded {@code VehicleResource}; unknown types keep their own sound.
	 */
	public static String forVehicleType(TransportMode transportMode, @Nullable String vehicleTypeId) {
		if (vehicleTypeId == null || vehicleTypeId.isBlank()) {
			return "";
		}
		final String[] found = {""};
		CustomResourceLoader.getVehicleById(transportMode, vehicleTypeId, details -> found[0] = details.left().getAureliaDefaultSoundProfile());
		return found[0];
	}
}
