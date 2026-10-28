package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BrandingTest {

	@Test
	public void modMetadataKeepsIdAndMtrVersionRange() throws IOException {
		final String json = read("/fabric.mod.json");
		Assertions.assertEquals("mtr", field(json, "id"));
		Assertions.assertEquals("Aurelia Transit Railway", field(json, "name"));

		// Add-ons pin mtr >=4.0.5 <4.1.0; semver ignores the +atr build suffix
		final Matcher matcher = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)\\+atr\\.\\d+\\.\\d+\\.\\d+$").matcher(field(json, "version"));
		Assertions.assertTrue(matcher.matches());
		Assertions.assertEquals("4", matcher.group(1));
		Assertions.assertEquals("0", matcher.group(2));
		Assertions.assertTrue(Integer.parseInt(matcher.group(3)) >= 5);
	}

	@Test
	public void modMetadataPeopleListsAreArrays() throws IOException {
		// Fabric Loader refuses to start if authors or contributors is not an array
		final com.google.gson.JsonObject root = JsonParser.parseString(read("/fabric.mod.json")).getAsJsonObject();
		for (final String key : new String[]{"authors", "contributors"}) {
			if (root.has(key)) {
				Assertions.assertTrue(root.get(key).isJsonArray(), key);
			}
		}
		Assertions.assertTrue(root.get("authors").toString().contains("jonafanho"), "original author credited");
	}

	@Test
	public void configScreenLogoExists() {
		Assertions.assertNotNull(BrandingTest.class.getResource("/assets/mtr/textures/gui/atr_logo.png"));
		Assertions.assertNotNull(BrandingTest.class.getResource("/icon.png"));
	}

	private static String field(String json, String key) {
		final Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
		Assertions.assertTrue(matcher.find(), key);
		return matcher.group(1);
	}

	private static String read(String path) throws IOException {
		try (final InputStream inputStream = BrandingTest.class.getResourceAsStream(path)) {
			Assertions.assertNotNull(inputStream, path);
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
