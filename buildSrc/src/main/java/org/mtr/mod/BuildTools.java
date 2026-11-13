package org.mtr.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.mtr.mapping.mixin.CreateAccessWidener;
import org.mtr.mapping.mixin.CreateClientWorldRenderingMixin;
import org.mtr.mapping.mixin.CreatePlayerRendererOffsetMixin;
import org.mtr.mapping.mixin.CreatePlayerTeleportationStateAccessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Build steps for the one target ATR has: Fabric on Minecraft 1.20.1. Dependency versions are pinned in gradle.properties. */
public class BuildTools {

	public final String minecraftVersion;
	public final int javaLanguageVersion = 17;

	private final Path path;
	private final String version;
	private final Logger logger;

	private static final String LOADER = "fabric";

	public BuildTools(String minecraftVersion, Project project) throws IOException {
		this.minecraftVersion = minecraftVersion;
		path = project.getProjectDir().toPath();
		version = project.getVersion().toString();
		logger = project.getLogger();

		final Path accessWidenerPath = path.resolve("src/main/resources");
		Files.createDirectories(accessWidenerPath);
		CreateAccessWidener.create(minecraftVersion, LOADER, accessWidenerPath.resolve("mtr.accesswidener"));

		final Path mixinPath = path.resolve("src/main/java/org/mtr/mixin");
		Files.createDirectories(mixinPath);
		CreateClientWorldRenderingMixin.create(minecraftVersion, LOADER, mixinPath, "org.mtr.mixin");
		CreatePlayerTeleportationStateAccessor.create(minecraftVersion, LOADER, mixinPath, "org.mtr.mixin");
		CreatePlayerRendererOffsetMixin.create(minecraftVersion, LOADER, mixinPath, "org.mtr.mixin");
	}

