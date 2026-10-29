package org.mtr.aurelia.client;

import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * One-time import of Aurelia Transit Utilities settings on the first start of ATR. The old files are left alone; a
 * marker in {@code config/aurelia} stops it from running again.
 */
public final class AtuImport {

	static final String OLD_DIR = "aurelia_transit_utilities";
	static final String OLD_PERF_FILE = "aurelia_transit_utilities_performance.properties";
	static final String MARKER = ".atu-imported";
	static final List<String> FILES = List.of("announcer.properties", "handshake.properties", "train_presets.json");
	/** ATU's performance keys that ATR's client config has too; ATU's {@code debugOverlay} is ATR's F3 switch. */
	static final List<String> PERF_KEYS = List.of("mergeTrainParts", "shaderPackFastPath", "cheapShadows", "fasterModelLoading", "backgroundModelLoading", "fasterDoorChecks", "freeUnusedModels", "debugOverlay", "trainInteriorChunks", "trainBoxChunks", "railSimpleChunks", "railFarChunks");

	private AtuImport() {
	}

	public static void run(Path configDir) {
		try {
			final Path newDir = configDir.resolve(AureliaClient.MOD_ID);
			if (Files.exists(newDir.resolve(MARKER))) {
				return;
			}
			final int files = copyFiles(configDir);
			final Map<String, String> perf = readPerf(configDir.resolve(OLD_PERF_FILE));
			if (files == 0 && perf.isEmpty()) {
				return;
			}
			final int settings = applyPerf(Config.getClient(), perf);
			if (settings > 0) {
				Config.save();
			}
			Files.createDirectories(newDir);
			Files.writeString(newDir.resolve(MARKER), "imported from " + OLD_DIR + "\n");
			AureliaClient.LOGGER.info("[ATR] imported {} ATU settings", files + settings);
		} catch (IOException | RuntimeException e) {
			AureliaClient.LOGGER.error("[ATR] Could not import ATU settings", e);
		}
	}

	/** Copies ATU's files into {@code config/aurelia} where ATR has none yet. Returns how many were copied. */
	static int copyFiles(Path configDir) throws IOException {
		final Path oldDir = configDir.resolve(OLD_DIR);
		final Path newDir = configDir.resolve(AureliaClient.MOD_ID);
		int copied = 0;
		for (final String name : FILES) {
			final Path from = oldDir.resolve(name);
			final Path to = newDir.resolve(name);
			if (Files.isRegularFile(from) && !Files.exists(to)) {
				Files.createDirectories(newDir);
				Files.copy(from, to);
				copied++;
			}
		}
		return copied;
	}

	/** The known keys of ATU's performance file, trimmed; unknown keys and an unreadable file give nothing. */
	static Map<String, String> readPerf(Path file) {
		final Map<String, String> values = new LinkedHashMap<>();
		if (!Files.isRegularFile(file)) {
			return values;
		}
		final Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException | IllegalArgumentException e) {
			return values;
		}
		for (final String key : PERF_KEYS) {
			final String value = properties.getProperty(key);
			if (value != null) {
				values.put(key, value.trim());
			}
		}
		return values;
	}

	/** Sets ATR's matching switches and distances. Returns how many values were taken. */
	static int applyPerf(Client client, Map<String, String> perf) {
		int applied = 0;
		for (final Map.Entry<String, String> entry : perf.entrySet()) {
			final String value = entry.getValue();
			final boolean taken = switch (entry.getKey()) {
				case "mergeTrainParts" -> flag(value, client.getMergeTrainParts(), client::toggleMergeTrainParts);
				case "shaderPackFastPath" -> flag(value, client.getShaderPackFastPath(), client::toggleShaderPackFastPath);
				case "cheapShadows" -> flag(value, client.getCheapShadows(), client::toggleCheapShadows);
				case "fasterModelLoading" -> flag(value, client.getFasterModelLoading(), client::toggleFasterModelLoading);
				case "backgroundModelLoading" -> flag(value, client.getBackgroundModelLoading(), client::toggleBackgroundModelLoading);
				case "fasterDoorChecks" -> flag(value, client.getFasterDoorChecks(), client::toggleFasterDoorChecks);
				case "freeUnusedModels" -> flag(value, client.getFreeUnusedModels(), client::toggleFreeUnusedModels);
				case "debugOverlay" -> flag(value, client.getPerfOnF3(), client::togglePerfOnF3);
				case "trainInteriorChunks" -> chunks(value, Client.TRAIN_INTERIOR_CHUNKS_MAX, client::setTrainInteriorChunks);
				case "trainBoxChunks" -> chunks(value, Client.TRAIN_BOX_CHUNKS_MAX, client::setTrainBoxChunks);
				case "railSimpleChunks" -> chunks(value, Client.RAIL_SIMPLE_CHUNKS_MAX, client::setRailSimpleChunks);
				case "railFarChunks" -> chunks(value, Client.RAIL_FAR_CHUNKS_MAX, client::setRailFarChunks);
				default -> false;
			};
			if (taken) {
				applied++;
			}
		}
		return applied;
	}

	private static boolean flag(String value, boolean current, Runnable toggle) {
		final boolean wanted;
		if (value.equalsIgnoreCase("true")) {
			wanted = true;
		} else if (value.equalsIgnoreCase("false")) {
			wanted = false;
		} else {
			return false;
		}
		if (wanted != current) {
			toggle.run();
		}
		return true;
	}

	private static boolean chunks(String value, int max, java.util.function.IntConsumer setter) {
		try {
			final int chunks = Integer.parseInt(value);
			if (chunks < 0) {
				return false;
			}
			setter.accept(Math.min(chunks, max));
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
