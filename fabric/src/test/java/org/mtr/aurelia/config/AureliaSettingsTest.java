package org.mtr.aurelia.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.aurelia.client.PassengerDrawSettings;
import org.mtr.aurelia.client.map.MapSettings;
import org.mtr.aurelia.client.ui.AureliaUiSettings;
import org.mtr.aurelia.client.ui.AureliaUiSettings.ScreenId;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one settings file must never lose a player's old settings: a folder it cannot write stays exactly as it was, a
 * half-readable folder keeps its working file for the next start, and a finished move is not undone by an old file that
 * comes back.
 */
public final class AureliaSettingsTest {

	/** A damaged file is kept, not written over: one typo must not silently reset ten features. */
	@Test
	public void unreadableFileIsKeptAsideInsteadOfOverwritten(@TempDir Path config) throws Exception {
		final Path dir = config.resolve("aurelia");
		Files.createDirectories(dir);
		final byte[] damaged = "{\"version\":1,\"map\":{\"size\":".getBytes(StandardCharsets.UTF_8);
		Files.write(dir.resolve("settings.json"), damaged);

		AureliaSettings.load(config);

		final Path kept = dir.resolve("settings.json.unreadable");
		assertTrue(Files.isRegularFile(kept), "the damaged bytes must survive");
		assertArrayEquals(damaged, Files.readAllBytes(kept));
		assertEquals(1, AureliaSettings.version(), "a fresh file is written once the damaged one is safe");
		assertTrue(Files.isRegularFile(dir.resolve("settings.json")));
	}

	@Test
	public void readOnlyFolderKeepsTheOldFilesAndStillUsesTheirValues(@TempDir Path config) throws Exception {
		final Path dir = config.resolve("aurelia");
		Files.createDirectories(dir);
		final byte[] existing = "{\"version\":0}".getBytes(StandardCharsets.UTF_8);
		Files.write(dir.resolve("settings.json"), existing);
		final byte[] map = "{\"size\":150,\"scanPerTick\":12}".getBytes(StandardCharsets.UTF_8);
		Files.write(dir.resolve("map.json"), map);
		Files.writeString(dir.resolve("passenger-draw.json"), "{\"draw\":false,\"drawDistance\":96}", StandardCharsets.UTF_8);
		final Set<java.nio.file.attribute.PosixFilePermission> writable = Files.getPosixFilePermissions(dir);
		Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
		try {
			AureliaSettings.load(config);
			assertEquals(0, AureliaSettings.version());
			assertArrayEquals(existing, Files.readAllBytes(dir.resolve("settings.json")));
			assertArrayEquals(map, Files.readAllBytes(dir.resolve("map.json")));
			assertTrue(Files.isRegularFile(dir.resolve("passenger-draw.json")));
			assertFalse(Files.exists(dir.resolve("map.json.migrated-v1")));

			MapSettings.load(config);
			assertEquals(150, MapSettings.size());
			assertEquals(12, MapSettings.scanPerTick());
			PassengerDrawSettings.load(config);
			assertFalse(PassengerDrawSettings.get().draw());
			assertEquals(96, PassengerDrawSettings.get().drawDistance());
		} finally {
			Files.setPosixFilePermissions(dir, writable);
		}
	}

	@Test
	public void malformedOldFileLeavesItsSectionOnDefaultsAndHoldsTheVersion(@TempDir Path config) throws Exception {
		final Path dir = config.resolve("aurelia");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("map.json"), "{\"size\":150,\"minimap\":false}", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("ui.json"), "{not json", StandardCharsets.UTF_8);

		AureliaSettings.load(config);
		assertEquals(0, AureliaSettings.version());

		MapSettings.load(config);
		assertEquals(150, MapSettings.size());
		assertFalse(MapSettings.minimap());
		AureliaUiSettings.load(config);
		assertTrue(AureliaUiSettings.enabled(ScreenId.DASHBOARD));

		final JsonObject written = JsonParser.parseString(Files.readString(dir.resolve("settings.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(150, written.getAsJsonObject("map").get("size").getAsInt());
		assertFalse(written.has("ui"));
		assertTrue(Files.isRegularFile(dir.resolve("map.json.migrated-v1")));
		assertTrue(Files.isRegularFile(dir.resolve("ui.json")));
		assertFalse(Files.exists(dir.resolve("ui.json.migrated-v1")));
	}

	@Test
	public void versionOneFileWinsOverARestoredOldFile(@TempDir Path config) throws Exception {
		final Path dir = config.resolve("aurelia");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("settings.json"), "{\"version\":1,\"map\":{\"size\":150,\"zoom\":200}}", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("map.json"), "{\"size\":80,\"zoom\":50}", StandardCharsets.UTF_8);

		AureliaSettings.load(config);
		assertEquals(1, AureliaSettings.version());
		MapSettings.load(config);
		assertEquals(150, MapSettings.size());
		assertEquals(200, MapSettings.zoom());

		assertTrue(Files.isRegularFile(dir.resolve("map.json")));
		assertFalse(Files.exists(dir.resolve("map.json.migrated-v1")));
	}
}
