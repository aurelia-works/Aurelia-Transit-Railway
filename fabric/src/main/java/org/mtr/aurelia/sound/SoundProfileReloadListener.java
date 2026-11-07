package org.mtr.aurelia.sound;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads every {@code assets/<namespace>/aurelia_sound_profiles/*.json} on resource reload, so a pack can add profiles
 * next to its trains or in a pack of its own. The profile id is {@code <namespace>:<file name>}.
 */
public final class SoundProfileReloadListener implements SimpleSynchronousResourceReloadListener {

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR Aurelia");
	private static final String DIRECTORY = "aurelia_sound_profiles";
	private static final String PREFIX = DIRECTORY + "/";
	private static final String SUFFIX = ".json";

	private SoundProfileReloadListener() {
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SoundProfileReloadListener());
	}

	@Override
	public Identifier getFabricId() {
		return new Identifier("aurelia", "sound_profiles");
	}

	@Override
	public void reload(ResourceManager manager) {
		final Map<String, SoundProfile> loaded = new HashMap<>();
		manager.findResources(DIRECTORY, path -> path.getPath().startsWith(PREFIX) && path.getPath().endsWith(SUFFIX)).forEach((identifier, resource) -> {
			final String path = identifier.getPath();
			final String profileId = identifier.getNamespace() + ":" + path.substring(PREFIX.length(), path.length() - SUFFIX.length());
			try (final InputStream inputStream = resource.getInputStream()) {
				final SoundProfile profile = SoundProfileJson.parse(profileId, new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
				if (profile == null) {
					LOGGER.warn("[ATR] Skipping sound profile {}: not valid JSON", profileId);
				} else {
					loaded.put(profileId, profile);
				}
			} catch (Exception e) {
				LOGGER.warn("[ATR] Skipping sound profile {}: {}", profileId, e.getMessage());
			}
		});
		SoundProfileRegistry.replace(loaded);
		LOGGER.info("[ATR] Loaded {} sound profiles", loaded.size());
	}
}