	public void generateTranslations() throws IOException {
		final StringBuilder stringBuilder = new StringBuilder("package org.mtr.mod.generated.lang;import org.mtr.mapping.holder.MutableText;import org.mtr.mapping.holder.Text;import org.mtr.mapping.mapper.GraphicsHolder;import org.mtr.mapping.mapper.TextHelper;public interface TranslationProvider{\n");
		JsonParser.parseString(FileUtils.readFileToString(path.resolve("src/main/resources/assets/mtr/lang/en_us.json").toFile(), StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(entry -> {
			final String key = entry.getKey();
			if (key.startsWith("block.") || key.startsWith("item.") || key.startsWith("entity.") || key.startsWith("itemGroup.")) {
				stringBuilder.append("@SuppressWarnings(\"unused\")");
			}
			stringBuilder.append(String.format("TranslationHolder %s=new TranslationHolder(\"%s\");\n", key.replace(".", "_").toUpperCase(Locale.ENGLISH), key));
		});
		stringBuilder.append("class TranslationHolder{public final String key;private TranslationHolder(String key){this.key=key;}\n");
		stringBuilder.append("public MutableText getMutableText(Object...arguments){return TextHelper.translatable(key,arguments);}\n");
		stringBuilder.append("public Text getText(Object...arguments){return new Text(TextHelper.translatable(key,arguments).data);}\n");
		stringBuilder.append("public String getString(Object...arguments){return getMutableText(arguments).getString();}\n");
		stringBuilder.append("public int width(Object...arguments){return GraphicsHolder.getTextWidth(getMutableText(arguments));}\n");
		stringBuilder.append("}}");
		FileUtils.write(path.resolve("src/main/java/org/mtr/mod/generated/lang/TranslationProvider.java").toFile(), stringBuilder.toString(), StandardCharsets.UTF_8);
	}

	public void copyLootTables(String namespace) throws IOException {
		final Path directory = path.resolve("src/main/resources/data").resolve(namespace).resolve("loot_tables/blocks");
		Files.createDirectories(directory);
		try (final Stream<Path> stream = Files.list(path.resolve("src/main/loot_table_templates").resolve(namespace))) {
			stream.forEach(lootTablePath -> {
				try {
					FileUtils.write(
							directory.resolve(lootTablePath.getFileName()).toFile(),
							FileUtils.readFileToString(lootTablePath.toFile(), StandardCharsets.UTF_8).replace("@condition@", "any_of"),
							StandardCharsets.UTF_8
					);
				} catch (Exception e) {
					logger.error("", e);
				}
			});
		}
	}

	public void copyFontDefinition() throws IOException {
		final String modernFont = "reference\",\"id\":\"minecraft:include/space\"},{\"type\":\"reference\",\"id\":\"minecraft:include/default\"},{\"type\":\"reference\",\"id\":\"minecraft:include/unifont";
		FileUtils.write(
				path.resolve("src/main/resources/assets/mtr/font/mtr.json").toFile(),
				FileUtils.readFileToString(path.resolve("src/main/font_template.json").toFile(), StandardCharsets.UTF_8).replace("@type@", modernFont),
				StandardCharsets.UTF_8
		);
	}

	public void copyVehicleTemplates() throws IOException {
		final List<String> vehicles = new ArrayList<>();

		try (final Stream<Path> stream = Files.list(path.resolve("src/main/vehicle_templates"))) {
			stream.sorted().forEach(vehicleTemplatePath -> {
				try {
					final JsonObject fileObject = JsonParser.parseString(FileUtils.readFileToString(vehicleTemplatePath.toFile(), StandardCharsets.UTF_8)).getAsJsonObject();
					final JsonObject replacementObject = fileObject.getAsJsonObject("replacements");
					final int variationCount = replacementObject.entrySet().stream().map(Map.Entry::getValue).findFirst().orElse(new JsonArray()).getAsJsonArray().size();

					fileObject.getAsJsonArray("vehicles").forEach(vehicleElement -> {
						// a template that gives its own gear positions (an airliner's nose and main gear) keeps them
						final JsonObject vehicleTemplate = vehicleElement.getAsJsonObject();
						final boolean ownGear = vehicleTemplate.has("bogie1Position") && vehicleTemplate.has("transportMode") && "AIRPLANE".equals(vehicleTemplate.get("transportMode").getAsString());
						for (int i = 0; i < variationCount; i++) {
							final JsonObject vehicleObject = vehicleElement.getAsJsonObject();
							final double length = replacementObject.getAsJsonArray("lengths").get(i).getAsDouble();
							if (length > 0) {
								final String id = vehicleObject.get("id").getAsString();
								vehicleObject.addProperty("length", length);

								if (ownGear) {
									// nothing to work out
								} else if (replacementObject.toString().contains("boat_small") && replacementObject.toString().contains("boat_medium")) {
									vehicleObject.addProperty("bogie1Position", -1);
									vehicleObject.addProperty("bogie2Position", 1);
								} else if (replacementObject.toString().contains("a320")) {
									vehicleObject.addProperty("bogie1Position", -14.25);
									vehicleObject.addProperty("bogie2Position", -2);
								} else if (replacementObject.toString().contains("br_423")) {
									vehicleObject.addProperty("bogie1Position", -6);
									vehicleObject.addProperty("bogie2Position", 6);
								} else if (length <= 4 || length <= 14 && id.contains("cab_3")) {
									vehicleObject.addProperty("bogie1Position", 0);
									vehicleObject.addProperty("bogie2Position", 0);
								} else {
									vehicleObject.addProperty("bogie1Position", -length / 2 + (length <= 14 && (id.contains("trailer") || id.contains("cab_2")) ? 0 : 4));
									vehicleObject.addProperty("bogie2Position", length / 2 - (length <= 14 && (id.contains("trailer") || id.contains("cab_1")) ? 0 : 4));
								}
							}

							String newFileString = vehicleObject.toString();
							for (final Map.Entry<String, JsonElement> entry : replacementObject.entrySet()) {
								newFileString = newFileString.replace(String.format("@%s@", entry.getKey()), entry.getValue().getAsJsonArray().get(i).getAsString());
							}
							vehicles.add(newFileString);
						}
					});
				} catch (Exception e) {
					logger.error("", e);
				}
			});
		}

		FileUtils.write(
				path.resolve("src/main/resources/assets/mtr/mtr_custom_resources.json").toFile(),
				FileUtils.readFileToString(path.resolve("src/main/mtr_custom_resources_template.json").toFile(), StandardCharsets.UTF_8).replace("\"@token@\"", String.join(",", vehicles)),
				StandardCharsets.UTF_8
		);
	}

	public void copyBuildFile(boolean excludeAssets) throws IOException {
		final Path directory = path.getParent().resolve("build/release");
		Files.createDirectories(directory);
		Files.copy(path.resolve(String.format("build/libs/%s-%s.jar", LOADER, version)), directory.resolve(String.format("ATR-%s-%s-mc%s%s.jar", LOADER, version, minecraftVersion, excludeAssets ? "-server" : "")), StandardCopyOption.REPLACE_EXISTING);
	}

	/** {@code org.mtr.mod.Patreon} is part of MTR 4.0.5, so the class stays; ATR has no supporter list to put in it. */
	public void writeEmptyPatreonList() throws IOException {
		final String source = "package org.mtr.mod;public class Patreon{public final String name;public final String tierTitle;public final int tierAmount;public final int tierColor;"
				+ "private Patreon(String name,String tierTitle,int tierAmount,int tierColor){this.name=name;this.tierTitle=tierTitle;this.tierAmount=tierAmount;this.tierColor=tierColor;}public static Patreon[]PATREON_LIST={\n"
				+ "};}";
		FileUtils.write(path.resolve("src/main/java/org/mtr/mod/Patreon.java").toFile(), source, StandardCharsets.UTF_8);
	}
}
