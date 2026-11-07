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
 * The Aurelia block look: a built-in resource pack that repaints MTR's blocks, items, rails and signals under the same ids.
 * Off by default. Minecraft keeps the on/off choice in options.txt with the other resource packs, so no config key is needed,
 * and old worlds are untouched because only textures and models change.
 */
public final class AureliaLookPack {

	public static final Identifier ID = new Identifier("mtr", "aurelia_look");
	/** Fabric names a built-in pack profile "namespace/path". */
	static final String PROFILE = ID.getNamespace() + "/" + ID.getPath();

	private AureliaLookPack() {
	}

	public static void register() {
		final Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer("mtr");
		if (mod.isPresent()) {
			ResourceManagerHelper.registerBuiltinResourcePack(ID, mod.get(), Text.translatable("options.mtr.aurelia_block_look.pack"), ResourcePackActivationType.NORMAL);
		}
	}

	public static boolean enabled() {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.getResourcePackManager().getEnabledNames().contains(PROFILE);
	}

	/** Turns the pack on or off, saves the choice to options.txt and reloads resources. */
	public static void toggle() {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}
		final ResourcePackManager manager = client.getResourcePackManager();
		if (manager.getEnabledNames().contains(PROFILE)) {
			manager.disable(PROFILE);
		} else if (!manager.enable(PROFILE)) {
			return;
		}
		client.options.refreshResourcePacks(manager);
	}
}
