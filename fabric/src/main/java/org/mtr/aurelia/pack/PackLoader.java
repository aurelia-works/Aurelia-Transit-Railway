package org.mtr.aurelia.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mtr.aurelia.timetable.Timetable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and validates the files described in {@code docs/private-packs.md}. Pure Java: the game only supplies a list of
 * file names and their text, plus a way to ask whether a sound profile exists. A bad file or entry is skipped and
 * reported; nothing here throws out to the caller.
 */
public final class PackLoader {

	public static final int FORMAT = 1;
	public static final int MAX_PACKS = 64;
	public static final int MAX_ENTRIES = 512;
	public static final int MAX_FILE_CHARS = 1_048_576;
	private static final int MAX_DIAGNOSTICS_PER_FILE = 50;

	private static final Pattern LOCAL_ID = Pattern.compile("^[a-z0-9_.-]+$");
	private static final Pattern RESOURCE_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
	private static final Pattern COLOUR = Pattern.compile("^[0-9A-Fa-f]{6}$");

	public enum Kind {
		RESOURCE_PACK, DATA_PACK
	}

	/**
	 * One JSON file to read. {@code file} is what the panel shows ({@code namespace:path} in game); {@code readError} is
	 * set when the file could not even be opened.
	 */
	public record Source(String file, Kind kind, String text, String readError) {

		public Source(String file, Kind kind, String text) {
			this(file, kind, text, null);
		}
	}

	@FunctionalInterface
	public interface ProfileLookup {
		boolean exists(String profileId);
	}

	private PackLoader() {
	}

	public static PackLoadResult load(List<Source> sources, ProfileLookup profiles) {
		final List<Source> sorted = new ArrayList<>(sources);
		sorted.sort(Comparator.comparing(Source::file));
		final List<LoadedPack> packs = new ArrayList<>();
		final List<PackDiagnostic> diagnostics = new ArrayList<>();

		for (final Source source : sorted) {
			if (packs.size() >= MAX_PACKS) {
				diagnostics.add(PackDiagnostic.error("<packs>", 0, "more than " + MAX_PACKS + " pack files; the rest were not read"));
				break;
			}
			if (source.readError() != null) {
				diagnostics.add(PackDiagnostic.error(source.file(), 0, "could not be read: " + source.readError()));
				continue;
			}
			if (source.text().length() > MAX_FILE_CHARS) {
				diagnostics.add(PackDiagnostic.error(source.file(), 1, "file is larger than " + MAX_FILE_CHARS + " characters"));
				continue;
			}

			final PackJson.Parsed parsed;
			try {
				parsed = PackJson.parse(source.text());
			} catch (PackJson.SyntaxError e) {
				diagnostics.add(PackDiagnostic.error(source.file(), e.line(), "JSON syntax error at column " + e.column() + ": " + e.getMessage()));
				continue;
			}

			if (!parsed.root().isJsonObject()) {
				diagnostics.add(PackDiagnostic.error(source.file(), 1, "the top level must be a JSON object"));
				continue;
			}

			final JsonObject root = parsed.root().getAsJsonObject();
			final JsonElement formatElement = root.get("format");
			if (formatElement == null) {
				diagnostics.add(PackDiagnostic.error(source.file(), parsed.line(root), "missing \"format\": " + FORMAT));
				continue;
			}
			if (!formatElement.isJsonPrimitive() || !formatElement.getAsJsonPrimitive().isNumber()) {
				diagnostics.add(PackDiagnostic.error(source.file(), parsed.line(formatElement), "\"format\" must be the number " + FORMAT));
				continue;
			}
			final double format = formatElement.getAsDouble();
			if (format != FORMAT) {
				diagnostics.add(PackDiagnostic.error(source.file(), parsed.line(formatElement), "format " + formatElement + " is not supported; this ATR reads format " + FORMAT));
				continue;
			}

			final Ctx ctx = new Ctx(source.file(), parsed, diagnostics);
			String name = fileStem(source.file());
			final JsonElement nameElement = root.get("name");
			if (nameElement != null) {
				if (nameElement.isJsonPrimitive() && nameElement.getAsJsonPrimitive().isString() && !nameElement.getAsString().strip().isEmpty()) {
					name = nameElement.getAsString().strip();
					if (name.length() > 80) {
						ctx.error(nameElement, "pack name is longer than 80 characters; using the file name instead");
						name = fileStem(source.file());
					}
				} else {
					ctx.error(nameElement, "pack \"name\" must be a non-empty string; using the file name instead");
				}
			}

			final List<PackOperator> operators = readOperators(root, ctx);
			final Set<String> operatorIds = new HashSet<>();
			operators.forEach(operator -> operatorIds.add(operator.id()));
			final List<PackLivery> liveries = readLiveries(root, ctx, operatorIds);
			final List<PackSound> sounds = readSounds(root, ctx, profiles, source.kind());
			final List<PackStationPreset> stationPresets = readStationPresets(root, ctx);
			final List<PackTimetable> timetables = readTimetables(root, ctx);
			ctx.finish();

			packs.add(new LoadedPack(source.file(), name, source.kind() == Kind.RESOURCE_PACK, operators, liveries, sounds, stationPresets, timetables));
		}

		warnDuplicates(packs, diagnostics);
		if (diagnostics.size() > 200) {
			final List<PackDiagnostic> capped = new ArrayList<>(diagnostics.subList(0, 200));
			capped.add(PackDiagnostic.error("<packs>", 0, "more than 200 diagnostics; the rest were not shown"));
			return new PackLoadResult(packs, capped);
		}
		return new PackLoadResult(packs, diagnostics);
	}

