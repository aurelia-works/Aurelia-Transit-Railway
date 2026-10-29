package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.mod.client.DynamicTextureLimits;
import org.mtr.mod.render.RenderAPGGlass;
import org.mtr.mod.render.RenderPSDTop;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Upstream MTR 4.0.5 bug: a route map on automatic platform gates is cropped once the run is longer than 9 blocks,
 * then stretched across the whole run. See Minecraft-Transit-Railway issue 1500 (and 1332).
 * <p>
 * A single route has raw height 2 (one block of padding above the line and one below, stations sitting on y = 0).
 * The default dynamic-texture resolution is 2, so the texture scale is 128 and the upload cap is 8192.
 */
public final class PlatformGateTextureLimitTest {

	private static final int DEFAULT_RESOLUTION = 2;
	/** One ordinary route: the diagram is about as tall as two station spacings. */
	private static final float SIMPLE_ROUTE_RAW_WIDTH = 1F;
	private static final float SIMPLE_ROUTE_RAW_HEIGHT = 2F;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int BLUE = 0xFF0000FF;
	private static final int RED = 0xFFFF0000;
	private static final int GREEN = 0xFF00FF00;
	private static final int BLACK = 0xFF202020;

	@Test
	public void nineGatesStayWithinTheCapAndTenDoNot() {
		for (int resolution = 0; resolution <= 7; resolution++) {
			final int scale = DynamicTextureLimits.textureScale(resolution);
			final int max = DynamicTextureLimits.maxImageSize(resolution);
			Assertions.assertEquals((int) Math.pow(2, resolution + 5), scale);
			Assertions.assertEquals(2048 * (int) Math.pow(2, resolution), max);

			final DynamicTextureLimits.MapPixels nine = routeMap(RenderAPGGlass.mapAspectRatio(9), scale);
			final DynamicTextureLimits.MapPixels ten = routeMap(RenderAPGGlass.mapAspectRatio(10), scale);
			Assertions.assertTrue(nine.width <= max && nine.height <= max, "resolution " + resolution + " 9 gates " + nine.width + "x" + nine.height);
			Assertions.assertTrue(ten.width > max, "resolution " + resolution + " 10 gates should exceed the cap, was " + ten.width + "x" + ten.height);
			Assertions.assertEquals(nine.width, DynamicTextureLimits.fit(nine.width, nine.height, max).width);
			Assertions.assertEquals(nine.height, DynamicTextureLimits.fit(nine.width, nine.height, max).height);
			assertAspectPreserved(ten.width, ten.height, DynamicTextureLimits.fit(ten.width, ten.height, max), max);
		}
	}

	@Test
	public void defaultResolutionMatchesTheReportedTenGateBreak() {
		final int scale = DynamicTextureLimits.textureScale(DEFAULT_RESOLUTION);
		final int max = DynamicTextureLimits.maxImageSize(DEFAULT_RESOLUTION);
		Assertions.assertEquals(128, scale);
		Assertions.assertEquals(8192, max);

		final DynamicTextureLimits.MapPixels nine = routeMap(RenderAPGGlass.mapAspectRatio(9), scale);
		Assertions.assertEquals(8192, nine.width);
		Assertions.assertEquals(256, nine.height);
		Assertions.assertEquals(1F, nine.heightScale, 0F);

		final DynamicTextureLimits.MapPixels ten = routeMap(RenderAPGGlass.mapAspectRatio(10), scale);
		Assertions.assertEquals(9216, ten.width);
		Assertions.assertEquals(256, ten.height);
		// The old upload kept the full height and cut the width, so a map of aspect 36 was stored at aspect 32
		// and then stretched by 9216/8192 across the gates.
		final DynamicTextureLimits.Size cropped = legacyCrop(ten.width, ten.height, max);
		Assertions.assertEquals(8192, cropped.width);
		Assertions.assertEquals(256, cropped.height);
		Assertions.assertEquals(32D, cropped.aspect(), 0.001);
		Assertions.assertEquals(36D, ten.width / (double) ten.height, 0.001);

		final DynamicTextureLimits.Size fitted = DynamicTextureLimits.fit(ten.width, ten.height, max);
		Assertions.assertEquals(8192, fitted.width);
		Assertions.assertEquals(228, fitted.height);
		assertAspectPreserved(ten.width, ten.height, fitted, max);
	}

