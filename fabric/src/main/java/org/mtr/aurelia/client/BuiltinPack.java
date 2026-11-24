package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * A built-in resource pack shipped in the mod, off by default. Minecraft keeps the on/off choice in options.txt with the
 * other resource packs, so no config key is needed.
 */
final class BuiltinPack {

	private final Identifier id;
	/** Fabric names a built-in pack profile "namespace/path". */
	private final String profile;

	BuiltinPack(Identifier id) {
		this.id = id;
		profile = id.getNamespace() + "/" + id.getPath();
	}

	void register(String nameKey) {
		final Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer("mtr");
		if (mod.isPresent()) {
			ResourceManagerHelper.registerBuiltinResourcePack(id, mod.get(), Text.translatable(nameKey), ResourcePackActivationType.NORMAL);
		}
	}

	boolean enabled() {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.getResourcePackManager().getEnabledNames().contains(profile);
	}

	/** Turns the pack on or off, saves the choice to options.txt and reloads resources. */
	void toggle() {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}
		final ResourcePackManager manager = client.getResourcePackManager();
		if (manager.getEnabledNames().contains(profile)) {
			manager.disable(profile);
		} else if (!manager.enable(profile)) {
			return;
		}
		client.options.refreshResourcePacks(manager);
	}
}
