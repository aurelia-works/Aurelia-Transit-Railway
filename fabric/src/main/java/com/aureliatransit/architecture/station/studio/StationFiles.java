package com.aureliatransit.architecture.station.studio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Station files: one station per file, shared by the browser Station Studio and the in-game editor, so a station drawn in
 * one opens unchanged in the other. A file holds the Studio form (its field names and values, as the page sends them to
 * {@code /api/plan}), so either side plans it with {@link StudioApi#description(Map)}:
 * <pre>{"format":1,"name":"Leidsche Rijn","form":{"text":"...","tracks":"2","pinned":"tracks"}}</pre>
 * The file name is the station name as a slug ({@code leidsche-rijn.json}). Fields this version does not know are kept as
 * they are, so a newer Studio's file survives a save from an older editor. No Minecraft classes: the Studio server and the
 * game client both use it.
 */
public final class StationFiles {

	public static final int FORMAT = 1;
	/** Field names: letters and digits, as the page's fields are named. */
	private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,23}");
	/** File ids: what {@link StudioApi#slug} makes, plus a numbered copy ("station-2"). */
	private static final Pattern FILE = Pattern.compile("[a-z0-9][a-z0-9-]{0,47}");
	public static final int MAX_FIELDS = 64;
	public static final int MAX_VALUE = 4000;
	public static final int MAX_FILES = 500;
	private static final int MAX_BYTES = 256 * 1024;

	/** One file in the list: its id (name without ".json"), the station name in it, and when it last changed. */
	public record Entry(String file, String name, long modified) {
	}

	private final Path dir;

	public StationFiles(Path dir) {
		this.dir = dir;
	}

	public Path dir() {
		return dir;
	}

	/** Whether {@code file} is a valid file id (no paths, no dots). */
	public static boolean validFile(String file) {
		return file != null && FILE.matcher(file).matches();
	}

	/** The id a station called {@code name} is saved under. */
	public static String fileFor(String name) {
		return StudioApi.slug(name == null ? "" : name);
	}

	/** Every station file, newest first; unreadable files are left out. */
	public List<Entry> list() throws IOException {
		final List<Entry> entries = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return entries;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
			for (final Path path : stream) {
				final String name = path.getFileName().toString();
				final String file = name.substring(0, name.length() - ".json".length());
				if (!validFile(file) || entries.size() >= MAX_FILES) {
					continue;
				}
				try {
					final Map<String, String> form = read(file);
					entries.add(new Entry(file, form.getOrDefault("name", ""), Files.getLastModifiedTime(path).toMillis()));
				} catch (IOException | IllegalArgumentException ignored) {
					// a broken or foreign file is not a station
				}
			}
		}
		entries.sort(Comparator.comparingLong(Entry::modified).reversed().thenComparing(Entry::file));
		return entries;
	}

	/** The form saved in {@code file}. */
	public Map<String, String> read(String file) throws IOException {
		if (!validFile(file)) {
			throw new IllegalArgumentException("not a station file: " + file);
		}
		final Path path = dir.resolve(file + ".json");
		if (Files.size(path) > MAX_BYTES) {
			throw new IllegalArgumentException("station file too large: " + file);
		}
		return decode(Files.readString(path, StandardCharsets.UTF_8));
	}

	/**
	 * Saves {@code form} under its station name and returns the file id. A file of that name is replaced; the write goes to a
	 * temporary file first, so a crash never leaves half a station.
	 */
	public String write(Map<String, String> form) throws IOException {
		final String file = fileFor(form.get("name"));
		Files.createDirectories(dir);
		final Path target = dir.resolve(file + ".json");
		final Path temp = dir.resolve(file + ".json.tmp");
		Files.writeString(temp, encode(form), StandardCharsets.UTF_8);
		try {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
		return file;
	}

	/** {@code form} as a station file; fields with bad names or too long values are left out. */
	public static String encode(Map<String, String> form) {
		final Map<String, String> clean = clean(form);
		final StringBuilder out = new StringBuilder("{\n\t\"format\": ").append(FORMAT).append(",\n\t\"name\": ")
				.append(Json.quote(clean.getOrDefault("name", ""))).append(",\n\t\"form\": {");
		boolean first = true;
		for (final Map.Entry<String, String> entry : clean.entrySet()) {
			out.append(first ? "\n\t\t" : ",\n\t\t").append(Json.quote(entry.getKey())).append(": ").append(Json.quote(entry.getValue()));
			first = false;
		}
		return out.append(first ? "}\n}\n" : "\n\t}\n}\n").toString();
	}

	/** The form in a station file. A file from a newer format is read as far as this version understands it. */
	public static Map<String, String> decode(String text) {
		final Map<String, Object> root = Json.object(Json.parse(text));
		if (root == null || !(root.get("form") instanceof Map<?, ?>)) {
			throw new IllegalArgumentException("not a station file");
		}
		final Map<String, String> form = new TreeMap<>();
		for (final Map.Entry<String, Object> entry : Json.object(root.get("form")).entrySet()) {
			final Object value = entry.getValue();
			if (value instanceof String string) {
				form.put(entry.getKey(), string);
			} else if (value instanceof Number number) {
				form.put(entry.getKey(), number.doubleValue() == Math.rint(number.doubleValue()) ? Long.toString(number.longValue()) : number.toString());
			} else if (value instanceof Boolean bool) {
				form.put(entry.getKey(), bool ? "1" : "0");
			}
		}
		final String name = Json.string(root.get("name"), null);
		if (name != null && !form.containsKey("name")) {
			form.put("name", name);
		}
		return clean(form);
	}

	/** {@code form} sorted, with only well-named fields of sane length, at most {@link #MAX_FIELDS}. */
	public static Map<String, String> clean(Map<String, String> form) {
		final Map<String, String> clean = new TreeMap<>();
		for (final Map.Entry<String, String> entry : form.entrySet()) {
			if (clean.size() < MAX_FIELDS && entry.getKey() != null && KEY.matcher(entry.getKey()).matches() && entry.getValue() != null
					&& entry.getValue().length() <= MAX_VALUE) {
				clean.put(entry.getKey(), entry.getValue());
			}
		}
		return clean;
	}

	/** The list as JSON for the Studio page: {@code {"stations":[{"file":..,"name":..,"modified":..}]}}. */
	public static String listJson(List<Entry> entries) {
		final StringBuilder out = new StringBuilder("{\"stations\":[");
		for (int i = 0; i < entries.size(); i++) {
			final Entry entry = entries.get(i);
			out.append(i == 0 ? "" : ",").append("{\"file\":").append(Json.quote(entry.file())).append(",\"name\":").append(Json.quote(entry.name()))
					.append(",\"modified\":").append(entry.modified()).append('}');
		}
		return out.append("]}").toString();
	}

	/** One file's form as JSON for the Studio page: {@code {"file":..,"form":{..}}}. */
	public static String formJson(String file, Map<String, String> form) {
		final StringBuilder out = new StringBuilder("{\"file\":").append(Json.quote(file)).append(",\"form\":{");
		boolean first = true;
		for (final Map.Entry<String, String> entry : form.entrySet()) {
			out.append(first ? "" : ",").append(Json.quote(entry.getKey())).append(':').append(Json.quote(entry.getValue()));
			first = false;
		}
		return out.append("}}").toString();
	}
}