	@Test
	public void thirtyAndThreeHundredGatesKeepTheirAspect() {
		final int scale = DynamicTextureLimits.textureScale(DEFAULT_RESOLUTION);
		final int max = DynamicTextureLimits.maxImageSize(DEFAULT_RESOLUTION);
		for (final int gates : new int[]{11, 30, 31, 300}) {
			final DynamicTextureLimits.MapPixels map = routeMap(RenderAPGGlass.mapAspectRatio(gates), scale);
			Assertions.assertTrue(map.width > max, gates + " gates");
			assertAspectPreserved(map.width, map.height, DynamicTextureLimits.fit(map.width, map.height, max), max);
			final DynamicTextureLimits.Size arrow = DynamicTextureLimits.directionArrowPixels(RenderAPGGlass.mapAspectRatio(gates), scale);
			assertAspectPreserved(arrow.width, arrow.height, DynamicTextureLimits.fit(arrow.width, arrow.height, max), max);
		}
	}

	@Test
	public void screenDoorRouteMapBreaksAtFifteenNotTen() {
		final int scale = DynamicTextureLimits.textureScale(DEFAULT_RESOLUTION);
		final int max = DynamicTextureLimits.maxImageSize(DEFAULT_RESOLUTION);
		final DynamicTextureLimits.MapPixels fourteen = routeMap(RenderPSDTop.mapAspectRatio(14), scale);
		final DynamicTextureLimits.MapPixels fifteen = routeMap(RenderPSDTop.mapAspectRatio(15), scale);
		final DynamicTextureLimits.MapPixels ten = routeMap(RenderPSDTop.mapAspectRatio(10), scale);
		Assertions.assertTrue(ten.width <= max, "10 PSD tops are under the cap (" + ten.width + ")");
		Assertions.assertTrue(fourteen.width <= max, "14 PSD tops are under the cap (" + fourteen.width + ")");
		Assertions.assertTrue(fifteen.width > max, "15 PSD tops exceed the cap (" + fifteen.width + ")");
		Assertions.assertTrue(DynamicTextureLimits.fit(fourteen.width, fourteen.height, max).sameAs(fourteen.width, fourteen.height));
		assertAspectPreserved(fifteen.width, fifteen.height, DynamicTextureLimits.fit(fifteen.width, fifteen.height, max), max);
		for (final int gates : new int[]{30, 300}) {
			final DynamicTextureLimits.MapPixels map = routeMap(RenderPSDTop.mapAspectRatio(gates), scale);
			assertAspectPreserved(map.width, map.height, DynamicTextureLimits.fit(map.width, map.height, max), max);
		}
	}

	@Test
	public void enormousRunsShrinkTheGenerationScaleButTenGatesDoNot() {
		final int scale = DynamicTextureLimits.textureScale(DEFAULT_RESOLUTION);
		final int max = DynamicTextureLimits.maxImageSize(DEFAULT_RESOLUTION);
		final DynamicTextureLimits.MapPixels ten = routeMap(RenderAPGGlass.mapAspectRatio(10), scale);
		Assertions.assertEquals(scale, DynamicTextureLimits.generationScale(scale, ten.width, ten.height, max, DynamicTextureLimits.MAX_ALLOCATED_PIXELS));

		final DynamicTextureLimits.MapPixels threeHundred = routeMap(RenderAPGGlass.mapAspectRatio(300), scale);
		final int reduced = DynamicTextureLimits.generationScale(scale, threeHundred.width, threeHundred.height, max, DynamicTextureLimits.MAX_ALLOCATED_PIXELS);
		Assertions.assertTrue(reduced < scale && reduced >= 8, "reduced scale " + reduced);
		final DynamicTextureLimits.MapPixels reducedMap = routeMap(RenderAPGGlass.mapAspectRatio(300), reduced);
		Assertions.assertTrue((long) reducedMap.width * reducedMap.height <= DynamicTextureLimits.MAX_ALLOCATED_PIXELS);
		assertAspectPreserved(reducedMap.width, reducedMap.height, DynamicTextureLimits.fit(reducedMap.width, reducedMap.height, max), max);
	}

	@Test
	public void scalingKeepsBothEndsAndCroppingDoesNot() {
		final int[] source = {RED, RED, RED, GREEN, GREEN, GREEN};
		final int[] scaled = DynamicTextureLimits.scale(source, 6, 1, 2, 1);
		Assertions.assertEquals(RED, scaled[0]);
		Assertions.assertEquals(GREEN, scaled[1]);
		final int[] cropped = legacyCropPixels(source, 6, 1, 2, 1);
		Assertions.assertEquals(RED, cropped[0]);
		Assertions.assertEquals(RED, cropped[1]);

		final int[] identity = {RED, GREEN, BLUE, WHITE};
		Assertions.assertArrayEquals(identity, DynamicTextureLimits.scale(identity, 2, 2, 2, 2));
	}

