package com.aureliatransit.architecture.bridge.world;

import com.aureliatransit.architecture.bridge.BridgeTemplate;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Bridge templates on disk, one JSON file per template under {@code config/aurelia/bridges}. The files are world
 * independent, so an operator can copy a bridge between worlds. Names are lower-case so a template resolves the same way
 * on case-insensitive and case-sensitive file systems.
 */
public final class BridgeTemplateStore {

	public static final String FOLDER = "bridges";
	private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,47}");
	private static final String EXTENSION = ".json";

	private final Path root;

	public BridgeTemplateStore(Path root) {
		this.root = root;
	}

	/** The server's template folder; on a dedicated server this is the server's own config directory. */
	public static BridgeTemplateStore config() {
		return new BridgeTemplateStore(FabricLoader.getInstance().getConfigDir().resolve("aurelia").resolve(FOLDER));
	}

	public Path root() {
		return root;
	}

	public List<String> list() throws IOException {
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		final List<String> names = new ArrayList<>();
		try (Stream<Path> files = Files.list(root)) {
			files.filter(Files::isRegularFile)
					.map(file -> file.getFileName().toString())
					.filter(file -> file.endsWith(EXTENSION))
					.map(file -> file.substring(0, file.length() - EXTENSION.length()))
					.forEach(names::add);
		}
		names.sort(Comparator.naturalOrder());
		return List.copyOf(names);
	}

	public boolean exists(String name) {
		return Files.isRegularFile(file(name));
	}

	public BridgeTemplate load(String name) throws IOException {
		final Path file = file(name);
		if (!Files.isRegularFile(file)) {
			throw new IOException("No bridge template named '" + name + "'.");
		}
		try {
			return BridgeTemplate.fromJson(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException e) {
			throw new IOException("Bridge template '" + name + "' is not readable: " + e.getMessage(), e);
		}
	}

	public void save(String name, BridgeTemplate template) throws IOException {
		Files.createDirectories(root);
		final Path file = file(name);
		final Path temporary = Files.createTempFile(root, "bridge-" + name + "-", ".tmp");
		try {
			Files.writeString(temporary, template.toJson().toString(), StandardCharsets.UTF_8,
					StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
			try {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public boolean delete(String name) throws IOException {
		return Files.deleteIfExists(file(name));
	}

	private Path file(String name) {
		return root.resolve(normalise(name) + EXTENSION);
	}

	/**
	 * A template name as it is stored: lower-case letters, digits, {@code _} and {@code -}, up to 48 characters.
	 */
	public static String normalise(String name) {
		final String normalised = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
		if (!NAME.matcher(normalised).matches()) {
			throw new IllegalArgumentException("A template name uses a-z, 0-9, _ and - and is at most 48 characters.");
		}
		return normalised;
	}
}
