package org.mtr.aurelia.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.core.serializer.JsonReader;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.mod.config.Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public final class AtuImportTest {

	@Test
	public void copiesAtuFilesOnlyWhereAtrHasNone(@TempDir Path config) throws IOException {
		final Path oldDir = Files.createDirectories(config.resolve(AtuImport.OLD_DIR));
		Files.writeString(oldDir.resolve("train_presets.json"), "old presets");
		Files.writeString(oldDir.resolve("announcer.properties"), "old announcer");
		final Path newDir = Files.createDirectories(config.resolve(AureliaClient.MOD_ID));
		Files.writeString(newDir.resolve("announcer.properties"), "atr announcer");

		assertEquals(1, AtuImport.copyFiles(config));
		assertEquals("old presets", Files.readString(newDir.resolve("train_presets.json")));
		assertEquals("atr announcer", Files.readString(newDir.resolve("announcer.properties")));
		assertTrue(Files.exists(oldDir.resolve("train_presets.json")), "ATU's files are left alone");
	}

	@Test
	public void noAtuFilesCopiesNothing(@TempDir Path config) throws IOException {
		assertEquals(0, AtuImport.copyFiles(config));
		assertFalse(Files.exists(config.resolve(AureliaClient.MOD_ID)));
	}

	@Test
	public void readsOnlyKnownPerfKeys(@TempDir Path config) throws IOException {
		final Path file = config.resolve(AtuImport.OLD_PERF_FILE);
		Files.writeString(file, "mergeTrainParts = false\ntrainBoxChunks=12\nsomethingElse=true\n");
		assertEquals(Map.of("mergeTrainParts", "false", "trainBoxChunks", "12"), AtuImport.readPerf(file));
		assertTrue(AtuImport.readPerf(config.resolve("missing.properties")).isEmpty());
	}

	@Test
	public void appliesSwitchesAndDistances() {
		final Client client = new Client(new JsonReader(new JsonObject()));
		final boolean merge = client.getMergeTrainParts();
		final boolean f3 = client.getPerfOnF3();
		final int applied = AtuImport.applyPerf(client, Map.of(
				"mergeTrainParts", String.valueOf(!merge),
				"debugOverlay", String.valueOf(!f3),
				"cheapShadows", String.valueOf(client.getCheapShadows()),
				"trainBoxChunks", "99",
				"railFarChunks", "-1",
				"railSimpleChunks", "abc"
		));
		assertEquals(4, applied);
		assertEquals(!merge, client.getMergeTrainParts());
		assertEquals(!f3, client.getPerfOnF3());
		assertEquals(Client.TRAIN_BOX_CHUNKS_MAX, client.getTrainBoxChunks());
	}
}
