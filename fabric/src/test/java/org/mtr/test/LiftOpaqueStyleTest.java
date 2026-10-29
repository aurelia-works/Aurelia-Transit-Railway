package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.core.data.Data;
import org.mtr.core.data.Lift;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.MessagePackReader;
import org.mtr.core.serializer.MessagePackWriter;
import org.mtr.core.tool.Utilities;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.LiftResource;
import org.mtr.libraries.org.msgpack.core.MessageBufferPacker;
import org.mtr.libraries.org.msgpack.core.MessagePack;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.mod.resource.LiftGlassRegions;
import org.mtr.mod.resource.LiftStyleIds;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opaque lift style: the glass atlas's wall and door panes can be filled independently, the committed opaque
 * atlas is exactly that fill, and the style id round-trips through the same JSON the refresher and the lift
 * sync already use. A save with no style, or with the original glass id, does not become opaque.
 */
class LiftOpaqueStyleTest {

	@Test
	void opaqueAtlasIsTheGlassAtlasWithBothPanesFilled() throws Exception {
		final BufferedImage glass = readAtlas("lift_1.png");
		final BufferedImage opaque = readAtlas("lift_1_opaque.png");
		final BufferedImage filled = LiftGlassRegions.fillTransparent(glass, true, true, LiftGlassRegions.PANEL_ARGB);
		assertEquals(LiftGlassRegions.ATLAS_SIZE, glass.getWidth());
		assertEquals(LiftGlassRegions.ATLAS_SIZE, glass.getHeight());
		int changed = 0;
		for (int y = 0; y < LiftGlassRegions.ATLAS_SIZE; y++) {
			for (int x = 0; x < LiftGlassRegions.ATLAS_SIZE; x++) {
				final int before = glass.getRGB(x, y);
				final int after = opaque.getRGB(x, y);
				assertEquals(filled.getRGB(x, y), after, "opaque atlas differs from a pane fill at " + x + "," + y);
				if ((before >>> 24) != 0) {
					assertEquals(before, after, "already-opaque texel changed at " + x + "," + y);
				}
				if (before != after) {
					changed++;
					assertEquals(0, before >>> 24);
					assertEquals(LiftGlassRegions.PANEL_ARGB, after);
				}
			}
		}
		assertTrue(changed > 1000, "opaque atlas should fill the glass panes");
	}

	@Test
	void wallsAndDoorsFillSeparateTexels() throws Exception {
		final BufferedImage glass = readAtlas("lift_1.png");
		final BufferedImage wallsOnly = LiftGlassRegions.fillTransparent(glass, true, false, LiftGlassRegions.PANEL_ARGB);
		final BufferedImage doorsOnly = LiftGlassRegions.fillTransparent(glass, false, true, 0xFF224466);
		assertNotEquals(0, countChanged(glass, wallsOnly));
		assertNotEquals(0, countChanged(glass, doorsOnly));
		for (int y = 0; y < LiftGlassRegions.ATLAS_SIZE; y++) {
			for (int x = 0; x < LiftGlassRegions.ATLAS_SIZE; x++) {
				final boolean wallChanged = glass.getRGB(x, y) != wallsOnly.getRGB(x, y);
				final boolean doorChanged = glass.getRGB(x, y) != doorsOnly.getRGB(x, y);
				assertFalse(wallChanged && doorChanged, "texel " + x + "," + y + " is both a wall pane and a door pane");
			}
		}
		assertTrue(LiftGlassRegions.rectCount(LiftGlassRegions.Part.WALL) > 0);
		assertTrue(LiftGlassRegions.rectCount(LiftGlassRegions.Part.DOOR) > 0);
	}

	@Test
	void builtinStylesStayInOrderAndTheGlassEntryOmitsTheNewFields() throws Exception {
		final String json = Files.readString(Path.of("src/main/mtr_custom_resources_template.json"));
		final JsonObject root = Utilities.parseJson(json);
		final var lifts = root.getAsJsonArray("lifts");
		assertEquals(2, lifts.size());
		final JsonObject glass = lifts.get(0).getAsJsonObject();
		final JsonObject opaque = lifts.get(1).getAsJsonObject();
		assertEquals(LiftStyleIds.TRANSPARENT, glass.get("id").getAsString());
		assertEquals(LiftStyleIds.GLASS_TEXTURE, glass.get("textureResource").getAsString());
		assertFalse(glass.has("walls"), "the original style must keep the schema defaults, not a new required field");
		assertFalse(glass.has("doors"));
		assertFalse(glass.has("model"));
		assertEquals(LiftStyleIds.OPAQUE, opaque.get("id").getAsString());
		assertEquals("lift.mtr.default_opaque", opaque.get("name").getAsString());
		assertEquals(LiftStyleIds.OPAQUE_TEXTURE, opaque.get("textureResource").getAsString());
		assertEquals(LiftStyleIds.MODEL_LIFT_1, opaque.get("model").getAsString());
		assertEquals(LiftStyleIds.OPAQUE_PART, opaque.get("walls").getAsString());
		assertEquals(LiftStyleIds.OPAQUE_PART, opaque.get("doors").getAsString());
		final String english = Files.readString(Path.of("src/main/resources/assets/mtr/lang/en_us.json"));
		assertTrue(english.contains("\"lift.mtr.default_opaque\": \"Default Lift (Opaque)\""));
		assertTrue(english.contains("\"lift.mtr.default_transparent\": \"Default Lift (Transparent)\""));
	}