	private static void warnDuplicates(List<LoadedPack> packs, List<PackDiagnostic> diagnostics) {
		final Map<String, LoadedPack> operators = new HashMap<>();
		final Map<String, LoadedPack> liveries = new HashMap<>();
		final Map<String, LoadedPack> presets = new HashMap<>();
		final Map<String, LoadedPack> timetables = new HashMap<>();
		final Map<String, LoadedPack> sounds = new HashMap<>();
		for (final LoadedPack pack : packs) {
			for (final PackOperator operator : pack.operators()) {
				duplicate(operators, operator.id(), pack, diagnostics, "operator");
			}
			for (final PackLivery livery : pack.liveries()) {
				duplicate(liveries, livery.id(), pack, diagnostics, "livery");
			}
			for (final PackStationPreset preset : pack.stationPresets()) {
				duplicate(presets, preset.id(), pack, diagnostics, "station preset");
			}
			for (final PackTimetable timetable : pack.timetables()) {
				duplicate(timetables, timetable.id(), pack, diagnostics, "timetable");
			}
			for (final PackSound sound : pack.sounds()) {
				duplicate(sounds, sound.vehicle(), pack, diagnostics, "sound assignment for");
			}
		}
	}

	private static void duplicate(Map<String, LoadedPack> seen, String id, LoadedPack pack, List<PackDiagnostic> diagnostics, String kind) {
		final LoadedPack previous = seen.putIfAbsent(id, pack);
		if (previous != null) {
			diagnostics.add(PackDiagnostic.warning(pack.file(), 0, kind + " \"" + id + "\" is also in " + previous.file() + "; both are kept"));
		}
	}

	private static List<PackOperator> readOperators(JsonObject root, Ctx ctx) {
		final List<PackOperator> result = new ArrayList<>();
		final JsonArray array = section(root, "operators", ctx);
		if (array == null) {
			return result;
		}
		final Set<String> ids = new HashSet<>();
		for (int i = 0; i < Math.min(array.size(), MAX_ENTRIES); i++) {
			final JsonElement element = array.get(i);
			try {
				final JsonObject object = requireObject(element, ctx, "operators[" + i + "]");
				final String id = requiredLocalId(object, "id", ctx, "operator");
				if (!ids.add(id)) {
					ctx.error(object, "operator \"" + id + "\": duplicate id in this pack");
					continue;
				}
				final String label = "operator \"" + id + "\"";
				final String name = requiredString(object, "name", ctx, label, 80);
				final String shortCode = requiredString(object, "short_code", ctx, label, 8);
				final JsonElement coloursElement = object.get("colours");
				if (coloursElement == null) {
					ctx.error(object, label + ": missing \"colours\"");
					continue;
				}
				final JsonObject colours = requireObject(coloursElement, ctx, label + " \"colours\"");
				final int primary = requiredColour(colours, "primary", ctx, label);
				final int secondary = optionalColour(colours, "secondary", ctx, label, primary);
				final int text = optionalColour(colours, "text", ctx, label, 0xFFFFFF);
				result.add(new PackOperator(id, name, shortCode, primary, secondary, text));
			} catch (EntryError ignored) {
			}
		}
		tooMany(array, "operators", ctx);
		return result;
	}