	@Test
	public void displayedTenAndThirtyGateMaps() throws IOException {
		final int max = DynamicTextureLimits.maxImageSize(DEFAULT_RESOLUTION);
		final int scale = DynamicTextureLimits.textureScale(DEFAULT_RESOLUTION);
		final Path directory = Path.of("build", "reports", "psd-gate-text");
		Files.createDirectories(directory);

		assertDisplayedRun(9, scale, max, directory);
		assertDisplayedRun(10, scale, max, directory);
		assertDisplayedRun(30, scale, max, directory);
	}

	private static void assertDisplayedRun(int gates, int scale, int max, Path directory) throws IOException {
		final DynamicTextureLimits.MapPixels map = routeMap(RenderAPGGlass.mapAspectRatio(gates), scale);
		final int[] source = drawRoute(map.width, map.height);
		final int[] cropped = legacyCropPixels(source, map.width, map.height, Math.min(max, map.width), Math.min(max, map.height));
		final DynamicTextureLimits.Size fitted = DynamicTextureLimits.fit(map.width, map.height, max);
		final int[] scaled = fitted.sameAs(map.width, map.height) ? source : DynamicTextureLimits.scale(source, map.width, map.height, fitted.width, fitted.height);

		final int viewHeight = 160;
		final int viewWidth = Math.max(1, (int) Math.round(viewHeight * (map.width / (double) map.height)));
		final int[] before = DynamicTextureLimits.scale(cropped, Math.min(max, map.width), Math.min(max, map.height), viewWidth, viewHeight);
		final int[] after = DynamicTextureLimits.scale(scaled, fitted.width, fitted.height, viewWidth, viewHeight);

		if (gates <= 9) {
			Assertions.assertArrayEquals(before, after, "a run of " + gates + " must be unchanged");
			write(directory.resolve("gates-" + gates + "-unchanged.png"), before, viewWidth, viewHeight);
			return;
		}

		Assertions.assertFalse(Arrays.equals(before, after), gates + " gates should no longer match the cropped upload");
		Assertions.assertEquals(0, count(before, viewWidth, viewHeight, RED, 0, viewWidth), "cropped map drops the far station");
		Assertions.assertTrue(count(after, viewWidth, viewHeight, RED, viewWidth * 3 / 4, viewWidth) > 20, "scaled map keeps the far station");
		Assertions.assertTrue(count(before, viewWidth, viewHeight, GREEN, 0, viewWidth / 4) > 20);
		Assertions.assertTrue(count(after, viewWidth, viewHeight, GREEN, 0, viewWidth / 4) > 20);

		// On a 10-block run the middle station is still inside the cropped fragment, so it is drawn and stretched.
		// On a 30-block run the crop never reaches the middle, so that station disappears until the map is scaled.
		if (gates <= 10) {
			final double beforeCircle = boxAspect(before, viewWidth, viewHeight, BLUE);
			final double afterCircle = boxAspect(after, viewWidth, viewHeight, BLUE);
			Assertions.assertTrue(afterCircle < 1.15, "scaled circle aspect " + afterCircle);
			Assertions.assertTrue(beforeCircle > afterCircle * 1.08, "cropped circle is stretched " + beforeCircle + " vs " + afterCircle);
		} else {
			Assertions.assertEquals(0, count(before, viewWidth, viewHeight, BLUE, 0, viewWidth), "cropped map drops the middle station");
			Assertions.assertTrue(count(after, viewWidth, viewHeight, BLUE, viewWidth / 3, viewWidth * 2 / 3) > 20, "scaled map keeps the middle station");
		}

		write(directory.resolve("gates-" + gates + "-before.png"), before, viewWidth, viewHeight);
		write(directory.resolve("gates-" + gates + "-after.png"), after, viewWidth, viewHeight);
		write(directory.resolve("gates-" + gates + "-comparison.png"), stack(before, after, viewWidth, viewHeight), viewWidth, viewHeight * 2 + 8);
	}

	/** White panel, a round station mark in the middle, and a mark at each end. The end marks sit in the outer 2% of the run. */
	private static int[] drawRoute(int width, int height) {
		final int[] pixels = new int[width * height];
		Arrays.fill(pixels, WHITE);
		final int line = height / 2;
		for (int x = 0; x < width; x++) {
			for (int y = line - 4; y <= line + 4; y++) {
				pixels[y * width + x] = BLACK;
			}
		}
		disc(pixels, width, height, width / 2, height / 2, height / 5, BLUE);
		final int mark = Math.max(8, height / 6);
		square(pixels, width, height, mark, height / 2 - mark / 2, mark, GREEN);
		square(pixels, width, height, width - mark * 2, height / 2 - mark / 2, mark, RED);
		return pixels;
	}

