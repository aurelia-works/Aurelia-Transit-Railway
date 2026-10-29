package org.mtr.mod.resource;

import java.awt.image.BufferedImage;

/**
 * Glass panes on the 512×512 {@code lift_1} atlas (the model’s UV space is 128, and the file is four times that).
 *
 * <p>Each rect is a face of a wall or a door. The pane is the transparent texels; the frame, handrail and other
 * already-opaque texels stay as they are. Filling a part is how the opaque style is made, and how a later custom
 * wall or door can replace just that part: copy the source, call {@link #fillTransparent} for {@link Part#WALL}
 * or {@link Part#DOOR} (or both) with a flat colour or with texels taken from a custom texture. Door motion
 * (single-speed, two-speed, and the rest of upstream issue 598) is the model, not these rects.
 */
public final class LiftGlassRegions {

	public enum Part {
		WALL,
		DOOR
	}

	public static final int ATLAS_SIZE = 512;
	/** Panel grey already used by the door metal, so a filled pane meets the frame. */
	public static final int PANEL_ARGB = 0xFF7A7A7A;

	/** x, y, width, height in atlas pixels. Wall faces, including the thin edges of the glass. */
	private static final int[] WALLS = {
			304, 136, 4, 128,
			308, 132, 32, 4,
			308, 136, 32, 128,
			340, 132, 32, 4,
			340, 136, 4, 128,
			344, 136, 32, 128,
			432, 384, 4, 128,
			436, 380, 16, 4,
			436, 384, 16, 128,
			448, 252, 4, 128,
			452, 248, 20, 4,
			452, 252, 20, 128,
			452, 380, 16, 4,
			452, 384, 4, 128,
			456, 384, 16, 128,
			472, 248, 20, 4,
			472, 252, 4, 128,
			476, 252, 20, 128
	};

	/** Door panes, both sides. The solid door edge is not in this list. */
	private static final int[] DOORS = {
			8, 208, 48, 128,
			64, 208, 48, 128,
			112, 272, 48, 128,
			160, 272, 48, 128,
			200, 144, 48, 128,
			208, 272, 48, 128,
			256, 272, 48, 128
	};

	private LiftGlassRegions() {
	}

	public static int rectCount(Part part) {
		return rects(part).length / 4;
	}

	public static int[] rects(Part part) {
		final int[] source = part == Part.WALL ? WALLS : DOORS;
		final int[] copy = new int[source.length];
		System.arraycopy(source, 0, copy, 0, source.length);
		return copy;
	}

	/** Copy of {@code source} with transparent texels inside the requested parts set to {@code argb}. */
	public static BufferedImage fillTransparent(BufferedImage source, boolean walls, boolean doors, int argb) {
		if (source.getWidth() != ATLAS_SIZE || source.getHeight() != ATLAS_SIZE) {
			throw new IllegalArgumentException("lift atlas must be " + ATLAS_SIZE + "x" + ATLAS_SIZE);
		}
		final BufferedImage copy = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		copy.setRGB(0, 0, ATLAS_SIZE, ATLAS_SIZE, source.getRGB(0, 0, ATLAS_SIZE, ATLAS_SIZE, null, 0, ATLAS_SIZE), 0, ATLAS_SIZE);
		if (walls) {
			fill(copy, WALLS, argb);
		}
		if (doors) {
			fill(copy, DOORS, argb);
		}
		return copy;
	}

	private static void fill(BufferedImage image, int[] rects, int argb) {
		for (int i = 0; i < rects.length; i += 4) {
			final int x0 = rects[i];
			final int y0 = rects[i + 1];
			final int width = rects[i + 2];
			final int height = rects[i + 3];
			for (int y = y0; y < y0 + height; y++) {
				for (int x = x0; x < x0 + width; x++) {
					if ((image.getRGB(x, y) >>> 24) == 0) {
						image.setRGB(x, y, argb);
					}
				}
			}
		}
	}
}
