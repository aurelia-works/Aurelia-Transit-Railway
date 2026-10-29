package org.mtr.mod.client;

/**
 * Pixel size of the route maps, direction arrows and station names drawn across a run of platform screen doors or gates.
 * <p>
 * The picture is mapped with texture coordinates 0–1 across the whole run, so its width/height must match the run.
 * Images already within the driver's size cap are left untouched. Larger ones are scaled down uniformly; cropping one
 * axis (the previous behaviour) changed that ratio and the text was stretched along the platform.
 */
public final class DynamicTextureLimits {

	/** Size cap at dynamic texture resolution 0. Each higher resolution step doubles it, matching the texture scale. */
	public static final int BASE_MAX_IMAGE_SIZE = 2048;
	/**
	 * Largest picture allocated before the texture scale is reduced. A 300-block gate run at the default resolution
	 * would otherwise ask for a route map hundreds of megabytes wide, then throw the extra pixels away.
	 */
	public static final long MAX_ALLOCATED_PIXELS = 8_000_000L;

	private DynamicTextureLimits() {
	}

	/** Same value {@code RouteMapGenerator.setConstants} stores in {@code scale}. */
	public static int textureScale(int dynamicTextureResolution) {
		return (int) Math.pow(2, dynamicTextureResolution + 5);
	}

	/** Largest width or height uploaded for one dynamic texture at this resolution setting. */
	public static int maxImageSize(int dynamicTextureResolution) {
		return BASE_MAX_IMAGE_SIZE * (int) Math.pow(2, dynamicTextureResolution);
	}

	/**
	 * Aspect ratio of one continuous door/gate run. Paddings are in blocks, already divided by 16, as stored on the renderer.
	 */
	public static float runAspectRatio(int blocks, float topPadding, float bottomPadding, float sidePadding) {
		final float width = blocks - sidePadding * 2F;
		final float height = 1F - topPadding - bottomPadding;
		return width / height;
	}

	/**
	 * Route-map pixel size for one line diagram. This is the sizing branch of {@code RouteMapGenerator.generateRouteMap}.
	 */
	public static MapPixels routeMapPixels(float rawWidth, float rawHeight, float aspectRatio, int scale) {
		final int width;
		final int height;
		final float widthScale;
		final float heightScale;
		if (rawWidth / rawHeight > aspectRatio) {
			width = Math.round(rawWidth * scale);
			height = Math.round(width / aspectRatio);
			widthScale = 1;
			heightScale = height / rawHeight / scale;
		} else {
			height = Math.round(rawHeight * scale);
			width = Math.round(height * aspectRatio);
			heightScale = 1;
			widthScale = width / rawWidth / scale;
		}
		return new MapPixels(width, height, widthScale, heightScale);
	}

	/** Direction-arrow pixel size. Height is the texture scale; width follows the run's aspect ratio. */
	public static Size directionArrowPixels(float aspectRatio, int scale) {
		final int height = scale;
		final int width = Math.round(height * aspectRatio);
		return new Size(width, height);
	}

	/**
	 * Dimensions that fit inside {@code maxSize} on both axes. Returns the original size when it already fits, so a
	 * short run keeps every pixel.
	 */
	public static Size fit(int width, int height, int maxSize) {
		if (width <= 0 || height <= 0 || maxSize <= 0 || (width <= maxSize && height <= maxSize)) {
			return new Size(width, height);
		}
		if (width >= height) {
			final int fittedWidth = maxSize;
			final int fittedHeight = clampSize((int) Math.round(height * (maxSize / (double) width)), maxSize);
			return new Size(fittedWidth, fittedHeight);
		}
		final int fittedHeight = maxSize;
		final int fittedWidth = clampSize((int) Math.round(width * (maxSize / (double) height)), maxSize);
		return new Size(fittedWidth, fittedHeight);
	}

