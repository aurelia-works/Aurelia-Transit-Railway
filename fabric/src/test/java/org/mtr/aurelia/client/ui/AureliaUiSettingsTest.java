package org.mtr.aurelia.client.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.aurelia.client.ui.AureliaUiSettings.ScreenId;
import org.mtr.libraries.com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AureliaUiSettingsTest {

	@Test
	public void missingAndNonBooleanKeysStayOn() {
		final JsonObject json = new JsonObject();
		json.addProperty("dashboard", false);
		json.addProperty("route", "yes");
		json.addProperty("somewhere", true);
		final AureliaUiSettings.Values values = AureliaUiSettings.from(json);
		assertFalse(values.enabled(ScreenId.DASHBOARD));
		assertTrue(values.enabled(ScreenId.STATION));
		assertTrue(values.enabled(ScreenId.ROUTE));
		assertTrue(values.enabled(ScreenId.DEPOT));
		assertTrue(values.enabled(ScreenId.SIDING));
		assertTrue(values.enabled(ScreenId.OPTIONS));
		assertTrue(values.enabled(ScreenId.PANEL));
		final JsonObject written = values.toJson();
		assertFalse(written.get("dashboard").getAsBoolean());
		assertTrue(written.get("station").getAsBoolean());
		assertFalse(written.has("somewhere"));
		assertFalse(AureliaUiSettings.from(written).enabled(ScreenId.DASHBOARD));
		assertTrue(AureliaUiSettings.from(written).enabled(ScreenId.PANEL));
	}

	@Test
	public void missingOrBrokenFileLeavesEveryScreenOn(@TempDir Path config) {
		AureliaUiSettings.load(config);
		for (final ScreenId id : ScreenId.values()) {
			assertTrue(AureliaUiSettings.enabled(id));
		}
		final Path file = config.resolve("aurelia").resolve("ui.json");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, "{not json");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		AureliaUiSettings.load(config);
		for (final ScreenId id : ScreenId.values()) {
			assertTrue(AureliaUiSettings.enabled(id));
		}
	}
}