	@Test
	void missingOrUnknownStyleDrawsAsGlassAndIsNotRewritten() {
		assertEquals(LiftStyleIds.TRANSPARENT, LiftStyleIds.resolve(null, false));
		assertEquals(LiftStyleIds.TRANSPARENT, LiftStyleIds.resolve("", false));
		assertEquals(LiftStyleIds.TRANSPARENT, LiftStyleIds.resolve("", true));
		assertEquals(LiftStyleIds.TRANSPARENT, LiftStyleIds.resolve("pack_only", false));
		assertEquals("pack_only", LiftStyleIds.resolve("pack_only", true));
		assertEquals(LiftStyleIds.OPAQUE, LiftStyleIds.resolve(LiftStyleIds.OPAQUE, true));
		assertEquals(LiftStyleIds.TRANSPARENT, LiftStyleIds.resolve(LiftStyleIds.TRANSPARENT, true));
		assertEquals(0, LiftStyleIds.fallbackIndex(List.of(LiftStyleIds.TRANSPARENT, LiftStyleIds.OPAQUE)));
		assertEquals(1, LiftStyleIds.fallbackIndex(List.of("pack_first", LiftStyleIds.TRANSPARENT, LiftStyleIds.OPAQUE)));
		assertEquals(0, LiftStyleIds.fallbackIndex(List.of("only_pack")));
	}

	@Test
	void textureFollowsAnExplicitAtlasUnlessBothPartsAskForTheBuiltinOpaqueCab() {
		assertEquals(LiftStyleIds.GLASS_TEXTURE, LiftStyleIds.cabTexturePath(LiftStyleIds.GLASS_TEXTURE, LiftStyleIds.GLASS, LiftStyleIds.GLASS));
		assertEquals(LiftStyleIds.OPAQUE_TEXTURE, LiftStyleIds.cabTexturePath(LiftStyleIds.GLASS_TEXTURE, LiftStyleIds.OPAQUE_PART, LiftStyleIds.OPAQUE_PART));
		assertEquals(LiftStyleIds.GLASS_TEXTURE, LiftStyleIds.cabTexturePath(LiftStyleIds.GLASS_TEXTURE, LiftStyleIds.OPAQUE_PART, LiftStyleIds.GLASS));
		assertEquals(LiftStyleIds.GLASS_TEXTURE, LiftStyleIds.cabTexturePath(LiftStyleIds.GLASS_TEXTURE, LiftStyleIds.GLASS, LiftStyleIds.OPAQUE_PART));
		assertEquals("mtr:textures/vehicle/custom.png", LiftStyleIds.cabTexturePath("mtr:textures/vehicle/custom.png", LiftStyleIds.OPAQUE_PART, LiftStyleIds.OPAQUE_PART));
		assertEquals(LiftStyleIds.OPAQUE_TEXTURE, LiftStyleIds.cabTexturePath(LiftStyleIds.OPAQUE_TEXTURE, LiftStyleIds.OPAQUE_PART, LiftStyleIds.OPAQUE_PART));
		assertTrue(LiftStyleIds.usesLift1Model(null));
		assertTrue(LiftStyleIds.usesLift1Model(""));
		assertTrue(LiftStyleIds.usesLift1Model(LiftStyleIds.MODEL_LIFT_1));
		assertFalse(LiftStyleIds.usesLift1Model("mtr:lift_custom"));
	}

	@Test
	void styleIdsMatchTheResourceLoaderConstants() {
		assertEquals(LiftStyleIds.TRANSPARENT, CustomResourceLoader.DEFAULT_LIFT_TRANSPARENT_ID);
		assertEquals(LiftStyleIds.OPAQUE, CustomResourceLoader.DEFAULT_LIFT_OPAQUE_ID);
	}