	/**
	 * Texture scale to draw at. Short runs, and runs that only just exceed the upload cap, keep {@code configuredScale}
	 * so the picture is identical up to the later uniform downscale. Enormous runs step down so the allocation stays
	 * within {@link #MAX_ALLOCATED_PIXELS}, but not below 8 when the configured scale is at least 8: lower than that,
	 * {@code scale / 8} becomes 0 and the route-map strokes disappear.
	 */
	public static int generationScale(int configuredScale, int width, int height, int maxSize, long pixelBudget) {
		if (configuredScale <= 1 || width <= 0 || height <= 0) {
			return Math.max(configuredScale, 1);
		}
		if (width <= maxSize && height <= maxSize) {
			return configuredScale;
		}
		final long pixels = (long) width * (long) height;
		if (pixels <= pixelBudget) {
			return configuredScale;
		}
		final int floor = configuredScale >= 8 ? 8 : 1;
		final int reduced = (int) Math.floor(configuredScale * Math.sqrt(pixelBudget / (double) pixels));
		final int stepped = Math.max(floor, Math.min(configuredScale - 1, reduced));
		return stepped >= configuredScale ? configuredScale : stepped;
	}

	/**
	 * Area-average scale. Each destination pixel is the weighted average of the source pixels it covers, so a uniform
	 * shrink keeps both ends of a wide map. A 1:1 scale copies the source.
	 */
	public static int[] scale(int[] source, int sourceWidth, int sourceHeight, int destWidth, int destHeight) {
		if (destWidth == sourceWidth && destHeight == sourceHeight) {
			return source.clone();
		}
		final int[] dest = new int[destWidth * destHeight];
		for (int y = 0; y < destHeight; y++) {
			final double y0 = y * (double) sourceHeight / destHeight;
			final double y1 = (y + 1) * (double) sourceHeight / destHeight;
			final int yStart = clampIndex((int) Math.floor(y0), sourceHeight);
			final int yEnd = clampIndex((int) Math.ceil(y1) - 1, sourceHeight);
			for (int x = 0; x < destWidth; x++) {
				final double x0 = x * (double) sourceWidth / destWidth;
				final double x1 = (x + 1) * (double) sourceWidth / destWidth;
				final int xStart = clampIndex((int) Math.floor(x0), sourceWidth);
				final int xEnd = clampIndex((int) Math.ceil(x1) - 1, sourceWidth);
				double weightSum = 0;
				double alpha = 0;
				double blue = 0;
				double green = 0;
				double red = 0;
				for (int sy = yStart; sy <= yEnd; sy++) {
					final double rowWeight = overlap(sy, y0, y1);
					if (rowWeight <= 0) {
						continue;
					}
					final int row = sy * sourceWidth;
					for (int sx = xStart; sx <= xEnd; sx++) {
						final double weight = rowWeight * overlap(sx, x0, x1);
						if (weight <= 0) {
							continue;
						}
						final int color = source[row + sx];
						alpha += ((color >>> 24) & 0xFF) * weight;
						blue += ((color >>> 16) & 0xFF) * weight;
						green += ((color >>> 8) & 0xFF) * weight;
						red += (color & 0xFF) * weight;
						weightSum += weight;
					}
				}
				dest[y * destWidth + x] = weightSum <= 0 ? 0 : channel(alpha, weightSum) << 24 | channel(blue, weightSum) << 16 | channel(green, weightSum) << 8 | channel(red, weightSum);
			}
		}
		return dest;
	}

	private static int clampSize(int value, int maxSize) {
		return Math.max(1, Math.min(maxSize, value));
	}

	private static int clampIndex(int index, int length) {
		return Math.max(0, Math.min(length - 1, index));
	}

	private static double overlap(int pixel, double start, double end) {
		final double low = Math.max(pixel, start);
		final double high = Math.min(pixel + 1.0, end);
		return Math.max(0, high - low);
	}

	private static int channel(double sum, double weight) {
		return Math.max(0, Math.min(255, (int) Math.round(sum / weight)));
	}

	public static final class Size {
		public final int width;
		public final int height;

		public Size(int width, int height) {
			this.width = width;
			this.height = height;
		}

		public boolean sameAs(int width, int height) {
			return this.width == width && this.height == height;
		}

		public double aspect() {
			return height == 0 ? 0 : width / (double) height;
		}
	}

	public static final class MapPixels {
		public final int width;
		public final int height;
		public final float widthScale;
		public final float heightScale;

		public MapPixels(int width, int height, float widthScale, float heightScale) {
			this.width = width;
			this.height = height;
			this.widthScale = widthScale;
			this.heightScale = heightScale;
		}
	}
}