	private static List<PackLivery> readLiveries(JsonObject root, Ctx ctx, Set<String> operatorIds) {
		final List<PackLivery> result = new ArrayList<>();
		final JsonArray array = section(root, "liveries", ctx);
		if (array == null) {
			return result;
		}
		final Set<String> ids = new HashSet<>();
		for (int i = 0; i < Math.min(array.size(), MAX_ENTRIES); i++) {
			final JsonElement element = array.get(i);
			try {
				final JsonObject object = requireObject(element, ctx, "liveries[" + i + "]");
				final String id = requiredLocalId(object, "id", ctx, "livery");
				if (!ids.add(id)) {
					ctx.error(object, "livery \"" + id + "\": duplicate id in this pack");
					continue;
				}
				final String label = "livery \"" + id + "\"";
				final JsonElement operatorElement = object.get("operator");
				final String operator = requiredLocalId(object, "operator", ctx, label);
				if (!operatorIds.contains(operator)) {
					ctx.error(operatorElement, label + ": operator \"" + operator + "\" is not defined in this pack");
					continue;
				}
				final String name = optionalString(object, "name", ctx, label, 80, id);
				final List<String> vehicles = resourceIdList(object, "vehicles", ctx, label);
				final Map<String, String> textures = textureMap(object, "textures", ctx, label);
				if (vehicles.isEmpty() && textures.isEmpty()) {
					ctx.error(object, label + ": give at least one vehicle id or one texture override");
					continue;
				}
				result.add(new PackLivery(id, operator, name, vehicles, textures));
			} catch (EntryError ignored) {
			}
		}
		tooMany(array, "liveries", ctx);
		return result;
	}

