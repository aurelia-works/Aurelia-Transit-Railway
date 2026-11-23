package org.mtr.aurelia.client.voice;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.mtr.aurelia.client.AureliaClient;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads and unpacks the engine and a voice into {@code config/aurelia/voice/}, one at a time, off the game thread.
 * A file whose SHA-256 does not match is deleted and never unpacked. A folder counts as installed once its
 * {@code .ready} marker is written, so a download cut short is started again, not used half-finished.
 */
public final class VoiceInstaller {

	public enum Status {IDLE, DOWNLOADING, FAILED}

	private static final String READY = ".ready";
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "ATR voice download");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile Status status = Status.IDLE;
	private static volatile long done;
	private static volatile long total;

	private VoiceInstaller() {
	}

	public static Status status() {
		return status;
	}

	/** 0 to 100 across everything still to download. */
	public static int percent() {
		return total <= 0 ? 0 : (int) Math.min(100, done * 100 / total);
	}

	public static boolean installed(Path root, VoiceCatalog.Download download) {
		return download != null && Files.isRegularFile(root.resolve(download.folder()).resolve(READY));
	}

	/** Bytes still to download for the engine and this voice. */
	public static long bytesNeeded(Path root, VoiceCatalog.Download engine, VoiceCatalog.Voice voice) {
		return (installed(root, engine) ? 0 : engine.bytes()) + (installed(root, voice.download()) ? 0 : voice.download().bytes());
	}

	/** Starts downloading whatever of the engine and this voice is missing. Does nothing while a download runs. */
	public static synchronized void install(Path root, VoiceCatalog.Download engine, VoiceCatalog.Voice voice) {
		if (status == Status.DOWNLOADING || engine == null) {
			return;
		}
		status = Status.DOWNLOADING;
		done = 0;
		total = bytesNeeded(root, engine, voice);
		WORKER.execute(() -> {
			try {
				fetch(root, engine, true);
				fetch(root, voice.download(), false);
				status = Status.IDLE;
			} catch (Exception e) {
				AureliaClient.LOGGER.warn("[ATR voice] download failed", e);
				status = Status.FAILED;
			}
		});
	}

	private static void fetch(Path root, VoiceCatalog.Download download, boolean program) throws Exception {
		if (installed(root, download)) {
			return;
		}
		Files.createDirectories(root);
		final Path archive = root.resolve(download.folder() + ".tar.bz2.part");
		try {
			final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
			final HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(URI.create(download.url())).build(), HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				throw new IOException("HTTP " + response.statusCode() + " for " + download.url());
			}
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(archive)) {
				final byte[] buffer = new byte[1 << 16];
				int read;
				while ((read = in.read(buffer)) >= 0) {
					digest.update(buffer, 0, read);
					out.write(buffer, 0, read);
					done += read;
				}
			}
			final String sha256 = HexFormat.of().formatHex(digest.digest());
			if (!sha256.equalsIgnoreCase(download.sha256())) {
				throw new IOException("checksum mismatch for " + download.url() + ": " + sha256);
			}
			unpack(archive, root, download.folder());
			if (program) {
				markPrograms(root.resolve(download.folder()));
			}
			Files.writeString(root.resolve(download.folder()).resolve(READY), download.sha256());
		} finally {
			Files.deleteIfExists(archive);
		}
	}

	/** Unpacks a .tar.bz2 into {@code root}. Every entry must land inside {@code root/folder}. */
	static void unpack(Path archive, Path root, String folder) throws IOException {
		final Path target = root.resolve(folder).normalize();
		try (TarArchiveInputStream tar = new TarArchiveInputStream(new BZip2CompressorInputStream(new BufferedInputStream(Files.newInputStream(archive))))) {
			TarArchiveEntry entry;
			while ((entry = tar.getNextTarEntry()) != null) {
				final Path path = inside(root, target, entry.getName());
				if (entry.isDirectory()) {
					Files.createDirectories(path);
				} else if (entry.isSymbolicLink()) {
					if (!path.getParent().resolve(entry.getLinkName()).normalize().startsWith(target)) {
						throw new IOException("archive link outside its folder: " + entry.getName());
					}
					Files.createDirectories(path.getParent());
					Files.deleteIfExists(path);
					Files.createSymbolicLink(path, Path.of(entry.getLinkName()));
				} else if (entry.isFile()) {
					Files.createDirectories(path.getParent());
					Files.copy(tar, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	/** Rejects an archive path that would escape the voice folder ("../", absolute paths). */
	static Path inside(Path root, Path target, String name) throws IOException {
		final Path path = root.resolve(name).normalize();
		if (!path.startsWith(target)) {
			throw new IOException("archive entry outside its folder: " + name);
		}
		return path;
	}

	private static void markPrograms(Path folder) throws IOException {
		final Path bin = folder.resolve("bin");
		if (Files.isDirectory(bin)) {
			try (var files = Files.list(bin)) {
				for (final Path file : (Iterable<Path>) files::iterator) {
					file.toFile().setExecutable(true);
				}
			}
		}
	}
}
