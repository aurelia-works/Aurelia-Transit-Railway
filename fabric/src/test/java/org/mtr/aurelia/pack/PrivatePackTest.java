package org.mtr.aurelia.pack;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class PrivatePackTest {

	@Test
	public void examplePackLoadsWithExpectedCounts() throws IOException {
		final Path file = exampleFile();
		final PackLoadResult result = PackLoader.load(
				List.of(new PackLoader.Source("example_rail:aurelia_packs/example_rail.json", PackLoader.Kind.RESOURCE_PACK, Files.readString(file))),
				id -> id.equals("mtr:uk_emu") || id.equals("mtr:metro")
		);
		Assertions.assertEquals(0, result.errorCount(), result.diagnostics().toString());
		Assertions.assertEquals(0, result.warningCount(), result.diagnostics().toString());
		Assertions.assertEquals(1, result.packs().size());

		final LoadedPack pack = result.packs().get(0);
		Assertions.assertEquals("Example Rail private pack", pack.name());
		Assertions.assertEquals(1, pack.operators().size());
		Assertions.assertEquals(2, pack.liveries().size());
		Assertions.assertEquals(2, pack.sounds().size());
		Assertions.assertEquals(1, pack.stationPresets().size());
		Assertions.assertEquals(1, pack.timetables().size());

		final PackOperator operator = pack.operators().get(0);
		Assertions.assertEquals("Example Rail", operator.name());
		Assertions.assertEquals("EXR", operator.shortCode());
		Assertions.assertEquals(0x1F4E79, operator.primaryColour());
		Assertions.assertEquals(0xF2C94C, operator.secondaryColour());
		Assertions.assertEquals(0xFFFFFF, operator.textColour());

		Assertions.assertEquals(List.of("example:emu_cab", "example:emu_trailer"), pack.liveries().get(0).vehicles());
		Assertions.assertEquals("example_rail:textures/vehicle/example_metro.png", pack.liveries().get(1).textures().get("example:metro_car"));

		final PackStationPreset preset = pack.stationPresets().get(0);
		Assertions.assertEquals("Example Central", preset.name());
		Assertions.assertEquals("modern", preset.style());

		final PackTimetable timetable = pack.timetables().get(0);
		Assertions.assertEquals("Example Depot", timetable.depot());
		Assertions.assertTrue(timetable.weekday().realTime());
		Assertions.assertEquals(4, timetable.weekday().departures().size());
		Assertions.assertTrue(timetable.weekend().usesFrequencies());
		Assertions.assertEquals(24, timetable.weekend().frequencies().size());
	}

	@Test
	public void brokenJsonGivesTheFileAndLine() {
		final String json = "{\n"
				+ "  \"format\": 1,\n"
				+ "  \"name\": \"Broken\",\n"
				+ "  \"operators\": [\n"
				+ "    {\"id\": \"x\", \"name\": \"X\"}\n"
				+ "    {\"id\": \"y\", \"name\": \"Y\"}\n"
				+ "  ]\n"
				+ "}\n";
		final PackLoadResult result = PackLoader.load(
				List.of(new PackLoader.Source("example:broken.json", PackLoader.Kind.RESOURCE_PACK, json)),
				id -> true
		);
		Assertions.assertTrue(result.packs().isEmpty(), "a file that is not JSON is skipped");
		Assertions.assertEquals(1, result.errorCount());
		final PackDiagnostic error = result.diagnostics().get(0);
		Assertions.assertEquals("example:broken.json", error.file());
		Assertions.assertEquals(6, error.line());
		Assertions.assertTrue(error.message().contains("Expected"), error.message());
	}

	@Test
	public void badEntryIsSkippedWithoutLosingTheFile() {
		final String json = "{\n"
				+ "  \"format\": 1,\n"
				+ "  \"operators\": [\n"
				+ "    {\"id\": \"good\", \"name\": \"Good\", \"short_code\": \"GD\", \"colours\": {\"primary\": \"#112233\"}},\n"
				+ "    {\"id\": \"bad\", \"name\": \"Bad\", \"short_code\": \"BD\", \"colours\": {\"primary\": \"blue\"}}\n"
				+ "  ]\n"
				+ "}\n";
		final PackLoadResult result = PackLoader.load(
				List.of(new PackLoader.Source("example:entry.json", PackLoader.Kind.RESOURCE_PACK, json)),
				id -> true
		);
		Assertions.assertEquals(1, result.errorCount(), result.diagnostics().toString());
		Assertions.assertEquals(1, result.packs().size());
		Assertions.assertEquals(1, result.packs().get(0).operators().size(), "the good operator still loads");
		final PackDiagnostic error = result.diagnostics().get(0);
		Assertions.assertEquals("example:entry.json", error.file());
		Assertions.assertEquals(5, error.line());
		Assertions.assertTrue(error.message().contains("blue"), error.message());
	}

	@Test
	public void unknownSoundProfileIsAWarningNotACrash() {
		final String json = "{\n"
				+ "  \"format\": 1,\n"
				+ "  \"name\": \"Unknown sound\",\n"
				+ "  \"sounds\": {\n"
				+ "    \"example:emu\": \"missing:profile\"\n"
				+ "  }\n"
				+ "}\n";
		final PackLoadResult result = PackLoader.load(
				List.of(new PackLoader.Source("example:sound.json", PackLoader.Kind.RESOURCE_PACK, json)),
				id -> false
		);
		Assertions.assertEquals(0, result.errorCount());
		Assertions.assertEquals(1, result.warningCount());
		Assertions.assertEquals(1, result.packs().size());
		Assertions.assertEquals(1, result.packs().get(0).sounds().size(), "the assignment is kept");
		final PackDiagnostic warning = result.diagnostics().get(0);
		Assertions.assertEquals(PackDiagnostic.Severity.WARNING, warning.severity());
		Assertions.assertEquals("example:sound.json", warning.file());
		Assertions.assertEquals(5, warning.line());
		Assertions.assertTrue(warning.message().contains("missing:profile"), warning.message());
	}

	private static Path exampleFile() {
		final Path workingDirectory = Paths.get(System.getProperty("user.dir"));
		final Path fromModule = workingDirectory.resolve("src/main/resources/resourcepacks/example_private_pack/assets/example_rail/aurelia_packs/example_rail.json");
		if (Files.isRegularFile(fromModule)) {
			return fromModule;
		}
		return workingDirectory.resolve("fabric/src/main/resources/resourcepacks/example_private_pack/assets/example_rail/aurelia_packs/example_rail.json");
	}
}
