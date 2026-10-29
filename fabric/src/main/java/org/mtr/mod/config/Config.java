package org.mtr.mod.config;

import org.apache.commons.io.FileUtils;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.mtr.mod.Init;
import org.mtr.mod.generated.config.ConfigSchema;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public final class Config extends ConfigSchema {

	private static Config instance;
	private static Path basePath;
	/** Last-modified time of config/mtr.json last applied to the ticket keys, or {@link Long#MIN_VALUE} if none. */
	private static long ticketSettingsStamp = Long.MIN_VALUE;

	public static void init(File baseFolder) {
		if (instance == null || basePath == null) {
			basePath = baseFolder.toPath();
			try (final InputStream inputStream = Files.newInputStream(getConfigFilePath(), StandardOpenOption.READ)) {
				instance = new Config(new JsonReader(readResource(inputStream)));
			} catch (Exception ignored) {
				instance = new Config(new JsonReader(new JsonObject()));
			}
		}
		save();
	}

	private Config(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public static JsonElement readResource(InputStream inputStream) {
		try (final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(inputStreamReader);
		} catch (Exception e) {
			Init.LOGGER.error("", e);
			return new JsonObject();
		}
	}

	public static Client getClient() {
		return instance == null ? new Client(new JsonReader(new JsonObject())) : instance.client;
	}

	public static Server getServer() {
		return instance == null ? new Server(new JsonReader(new JsonObject())) : instance.server;
	}

	public static void save() {
		// Pick up an admin's ticket edits before rewriting the file, so a client options save cannot clobber them.
		refreshTicketSettings();
		try {
			FileUtils.write(getConfigFilePath().toFile(), Utilities.prettyPrint(Utilities.getJsonObjectFromData(instance)), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Init.LOGGER.error("", e);
		}
	}

	/**
	 * Re-reads {@code ticketCurrency} and {@code freeTicketBarriers} when {@code config/mtr.json} has changed.
	 * Other server keys stay as they were at startup, matching the webserver port and the threading flags.
	 * Safe to call on every ticket purchase or barrier pass: the file is parsed only after its timestamp changes.
	 */
	public static void refreshTicketSettings() {
		if (instance == null || basePath == null) {
			return;
		}
		final Path path = getConfigFilePath();
		final long stamp;
		try {
			stamp = Files.getLastModifiedTime(path).toMillis();
		} catch (Exception e) {
			return;
		}
		if (stamp == ticketSettingsStamp) {
			return;
		}
		try (final InputStream inputStream = Files.newInputStream(path); final InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
			final JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				Init.LOGGER.warn("config/mtr.json is not an object; ticket settings were left unchanged");
				ticketSettingsStamp = stamp;
				return;
			}
			JsonObject serverObject = new JsonObject();
			final JsonElement serverElement = root.getAsJsonObject().get("server");
			if (serverElement != null && serverElement.isJsonObject()) {
				serverObject = serverElement.getAsJsonObject();
			}
			instance.server.copyTicketSettingsFrom(new Server(new JsonReader(serverObject)));
			ticketSettingsStamp = stamp;
		} catch (Exception e) {
			Init.LOGGER.error("Could not reload ticket settings from config/mtr.json", e);
			ticketSettingsStamp = stamp;
		}
	}

	/** Drops the loaded config so tests can point {@link #init} at another folder. */
	static void resetForTesting() {
		instance = null;
		basePath = null;
		ticketSettingsStamp = Long.MIN_VALUE;
	}

	private static Path getConfigFilePath() {
		return basePath == null ? Paths.get("") : basePath.resolve("config/mtr.json");
	}
}
