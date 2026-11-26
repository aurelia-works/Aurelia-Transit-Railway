package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Add-ons (JCM, MAGIC, Tianjin and others) hook MTR's methods by name and descriptor, including javac's numbered lambdas
 * such as {@code RenderVehicles.lambda$render$5}. Every method of MTR 4.0.5's {@code org.mtr.mod} classes must still exist in ATR
 * with the same name and descriptor, or those hooks fail and the add-on breaks.
 * New code goes into new methods: no new lambdas before existing ones and no new locals captured by existing lambdas.
 */
public final class DropInSignaturesTest {

	private static final String PREFIX = "org/mtr/mod/";
	/** Generated from the config schema; no add-on hooks it, and new config keys renumber its lambdas. */
	private static final String GENERATED = "org/mtr/mod/generated/";
	private static final int ACC_STATIC = 0x0008;
	/**
	 * Fields of a private nested class whose type changed for multi-flange and per-speed running sounds. An add-on can't
	 * point an accessor at a private nested class without naming it by string, and none of the add-ons we checked does.
	 */
	private static final List<String> RESHAPED_FIELDS = List.of(
			"org/mtr/mod/sound/BveVehicleSound$VehicleLoopingSoundHolder.soundLoopFlangeLorg/mtr/mod/sound/VehicleLoopingSoundInstance;",
			"org/mtr/mod/sound/BveVehicleSound$VehicleLoopingSoundHolder.soundLoopRunLorg/mtr/mod/sound/VehicleLoopingSoundInstance;"
	);

	@Test
	public void everyMtr405MethodStillExists() throws IOException {
		final List<String> missing = missingMembers(false);
		Assertions.assertEquals(List.of(), missing, missing.size() + " MTR 4.0.5 methods are missing or changed");
	}

	/**
	 * Add-ons also read MTR's fields by name with {@code @Accessor} mixins, which crash the game at launch when the field is gone
	 * (MTR Rail Works reads {@code ItemNodeModifierSelectableBlockBase.radius}).
	 */
	@Test
	public void everyMtr405FieldStillExists() throws IOException {
		final List<String> missing = missingMembers(true);
		for (final String reshaped : RESHAPED_FIELDS) {
			// Excused only while a field of that name is still there with its new type
			final String className = reshaped.substring(0, reshaped.lastIndexOf('.'));
			final String fieldName = reshaped.substring(reshaped.lastIndexOf('.') + 1, reshaped.indexOf("Lorg/", reshaped.lastIndexOf('.')));
			try (final InputStream inputStream = DropInSignaturesTest.class.getClassLoader().getResourceAsStream(className + ".class")) {
				Assertions.assertNotNull(inputStream, className);
				Assertions.assertTrue(members(inputStream, true).stream().anyMatch(field -> field.startsWith(fieldName + "[") || field.startsWith(fieldName + "L")), reshaped + " is gone, not just reshaped");
			}
			missing.remove(reshaped);
		}
		Assertions.assertEquals(List.of(), missing, missing.size() + " MTR 4.0.5 fields are missing or changed");
	}

	private static List<String> missingMembers(boolean fields) throws IOException {
		final String baselineJar = System.getProperty("atr.mtrBaselineJar");
		Assertions.assertNotNull(baselineJar, "atr.mtrBaselineJar is set by fabric/build.gradle");
		final List<String> missing = new ArrayList<>();
		int classes = 0;

		try (final ZipFile zipFile = new ZipFile(baselineJar)) {
			final Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				final String name = entries.nextElement().getName();
				if (!name.startsWith(PREFIX) || name.startsWith(GENERATED) || !name.endsWith(".class")) {
					continue;
				}
				classes++;
				final Set<String> baselineMethods;
				try (final InputStream inputStream = zipFile.getInputStream(zipFile.getEntry(name))) {
					baselineMethods = members(inputStream, fields);
				}
				try (final InputStream inputStream = DropInSignaturesTest.class.getClassLoader().getResourceAsStream(name)) {
					if (inputStream == null) {
						missing.add(name + " (class gone)");
						continue;
					}
					final Set<String> atrMethods = members(inputStream, fields);
					for (final String method : baselineMethods) {
						// Minecraft types and overridden Minecraft methods (method_1234) are named differently in the dev build than in released jars
						if (!method.contains("net/minecraft/") && !method.contains("com/mojang/") && !method.replace("static ", "").startsWith(fields ? "field_" : "method_") && !atrMethods.contains(method)) {
							missing.add(name.substring(0, name.length() - 6) + "." + method);
						}
					}
				}
			}
		}

		Assertions.assertTrue(classes > 500, "baseline jar has the org.mtr.mod classes");
		missing.sort(String::compareTo);
		return missing;
	}

	/** Name plus descriptor of every field or every method in a class file. */
	private static Set<String> members(InputStream inputStream, boolean fields) throws IOException {
		final DataInputStream in = new DataInputStream(inputStream);
		in.skipBytes(8);
		final int constantCount = in.readUnsignedShort();
		final String[] utf8 = new String[constantCount];
		for (int i = 1; i < constantCount; i++) {
			final int tag = in.readUnsignedByte();
			switch (tag) {
				case 1:
					final byte[] bytes = new byte[in.readUnsignedShort()];
					in.readFully(bytes);
					utf8[i] = new String(bytes, StandardCharsets.UTF_8);
					break;
				case 3:
				case 4:
				case 9:
				case 10:
				case 11:
				case 12:
				case 17:
				case 18:
					in.skipBytes(4);
					break;
				case 5:
				case 6:
					in.skipBytes(8);
					i++;
					break;
				case 7:
				case 8:
				case 16:
				case 19:
				case 20:
					in.skipBytes(2);
					break;
				case 15:
					in.skipBytes(3);
					break;
				default:
					throw new IOException("Unknown constant tag " + tag);
			}
		}
		in.skipBytes(6);
		in.skipBytes(2 * in.readUnsignedShort());
		final Set<String> fieldNames = new HashSet<>();
		skipMembers(in, fieldNames, utf8);
		final Set<String> methodNames = new HashSet<>();
		skipMembers(in, methodNames, utf8);
		return fields ? fieldNames : methodNames;
	}

	private static void skipMembers(DataInputStream in, Set<String> names, String[] utf8) throws IOException {
		final int count = in.readUnsignedShort();
		for (int i = 0; i < count; i++) {
			final boolean isStatic = (in.readUnsignedShort() & ACC_STATIC) != 0;
			final String name = utf8[in.readUnsignedShort()];
			final String descriptor = utf8[in.readUnsignedShort()];
			if (names != null) {
				// The release jar relocates some libraries (occlusion culling) under org/mtr/libraries/; the dev classes do not
				// A mixin into a static method needs a static handler, so a method turning static or not breaks it too
				names.add((isStatic ? "static " : "") + name + descriptor.replace("org/mtr/libraries/com/", "com/"));
			}
			final int attributes = in.readUnsignedShort();
			for (int j = 0; j < attributes; j++) {
				in.skipBytes(2);
				in.skipBytes(in.readInt());
			}
		}
	}
}