	@Test
	void omittedWallsAndDoorsStayGlassAndTheOpaqueEntryUsesTheOpaqueAtlas() {
		final LiftResource glass = new LiftResource(JsonReader.parse("{\"id\":\"default_transparent\",\"name\":\"lift.mtr.default_transparent\",\"color\":\"CCCCCC\",\"textureResource\":\"mtr:textures/vehicle/lift_1.png\"}"));
		assertEquals(LiftStyleIds.GLASS, glass.getWalls());
		assertEquals(LiftStyleIds.GLASS, glass.getDoors());
		assertEquals(LiftStyleIds.MODEL_LIFT_1, glass.getModel());
		assertEquals("mtr:textures/vehicle/lift_1.png", glass.cabTexture().data.toString());

		final LiftResource opaque = new LiftResource(JsonReader.parse("{\"id\":\"default_opaque\",\"name\":\"lift.mtr.default_opaque\",\"color\":\"7A7A7A\",\"textureResource\":\"mtr:textures/vehicle/lift_1_opaque.png\",\"model\":\"mtr:lift_1\",\"walls\":\"opaque\",\"doors\":\"opaque\"}"));
		assertEquals(LiftStyleIds.OPAQUE_PART, opaque.getWalls());
		assertEquals(LiftStyleIds.OPAQUE_PART, opaque.getDoors());
		assertEquals("mtr:textures/vehicle/lift_1_opaque.png", opaque.cabTexture().data.toString());

		final LiftResource flagsOnly = new LiftResource(JsonReader.parse("{\"id\":\"flags\",\"name\":\"x\",\"color\":\"0\",\"textureResource\":\"mtr:textures/vehicle/lift_1.png\",\"walls\":\"opaque\",\"doors\":\"opaque\"}"));
		assertEquals("mtr:textures/vehicle/lift_1_opaque.png", flagsOnly.cabTexture().data.toString());

		final LiftResource wallsOnly = new LiftResource(JsonReader.parse("{\"id\":\"walls\",\"name\":\"x\",\"color\":\"0\",\"textureResource\":\"mtr:textures/vehicle/lift_1.png\",\"walls\":\"opaque\",\"doors\":\"glass\"}"));
		assertEquals("mtr:textures/vehicle/lift_1.png", wallsOnly.cabTexture().data.toString());
	}

	@Test
	void styleRoundTripsInTheLiftJsonAndAMissingStyleStaysBlank() throws Exception {
		final Data data = new Data() {
		};
		final Lift opaque = new Lift(data);
		opaque.setStyle(LiftStyleIds.OPAQUE);
		final Lift reloaded = new Lift(JsonReader.parse(Utilities.getJsonObjectFromData(opaque).toString()), new Data() {
		});
		assertEquals(LiftStyleIds.OPAQUE, reloaded.getStyle());

		final Lift glass = new Lift(new Data() {
		});
		glass.setStyle(LiftStyleIds.TRANSPARENT);
		final JsonObject saved = Utilities.getJsonObjectFromData(glass);
		assertEquals(LiftStyleIds.TRANSPARENT, saved.get("style").getAsString());
		saved.remove("style");
		final Lift fromOldSave = new Lift(new JsonReader(saved), new Data() {
		});
		assertEquals("", fromOldSave.getStyle());
		assertEquals(LiftStyleIds.TRANSPARENT, LiftStyleIds.resolve(fromOldSave.getStyle(), false));

		// The simulation save is MessagePack. DataFixer copies the pre-4.0 key lift_style onto style, and only when the
		// reader is MessagePack (JsonReader.merge ignores a MessagePack payload, which is how the refresher packet works).
		assertEquals(LiftStyleIds.TRANSPARENT, readMessagePackStyle(legacyPack("lift_style", LiftStyleIds.TRANSPARENT)));
		assertEquals(LiftStyleIds.OPAQUE, readMessagePackStyle(legacyPack("lift_style", LiftStyleIds.OPAQUE)));
		assertEquals("", readMessagePackStyle(legacyPack("unrelated", "x")));

		final MessageBufferPacker modern = MessagePack.newDefaultBufferPacker();
		final MessagePackWriter modernWriter = new MessagePackWriter(modern);
		opaque.serializeData(modernWriter);
		modernWriter.serialize();
		assertEquals(LiftStyleIds.OPAQUE, readMessagePackStyle(modern.toByteArray()));

		final Lift unknownKept = new Lift(new Data() {
		});
		unknownKept.setStyle("pack_only");
		final Lift unknownReloaded = new Lift(JsonReader.parse(Utilities.getJsonObjectFromData(unknownKept).toString()), new Data() {
		});
		assertEquals("pack_only", unknownReloaded.getStyle());
		assertEquals(LiftStyleIds.TRANSPARENT, LiftStyleIds.resolve(unknownReloaded.getStyle(), false));
	}

	private static byte[] legacyPack(String key, String value) throws Exception {
		final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
		final MessagePackWriter writer = new MessagePackWriter(packer);
		writer.writeString(key, value);
		writer.serialize();
		return packer.toByteArray();
	}

	private static String readMessagePackStyle(byte[] bytes) throws Exception {
		return new Lift(new MessagePackReader(MessagePack.newDefaultUnpacker(bytes)), new Data() {
		}).getStyle();
	}

	private static BufferedImage readAtlas(String fileName) throws Exception {
		final Path path = Path.of("src/main/resources/assets/mtr/textures/vehicle", fileName);
		try (InputStream inputStream = Files.newInputStream(path)) {
			return ImageIO.read(inputStream);
		}
	}

	private static int countChanged(BufferedImage before, BufferedImage after) {
		int changed = 0;
		for (int y = 0; y < before.getHeight(); y++) {
			for (int x = 0; x < before.getWidth(); x++) {
				if (before.getRGB(x, y) != after.getRGB(x, y)) {
					changed++;
				}
			}
		}
		return changed;
	}
}
