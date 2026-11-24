package org.mtr.aurelia.client.voice;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Guards the download unpacker: an archive must never write outside its own voice folder. */
class VoiceInstallerTest {

	@Test
	void unpacksIntoItsFolderAndRefusesToEscapeIt(@TempDir Path root) throws IOException {
		final Path good = archive(root.resolve("good.tar.bz2"), "voice/tokens.txt");
		VoiceInstaller.unpack(good, root, "voice");
		assertEquals("x", Files.readString(root.resolve("voice/tokens.txt")));

		final Path evil = archive(root.resolve("evil.tar.bz2"), "voice/../../escaped.txt");
		assertThrows(IOException.class, () -> VoiceInstaller.unpack(evil, root, "voice"));
		assertFalse(Files.exists(root.getParent().resolve("escaped.txt")));

		final Path sideways = archive(root.resolve("sideways.tar.bz2"), "other/tokens.txt");
		assertThrows(IOException.class, () -> VoiceInstaller.unpack(sideways, root, "voice"));
	}

	private static Path archive(Path file, String entryName) throws IOException {
		final byte[] content = "x".getBytes(StandardCharsets.UTF_8);
		try (OutputStream out = Files.newOutputStream(file); TarArchiveOutputStream tar = new TarArchiveOutputStream(new BZip2CompressorOutputStream(out))) {
			final TarArchiveEntry entry = new TarArchiveEntry(entryName, true);
			entry.setSize(content.length);
			tar.putArchiveEntry(entry);
			tar.write(content);
			tar.closeArchiveEntry();
		}
		return file;
	}
}
