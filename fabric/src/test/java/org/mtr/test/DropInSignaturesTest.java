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

	@Test
	public void everyMtr405MethodStillExists() throws IOException {
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
					baselineMethods = methods(inputStream);
				}
				try (final InputStream inputStream = DropInSignaturesTest.class.getClassLoader().getResourceAsStream(name)) {
					if (inputStream == null) {
						missing.add(name + " (class gone)");
						continue;
					}
					final Set<String> atrMethods = methods(inputStream);
					for (final String method : baselineMethods) {
						// Minecraft types and overridden Minecraft methods (method_1234) are named differently in the dev build than in released jars
						if (!method.contains("net/minecraft/") && !method.contains("com/mojang/") && !method.replace("static ", "").startsWith("method_") && !atrMethods.contains(method)) {
							missing.add(name.substring(0, name.length() - 6) + "." + method);
						}
					}
				}
			}
		}

		Assertions.assertTrue(classes > 500, "baseline jar has the org.mtr.mod classes");
		missing.sort(String::compareTo);
		Assertions.assertEquals(List.of(), missing, missing.size() + " MTR 4.0.5 methods are missing or changed");
	}

	/** Name plus descriptor of every method in a class file. */
	private static Set<String> methods(InputStream inputStream) throws IOException {
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
		skipMembers(in, null, utf8);
		final Set<String> methods = new HashSet<>();
		skipMembers(in, methods, utf8);
		return methods;
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