	private static List<PackSound> readSounds(JsonObject root, Ctx ctx, ProfileLookup profiles, Kind kind) {
		final List<PackSound> result = new ArrayList<>();
		final JsonElement element = root.get("sounds");
		if (element == null) {
			return result;
		}
		if (!element.isJsonObject()) {
			ctx.error(element, "\"sounds\" must be an object mapping vehicle ids to sound profile ids");
			return result;
		}
		int count = 0;
		for (final Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			if (++count > MAX_ENTRIES) {
				ctx.error(element, "\"sounds\": at most " + MAX_ENTRIES + " assignments are read");
				break;
			}
			final String vehicle = entry.getKey();
			final JsonElement value = entry.getValue();
			if (!isResourceId(vehicle)) {
				ctx.error(value, "sounds: \"" + vehicle + "\" is not a vehicle resource id (namespace:path)");
				continue;
			}
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
				ctx.error(value, "sounds: the profile for \"" + vehicle + "\" must be a string profile id");
				continue;
			}
			final String profile = value.getAsString().strip();
			if (!isResourceId(profile)) {
				ctx.error(value, "sounds: \"" + profile + "\" is not a sound profile id (namespace:path)");
				continue;
			}
			result.add(new PackSound(vehicle, profile));
			// Sound profiles are client assets; a data pack cannot see them, so only resource packs are checked.
			if (kind == Kind.RESOURCE_PACK && !profiles.exists(profile)) {
				ctx.warning(value, "sound profile \"" + profile + "\" has no assets/" + profileNamespace(profile) + "/aurelia_sound_profiles/" + profilePath(profile) + ".json; the assignment is kept");
			}
		}
		return result;
	}

	private static List<PackStationPreset> readStationPresets(JsonObject root, Ctx ctx) {
		final List<PackStationPreset> result = new ArrayList<>();
		final JsonArray array = section(root, "station_presets", ctx);
		if (array == null) {
			return result;
		}
		final Set<String> ids = new HashSet<>();
		for (int i = 0; i < Math.min(array.size(), MAX_ENTRIES); i++) {
			final JsonElement element = array.get(i);
			try {
				final JsonObject object = requireObject(element, ctx, "station_presets[" + i + "]");
				final String id = requiredLocalId(object, "id", ctx, "station preset");
				if (!ids.add(id)) {
					ctx.error(object, "station preset \"" + id + "\": duplicate id in this pack");
					continue;
				}
				final String label = "station preset \"" + id + "\"";
				final String name = requiredString(object, "name", ctx, label, 80);
				final String description = requiredString(object, "description", ctx, label, 512);
				final String style = optionalString(object, "style", ctx, label, 32, null);
				result.add(new PackStationPreset(id, name, description, style));
			} catch (EntryError ignored) {
			}
		}
		tooMany(array, "station_presets", ctx);
		return result;
	}

	private static List<PackTimetable> readTimetables(JsonObject root, Ctx ctx) {
		final List<PackTimetable> result = new ArrayList<>();
		final JsonArray array = section(root, "timetables", ctx);
		if (array == null) {
			return result;
		}
		final Set<String> ids = new HashSet<>();
		for (int i = 0; i < Math.min(array.size(), MAX_ENTRIES); i++) {
			final JsonElement element = array.get(i);
			try {
				final JsonObject object = requireObject(element, ctx, "timetables[" + i + "]");
				final String id = requiredLocalId(object, "id", ctx, "timetable");
				if (!ids.add(id)) {
					ctx.error(object, "timetable \"" + id + "\": duplicate id in this pack");
					continue;
				}
				final String label = "timetable \"" + id + "\"";
				final String name = requiredString(object, "name", ctx, label, 80);
				final String depot = requiredString(object, "depot", ctx, label, 80);
				final JsonElement weekdayElement = object.get("weekday");
				final JsonElement weekendElement = object.get("weekend");
				if (weekdayElement == null && weekendElement == null) {
					ctx.error(object, label + ": needs \"weekday\" or \"weekend\" (or both)");
					continue;
				}
				final PackTimetable.Plan weekday = weekdayElement == null ? null : readPlan(weekdayElement, ctx, label + " weekday");
				final PackTimetable.Plan weekend = weekendElement == null ? null : readPlan(weekendElement, ctx, label + " weekend");
				result.add(new PackTimetable(id, name, depot, weekday, weekend));
			} catch (EntryError ignored) {
			}
		}
		tooMany(array, "timetables", ctx);
		return result;
	}

	private static PackTimetable.Plan readPlan(JsonElement element, Ctx ctx, String label) {
		final JsonObject object = requireObject(element, ctx, label);
		boolean realTime = true;
		final JsonElement realTimeElement = object.get("real_time");
		if (realTimeElement != null) {
			if (!realTimeElement.isJsonPrimitive() || !realTimeElement.getAsJsonPrimitive().isBoolean()) {
				ctx.error(realTimeElement, label + ": \"real_time\" must be true or false");
				throw new EntryError();
			}
			realTime = realTimeElement.getAsBoolean();
		}

		final JsonElement departuresElement = object.get("departures");
		final JsonElement frequenciesElement = object.get("frequencies");
		if (departuresElement != null && frequenciesElement != null) {
			ctx.error(object, label + ": give \"departures\" or \"frequencies\", not both");
			throw new EntryError();
		}
		if (departuresElement == null && frequenciesElement == null) {
			ctx.error(object, label + ": needs \"departures\" or \"frequencies\"");
			throw new EntryError();
		}

		if (departuresElement != null) {
			if (!departuresElement.isJsonArray()) {
				ctx.error(departuresElement, label + ": \"departures\" must be an array of HH:MM times");
				throw new EntryError();
			}
			final JsonArray array = departuresElement.getAsJsonArray();
			if (array.isEmpty()) {
				ctx.error(departuresElement, label + ": \"departures\" is empty");
				throw new EntryError();
			}
			if (array.size() > Timetable.MAX_DEPARTURES) {
				ctx.error(departuresElement, label + ": at most " + Timetable.MAX_DEPARTURES + " departures are read");
				throw new EntryError();
			}
			final List<Long> departures = new ArrayList<>();
			for (final JsonElement departure : array) {
				if (!departure.isJsonPrimitive() || !departure.getAsJsonPrimitive().isString()) {
					ctx.error(departure, label + ": each departure must be an \"HH:MM\" string");
					throw new EntryError();
				}
				final OptionalLong time = Timetable.parseTime(departure.getAsString());
				if (time.isEmpty()) {
					ctx.error(departure, label + ": \"" + departure.getAsString() + "\" is not a time (HH:MM)");
					throw new EntryError();
				}
				departures.add(time.getAsLong());
			}
			return new PackTimetable.Plan(realTime, Timetable.normalise(departures), List.of());
		}

		if (!frequenciesElement.isJsonArray()) {
			ctx.error(frequenciesElement, label + ": \"frequencies\" must be an array of 24 numbers");
			throw new EntryError();
		}
		final JsonArray array = frequenciesElement.getAsJsonArray();
		if (array.size() != 24) {
			ctx.error(frequenciesElement, label + ": \"frequencies\" must have 24 numbers (one per hour), found " + array.size());
			throw new EntryError();
		}
		final List<Integer> frequencies = new ArrayList<>();
		for (final JsonElement frequency : array) {
			if (!frequency.isJsonPrimitive() || !frequency.getAsJsonPrimitive().isNumber()) {
				ctx.error(frequency, label + ": each frequency must be a number");
				throw new EntryError();
			}
			final double number = frequency.getAsDouble();
			final int value = (int) number;
			if (number != value || value < 0 || value > Timetable.MAX_FREQUENCY) {
				ctx.error(frequency, label + ": a frequency must be a whole number from 0 to " + Timetable.MAX_FREQUENCY);
				throw new EntryError();
			}
			frequencies.add(value);
		}
		return new PackTimetable.Plan(realTime, List.of(), frequencies);
	}

	private static JsonArray section(JsonObject root, String key, Ctx ctx) {
		final JsonElement element = root.get(key);
		if (element == null) {
			return null;
		}
		if (!element.isJsonArray()) {
			ctx.error(element, "\"" + key + "\" must be an array");
			return null;
		}
		return element.getAsJsonArray();
	}

	private static void tooMany(JsonArray array, String key, Ctx ctx) {
		if (array.size() > MAX_ENTRIES) {
			ctx.error(array, "\"" + key + "\": at most " + MAX_ENTRIES + " entries are read");
		}
	}

	private static JsonObject requireObject(JsonElement element, Ctx ctx, String label) {
		if (element == null) {
			ctx.error(0, label + ": missing");
			throw new EntryError();
		}
		if (!element.isJsonObject()) {
			ctx.error(element, label + ": must be an object");
			throw new EntryError();
		}
		return element.getAsJsonObject();
	}

	private static String requiredString(JsonObject object, String key, Ctx ctx, String label, int maxLength) {
		final JsonElement element = object.get(key);
		if (element == null) {
			ctx.error(object, label + ": missing \"" + key + "\"");
			throw new EntryError();
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			ctx.error(element, label + ": \"" + key + "\" must be a string");
			throw new EntryError();
		}
		final String value = element.getAsString().strip();
		if (value.isEmpty()) {
			ctx.error(element, label + ": \"" + key + "\" is empty");
			throw new EntryError();
		}
		if (value.length() > maxLength) {
			ctx.error(element, label + ": \"" + key + "\" is longer than " + maxLength + " characters");
			throw new EntryError();
		}
		return value;
	}

	private static String optionalString(JsonObject object, String key, Ctx ctx, String label, int maxLength, String fallback) {
		final JsonElement element = object.get(key);
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			ctx.error(element, label + ": \"" + key + "\" must be a string");
			throw new EntryError();
		}
		final String value = element.getAsString().strip();
		if (value.isEmpty()) {
			ctx.error(element, label + ": \"" + key + "\" is empty");
			throw new EntryError();
		}
		if (value.length() > maxLength) {
			ctx.error(element, label + ": \"" + key + "\" is longer than " + maxLength + " characters");
			throw new EntryError();
		}
		return value;
	}

	private static String requiredLocalId(JsonObject object, String key, Ctx ctx, String label) {
		final JsonElement element = object.get(key);
		final String value = requiredString(object, key, ctx, label, 64);
		if (!LOCAL_ID.matcher(value).matches()) {
			ctx.error(element, label + ": \"" + value + "\" is not a lower-case id (a-z, 0-9, _ . -)");
			throw new EntryError();
		}
		return value;
	}

	private static int requiredColour(JsonObject object, String key, Ctx ctx, String label) {
		final JsonElement element = object.get(key);
		if (element == null) {
			ctx.error(object, label + ": missing \"colours." + key + "\"");
			throw new EntryError();
		}
		return parseColour(element, ctx, label + ": \"colours." + key + "\"");
	}

	private static int optionalColour(JsonObject object, String key, Ctx ctx, String label, int fallback) {
		final JsonElement element = object.get(key);
		return element == null ? fallback : parseColour(element, ctx, label + ": \"colours." + key + "\"");
	}

	private static int parseColour(JsonElement element, Ctx ctx, String label) {
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			ctx.error(element, label + " must be a \"#RRGGBB\" string");
			throw new EntryError();
		}
		final String value = element.getAsString().strip();
		final String hex = value.startsWith("#") ? value.substring(1) : value;
		if (!COLOUR.matcher(hex).matches()) {
			ctx.error(element, label + " \"" + value + "\" is not a #RRGGBB colour");
			throw new EntryError();
		}
		return Integer.parseInt(hex, 16);
	}

	private static List<String> resourceIdList(JsonObject object, String key, Ctx ctx, String label) {
		final JsonElement element = object.get(key);
		if (element == null) {
			return List.of();
		}
		if (!element.isJsonArray()) {
			ctx.error(element, label + ": \"" + key + "\" must be an array of resource ids");
			throw new EntryError();
		}
		final JsonArray array = element.getAsJsonArray();
		if (array.size() > 128) {
			ctx.error(element, label + ": at most 128 \"" + key + "\" entries are read");
			throw new EntryError();
		}
		final List<String> result = new ArrayList<>();
		for (final JsonElement item : array) {
			if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString() || !isResourceId(item.getAsString())) {
				ctx.error(item, label + ": \"" + item + "\" is not a resource id (namespace:path)");
				throw new EntryError();
			}
			result.add(item.getAsString().strip());
		}
		return result;
	}

	private static Map<String, String> textureMap(JsonObject object, String key, Ctx ctx, String label) {
		final JsonElement element = object.get(key);
		if (element == null) {
			return Map.of();
		}
		if (!element.isJsonObject()) {
			ctx.error(element, label + ": \"" + key + "\" must be an object mapping vehicle ids to texture ids");
			throw new EntryError();
		}
		final JsonObject map = element.getAsJsonObject();
		if (map.size() > 128) {
			ctx.error(element, label + ": at most 128 \"" + key + "\" entries are read");
			throw new EntryError();
		}
		final Map<String, String> result = new LinkedHashMap<>();
		for (final Map.Entry<String, JsonElement> entry : map.entrySet()) {
			final JsonElement value = entry.getValue();
			if (!isResourceId(entry.getKey()) || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !isResourceId(value.getAsString())) {
				ctx.error(value, label + ": \"" + entry.getKey() + "\" -> \"" + value + "\" is not a pair of resource ids");
				throw new EntryError();
			}
			result.put(entry.getKey(), value.getAsString().strip());
		}
		return result;
	}

	private static boolean isResourceId(String value) {
		return value != null && RESOURCE_ID.matcher(value.strip()).matches();
	}

	private static String profileNamespace(String profile) {
		return profile.substring(0, profile.indexOf(':'));
	}

	private static String profilePath(String profile) {
		return profile.substring(profile.indexOf(':') + 1);
	}

	private static String fileStem(String file) {
		final String path = file.substring(Math.max(file.lastIndexOf('/'), file.lastIndexOf(':')) + 1);
		return path.toLowerCase(Locale.ROOT).endsWith(".json") ? path.substring(0, path.length() - 5) : path;
	}

	private static final class EntryError extends RuntimeException {

		private EntryError() {
			super(null, null, false, false);
		}
	}

	private static final class Ctx {

		private final String file;
		private final PackJson.Parsed json;
		private final List<PackDiagnostic> diagnostics;
		private int added;
		private boolean capped;

		private Ctx(String file, PackJson.Parsed json, List<PackDiagnostic> diagnostics) {
			this.file = file;
			this.json = json;
			this.diagnostics = diagnostics;
		}

		private void error(JsonElement element, String message) {
			error(json.line(element), message);
		}

		private void error(int line, String message) {
			if (!capped) {
				diagnostics.add(PackDiagnostic.error(file, line, message));
				added++;
				capped = added >= MAX_DIAGNOSTICS_PER_FILE;
			}
		}

		private void warning(JsonElement element, String message) {
			diagnostics.add(PackDiagnostic.warning(file, json.line(element), message));
		}

		private void finish() {
			if (capped) {
				diagnostics.add(PackDiagnostic.error(file, 0, "more problems in this file were not shown"));
			}
		}
	}
}
