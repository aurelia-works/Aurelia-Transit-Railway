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
 * The generic example private pack, a built-in resource pack that is off by default. It shows every section with
 * made-up names, so the format can be tried without touching a real pack. See {@code docs/private-packs.md}.
 */
public final class AureliaExamplePack {

	public static final Identifier ID = new Identifier("mtr", "example_private_pack");
	/** Fabric names a built-in pack profile "namespace/path". */
	private static final String PROFILE = ID.getNamespace() + "/" + ID.getPath();

	private AureliaExamplePack() {
	}

	public static void register() {
		final Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer("mtr");
		if (mod.isPresent()) {
			ResourceManagerHelper.registerBuiltinResourcePack(ID, mod.get(), Text.translatable("options.mtr.example_private_pack"), ResourcePackActivationType.NORMAL);
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
