package org.mtr.aurelia.pack;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Finds {@code aurelia_packs/*.json} in loaded resource packs (client assets) and data packs (server data), validates
 * them with {@link PackLoader}, and keeps the last result for the Aurelia panel. Reloads with F3+T or a resource pack
 * change, like any other resource.
 */
public final class AureliaPacks {

	public static final String FOLDER = "aurelia_packs";
	private static final Logger LOGGER = LoggerFactory.getLogger("ATR Aurelia");

	private static volatile PackLoadResult client = PackLoadResult.EMPTY;
	private static volatile PackLoadResult server = PackLoadResult.EMPTY;
	private static volatile PackLoadResult merged = PackLoadResult.EMPTY;

	private AureliaPacks() {
	}

	public static void registerClient() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new Listener(true));
	}

	public static void registerServer() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new Listener(false));
	}

	public static List<LoadedPack> packs() {
		return merged.packs();
	}

	public static List<PackDiagnostic> diagnostics() {
		return merged.diagnostics();
	}

	public static int errorCount() {
		return merged.errorCount();
	}

	public static int warningCount() {
		return merged.warningCount();
	}

	private static synchronized void set(boolean resourcePack, PackLoadResult result) {
		if (resourcePack) {
			client = result;
		} else {
			server = result;
		}
		merged = client.and(server);
	}

	private static final class Listener implements SimpleSynchronousResourceReloadListener {

		private final boolean resourcePack;

		private Listener(boolean resourcePack) {
			this.resourcePack = resourcePack;
		}

		@Override
		public Identifier getFabricId() {
			return new Identifier("mtr", resourcePack ? "aurelia_packs_client" : "aurelia_packs_server");
		}

		@Override
		public void reload(ResourceManager manager) {
			if (!PackSettings.enabled()) {
				set(resourcePack, PackLoadResult.EMPTY);
				return;
			}

			final PackLoader.Kind kind = resourcePack ? PackLoader.Kind.RESOURCE_PACK : PackLoader.Kind.DATA_PACK;
			final List<PackLoader.Source> sources = new ArrayList<>();
			final Map<Identifier, Resource> found = manager.findResources(FOLDER, id -> id.getPath().endsWith(".json"));
			for (final Map.Entry<Identifier, Resource> entry : found.entrySet()) {
				try (InputStream inputStream = entry.getValue().getInputStream()) {
					sources.add(new PackLoader.Source(entry.getKey().toString(), kind, new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)));
				} catch (IOException e) {
					sources.add(new PackLoader.Source(entry.getKey().toString(), kind, "", e.toString()));
				}
			}
			sources.sort(Comparator.comparing(PackLoader.Source::file));

			final PackLoadResult result = PackLoader.load(sources, profile -> {
				if (!resourcePack) {
					// SN's profiles are client assets; a data pack cannot see them, so absence means nothing here.
					return true;
				}
				final int colon = profile.indexOf(':');
				if (colon <= 0 || colon >= profile.length() - 1) {
					return false;
				}
				try {
					return manager.getResource(new Identifier(profile.substring(0, colon), "aurelia_sound_profiles/" + profile.substring(colon + 1) + ".json")).isPresent();
				} catch (RuntimeException e) {
					return false;
				}
			});
			set(resourcePack, result);
			LOGGER.info("[ATR] Loaded {} pack(s) from {} ({} error(s), {} warning(s))", result.packs().size(), resourcePack ? "resource packs" : "data packs", result.errorCount(), result.warningCount());
		}
	}
}
