package org.mtr.aurelia.client.map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.WorldSavePath;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * The in-game map: the key that opens {@link WorldMapScreen}, the {@link MinimapHud}, and the map of the world the
 * player is in. Each world (a single-player save, or a server address) and dimension gets its own folder under
 * {@code <game folder>/aurelia/map/}; nothing is written into the world save, so worlds load exactly as before.
 */
public final class MapClient {

	private static final long NETWORK_MILLIS = 2_000;
	private static final long SAVE_MILLIS = 60_000;

	private static final ChunkScanner SCANNER = new ChunkScanner();
	private static final MapTextures TEXTURES = new MapTextures();
	private static KeyBinding openKey;
	@Nullable
	private static ExploredMap map;
	@Nullable
	private static SeenNetwork network;
	@Nullable
	private static ClientWorld world;
	private static long nextNetwork;
	private static long nextSave;

	private MapClient() {
	}

	public static void register() {
		MapSettings.load(FabricLoader.getInstance().getConfigDir());
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.map.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.mtr"));
		ClientTickEvents.END_CLIENT_TICK.register(MapClient::tick);
		HudRenderCallback.EVENT.register((context, tickDelta) -> MinimapHud.render(context));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(MapClient::close));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
	}

	@Nullable
	public static ExploredMap map() {
		return map;
	}

	@Nullable
	public static SeenNetwork network() {
		return network;
	}

	public static MapTextures textures() {
		return TEXTURES;
	}

	public static KeyBinding openKey() {
		return openKey;
	}

	public static void open(MinecraftClient client) {
		if (client.currentScreen == null && map != null) {
			client.setScreen(new WorldMapScreen());
		}
	}

	private static void tick(MinecraftClient client) {
		if (client.world != world) {
			world = client.world;
			if (world == null) {
				close();
			} else {
				use(folder(client, world));
			}
		}
		while (openKey.wasPressed()) {
			open(client);
		}
		if (map == null || network == null || world == null || client.player == null) {
			return;
		}
		SCANNER.tick(world, client.player, map, MapSettings.scanPerTick(), client.options.getClampedViewDistance());
		final long now = System.currentTimeMillis();
		if (now >= nextNetwork) {
			nextNetwork = now + NETWORK_MILLIS;
			network.refresh(client.player.getX(), client.player.getZ());
		}
		if (now >= nextSave) {
			nextSave = now + SAVE_MILLIS;
			map.saveChanged();
			network.saveIfChanged();
		}
	}

	/** Keeps the open map when the folder is the same (a respawn makes a new world object), else swaps maps. */
	private static void use(Path folder) {
		if (map != null && map.folder().equals(folder)) {
			return;
		}
		close();
		map = new ExploredMap(folder, TEXTURES::drop);
		network = new SeenNetwork(folder);
		SCANNER.reset();
		nextSave = System.currentTimeMillis() + SAVE_MILLIS;
	}

	private static void close() {
		if (map != null) {
			map.close();
			map = null;
		}
		if (network != null) {
			network.saveIfChanged();
			network = null;
		}
		TEXTURES.clear();
		SCANNER.reset();
	}

	private static Path folder(MinecraftClient client, ClientWorld world) {
		final String worldName;
		if (client.isIntegratedServerRunning() && client.getServer() != null) {
			final Path save = client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
			worldName = "local_" + (save.getFileName() == null ? "world" : save.getFileName().toString());
		} else {
			final ServerInfo server = client.getCurrentServerEntry();
			worldName = "server_" + (server == null ? "unknown" : server.address);
		}
		return FabricLoader.getInstance().getGameDir().resolve("aurelia").resolve("map")
				.resolve(safe(worldName)).resolve(safe(world.getRegistryKey().getValue().toString()));
	}

	private static String safe(String name) {
		final String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
		return cleaned.isEmpty() || cleaned.chars().allMatch(c -> c == '.') ? "_" : cleaned;
	}
}
