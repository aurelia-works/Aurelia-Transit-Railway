package org.mtr.aurelia.route;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.mtr.aurelia.client.AureliaClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The panel's master switch for fallback platforms (client-side, like the Announcer switches). Off means no route
 * resolves a fallback platform; per-route flags still sync and still show in the editor. Defaults to on: routes
 * without a fallback flag (every old route) behave exactly as before either way.
 */
public final class RouteControlSettings {

	private static final String FILE = "route_control.json";
	private static final String KEY_FALLBACK = "fallback_master";

	private static boolean fallbackMaster = true;

	private RouteControlSettings() {
	}

	public static boolean fallbackMaster() {
		return fallbackMaster;
	}

	public static void setFallbackMaster(boolean value) {
		fallbackMaster = value;
		save();
		if (net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler() != null) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(org.mtr.mod.data.RouteControlSync.MASTER, org.mtr.mod.data.RouteControlSync.requestMaster(value));
		}
	}

	/** The server's value after a sync; saved so the panel and the next edit send the same value. */
	public static void applyRemote(boolean value) {
		if (fallbackMaster != value) {
			fallbackMaster = value;
			save();
		}
	}

	public static void load() {
		load(FabricLoader.getInstance().getConfigDir());
	}

	static void load(Path configDir) {
		final Path file = configDir.resolve("aurelia").resolve(FILE);
		if (!Files.isRegularFile(file)) {
			fallbackMaster = true;
			return;
		}
		try {
			final JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			fallbackMaster = !root.has(KEY_FALLBACK) || root.get(KEY_FALLBACK).getAsBoolean();
		} catch (Exception e) {
			AureliaClient.LOGGER.error("[ATR] route_control.json could not be read; fallback master stays on", e);
			fallbackMaster = true;
		}
	}

	private static void save() {
		final Path file = FabricLoader.getInstance().getConfigDir().resolve("aurelia").resolve(FILE);
		try {
			Files.createDirectories(file.getParent());
			final JsonObject root = new JsonObject();
			root.addProperty(KEY_FALLBACK, fallbackMaster);
			Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			AureliaClient.LOGGER.error("[ATR] route_control.json could not be written", e);
		}
	}
}
