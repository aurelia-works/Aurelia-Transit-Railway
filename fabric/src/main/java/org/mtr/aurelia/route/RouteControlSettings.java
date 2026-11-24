package org.mtr.aurelia.route;

import net.fabricmc.loader.api.FabricLoader;
import org.mtr.aurelia.config.AureliaSettings;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The panel's master switch for fallback platforms (client-side, like the Announcer switches). Off means no route
 * resolves a fallback platform; per-route flags still sync and still show in the editor. Defaults to on: routes
 * without a fallback flag (every old route) behave exactly as before either way. It lives in the
 * {@code route_control} section of {@code config/aurelia/settings.json} (formerly {@code route_control.json}).
 */
public final class RouteControlSettings {

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
		AureliaSettings.load(configDir);
		try {
			apply(AureliaSettings.section(AureliaSettings.ROUTE_CONTROL));
		} catch (Exception e) {
			fallbackMaster = true;
		}
	}

	/** Reads the old {@code route_control.json} into the section body settings.json keeps. */
	public static JsonObject readLegacy(Path file) throws IOException {
		apply(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
		return toJson();
	}

	private static void apply(JsonObject root) {
		fallbackMaster = !root.has(KEY_FALLBACK) || root.get(KEY_FALLBACK).getAsBoolean();
	}

	private static void save() {
		AureliaSettings.put(AureliaSettings.ROUTE_CONTROL, toJson());
	}

	private static JsonObject toJson() {
		final JsonObject root = new JsonObject();
		root.addProperty(KEY_FALLBACK, fallbackMaster);
		return root;
	}
}
