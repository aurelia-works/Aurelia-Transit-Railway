package org.mtr.mod.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.core.serializer.JsonReader;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.mtr.mod.data.TicketRules;
import org.mtr.mod.data.TicketSystem;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Existing config files gain the new keys at their old behaviour, and later edits are picked up without a restart. */
class ConfigTicketSettingsTest {

	@TempDir
	Path folder;

	@BeforeEach
	void reset() {
		Config.resetForTesting();
	}

	@AfterEach
	void cleanup() {
		Config.resetForTesting();
	}

	@Test
	void emptyServerSectionKeepsEmeraldAndPaidBarriers() {
		final Server server = new Server(new JsonReader(new JsonObject()));
		assertEquals(TicketRules.DEFAULT_CURRENCY_ID, server.getTicketCurrency());
		assertFalse(server.getFreeTicketBarriers());
		assertEquals(8888, server.getWebserverPort());
	}

	@Test
	void savedWorldWithoutTheKeysKeepsTheOldBehaviour() throws Exception {
		write("{\"client\":{},\"server\":{\"webserverPort\":9999}}");
		Config.init(folder.toFile());
		assertEquals(TicketRules.DEFAULT_CURRENCY_ID, Config.getServer().getTicketCurrency());
		assertFalse(Config.getServer().getFreeTicketBarriers());
		assertEquals(9999, Config.getServer().getWebserverPort());
		assertFalse(TicketSystem.freePassage());

		final JsonObject written = JsonParser.parseString(Files.readString(configFile(), StandardCharsets.UTF_8)).getAsJsonObject();
		final JsonObject server = written.getAsJsonObject("server");
		assertEquals(TicketRules.DEFAULT_CURRENCY_ID, server.get("ticketCurrency").getAsString());
		assertFalse(server.get("freeTicketBarriers").getAsBoolean());
		assertEquals(9999, server.get("webserverPort").getAsInt());
	}

	@Test
	void editingTheFileAppliesOnTheNextReadAndLeavesOtherServerKeys() throws Exception {
		write("{\"client\":{},\"server\":{\"webserverPort\":9999}}");
		Config.init(folder.toFile());

		final JsonObject root = JsonParser.parseString(Files.readString(configFile(), StandardCharsets.UTF_8)).getAsJsonObject();
		final JsonObject server = root.getAsJsonObject("server");
		server.addProperty("ticketCurrency", "coins:gold");
		server.addProperty("freeTicketBarriers", true);
		Files.writeString(configFile(), root.toString(), StandardCharsets.UTF_8);
		Files.setLastModifiedTime(configFile(), FileTime.fromMillis(System.currentTimeMillis() + 10_000));

		Config.refreshTicketSettings();
		assertEquals("coins:gold", Config.getServer().getTicketCurrency());
		assertTrue(Config.getServer().getFreeTicketBarriers());
		assertTrue(TicketSystem.freePassage());
		assertEquals(9999, Config.getServer().getWebserverPort());

		final TicketRules.CurrencyResolution resolution = TicketRules.resolve(Config.getServer().getTicketCurrency(), id -> false);
		assertEquals(TicketRules.Problem.UNKNOWN_ITEM, resolution.problem);
		assertEquals(TicketRules.DEFAULT_CURRENCY_ID, resolution.id);
	}

	@Test
	void invalidCurrencyIsKeptForTheWarningAndStillFallsBack() throws Exception {
		write("{\"client\":{},\"server\":{\"ticketCurrency\":\"NOT AN ID\",\"freeTicketBarriers\":false}}");
		Config.init(folder.toFile());
		// init rewrites the file from memory. The bad id must survive that rewrite, not be replaced before anyone sees it.
		assertEquals("NOT AN ID", Config.getServer().getTicketCurrency());
		final TicketRules.CurrencyResolution resolution = TicketRules.resolve(Config.getServer().getTicketCurrency(), id -> true);
		assertEquals(TicketRules.Problem.INVALID_ID, resolution.problem);
		assertEquals(TicketRules.DEFAULT_CURRENCY_ID, resolution.id);
	}

	private void write(String json) throws Exception {
		Files.createDirectories(configFile().getParent());
		Files.writeString(configFile(), json, StandardCharsets.UTF_8);
	}

	private Path configFile() {
		return folder.resolve("config/mtr.json");
	}
}