	private static void disc(int[] pixels, int width, int height, int cx, int cy, int radius, int color) {
		for (int y = Math.max(0, cy - radius); y < Math.min(height, cy + radius); y++) {
			for (int x = Math.max(0, cx - radius); x < Math.min(width, cx + radius); x++) {
				final int dx = x - cx;
				final int dy = y - cy;
				if (dx * dx + dy * dy <= radius * radius) {
					pixels[y * width + x] = color;
				}
			}
		}
	}

	private static void square(int[] pixels, int width, int height, int x, int y, int size, int color) {
		for (int dy = 0; dy < size; dy++) {
			for (int dx = 0; dx < size; dx++) {
				final int px = x + dx;
				final int py = y + dy;
				if (px >= 0 && py >= 0 && px < width && py < height) {
					pixels[py * width + px] = color;
				}
			}
		}
	}

	private static int count(int[] pixels, int width, int height, int color, int x0, int x1) {
		int found = 0;
		for (int y = 0; y < height; y++) {
			for (int x = x0; x < x1; x++) {
				if (near(pixels[y * width + x], color)) {
					found++;
				}
			}
		}
		return found;
	}

	private static double boxAspect(int[] pixels, int width, int height, int color) {
		int minX = width;
		int maxX = 0;
		int minY = height;
		int maxY = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (near(pixels[y * width + x], color)) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
		}
		return (maxX - minX + 1) / (double) Math.max(1, maxY - minY + 1);
	}

	private static boolean near(int pixel, int color) {
		return Math.abs(((pixel >> 16) & 0xFF) - ((color >> 16) & 0xFF)) < 40
				&& Math.abs(((pixel >> 8) & 0xFF) - ((color >> 8) & 0xFF)) < 40
				&& Math.abs((pixel & 0xFF) - (color & 0xFF)) < 40
				&& ((pixel >>> 24) & 0xFF) > 200;
	}

	private static int[] stack(int[] before, int[] after, int width, int height) {
		final int gap = 8;
		final int[] stacked = new int[width * (height * 2 + gap)];
		Arrays.fill(stacked, BLACK);
		System.arraycopy(before, 0, stacked, 0, before.length);
		System.arraycopy(after, 0, stacked, width * (height + gap), after.length);
		return stacked;
	}

	private static void write(Path path, int[] pixels, int width, int height) throws IOException {
		final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, width, height, pixels, 0, width);
		ImageIO.write(image, "png", path.toFile());
	}

	/** What DynamicTextureCache used to upload: cut each axis to the cap and keep the top-left. */
	private static DynamicTextureLimits.Size legacyCrop(int width, int height, int max) {
		if (width <= max && height <= max) {
			return new DynamicTextureLimits.Size(width, height);
		}
		return new DynamicTextureLimits.Size(Math.min(max, width), Math.min(max, height));
	}

	private static int[] legacyCropPixels(int[] source, int width, int height, int croppedWidth, int croppedHeight) {
		final int[] cropped = new int[croppedWidth * croppedHeight];
		for (int y = 0; y < croppedHeight; y++) {
			System.arraycopy(source, y * width, cropped, y * croppedWidth, croppedWidth);
		}
		return cropped;
	}

	private static DynamicTextureLimits.MapPixels routeMap(float aspectRatio, int scale) {
		return DynamicTextureLimits.routeMapPixels(SIMPLE_ROUTE_RAW_WIDTH, SIMPLE_ROUTE_RAW_HEIGHT, aspectRatio, scale);
	}

	private static void assertAspectPreserved(int width, int height, DynamicTextureLimits.Size fitted, int max) {
		Assertions.assertTrue(fitted.width <= max && fitted.height <= max && fitted.width > 0 && fitted.height > 0);
		if (width <= max && height <= max) {
			Assertions.assertTrue(fitted.sameAs(width, height));
			return;
		}
		// The long side sits on the cap and the short side is the closest integer, which is the whole of the rounding error.
		if (width >= height) {
			Assertions.assertEquals(max, fitted.width);
			Assertions.assertEquals(Math.max(1, Math.min(max, (int) Math.round(height * (max / (double) width)))), fitted.height);
		} else {
			Assertions.assertEquals(max, fitted.height);
			Assertions.assertEquals(Math.max(1, Math.min(max, (int) Math.round(width * (max / (double) height)))), fitted.width);
		}
		final double original = width / (double) height;
		final DynamicTextureLimits.Size cropped = legacyCrop(width, height, max);
		Assertions.assertTrue(Math.abs(original - fitted.aspect()) < Math.abs(original - cropped.aspect()));
	}
}
