package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.mod.servlet.WebMapOverrideServlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WebMapOverrideServletTest {

	@Test
	public void readsFilesInsideTheFolder(@TempDir Path root) throws IOException {
		Files.write(root.resolve("index.html"), "<html></html>".getBytes(StandardCharsets.UTF_8));
		Files.createDirectories(root.resolve("assets/i18n"));
		Files.write(root.resolve("assets/i18n/en.json"), "{}".getBytes(StandardCharsets.UTF_8));
		Assertions.assertEquals("<html></html>", WebMapOverrideServlet.read(root, "index.html"));
		Assertions.assertEquals("{}", WebMapOverrideServlet.read(root, "assets/i18n/en.json"));
	}

	@Test
	public void missingFilesAndEscapesGiveNull(@TempDir Path directory) throws IOException {
		final Path root = Files.createDirectories(directory.resolve("site"));
		Files.write(directory.resolve("secret.txt"), "secret".getBytes(StandardCharsets.UTF_8));
		Assertions.assertNull(WebMapOverrideServlet.read(root, "missing.js"));
		Assertions.assertNull(WebMapOverrideServlet.read(root, "../secret.txt"));
		Assertions.assertNull(WebMapOverrideServlet.read(root, "assets"));
	}
}
