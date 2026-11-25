package org.mtr.aurelia.map;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * The file form of a {@link MapRegion}: a magic number, a format version, the side length, then every column's colour
 * and height, deflated. A file of another version or size is refused, so the caller starts that region afresh.
 */
public final class MapRegionCodec {

	private static final int MAGIC = 0x4154524D; // "ATRM"
	private static final int VERSION = 1;
	private static final int TILE_MAGIC = 0x41545254; // "ATRT"

	private MapRegionCodec() {
	}

	public static void write(OutputStream out, int[] colors, short[] heights) throws IOException {
		final DeflaterOutputStream deflated = new DeflaterOutputStream(out);
		final DataOutputStream data = new DataOutputStream(deflated);
		data.writeInt(MAGIC);
		data.writeInt(VERSION);
		data.writeInt(MapRegion.SIZE);
		for (final int color : colors) {
			data.writeInt(color);
		}
		for (final short height : heights) {
			data.writeShort(height);
		}
		data.flush();
		deflated.finish();
	}

	public static MapRegion read(InputStream in, int regionX, int regionZ) throws IOException {
		final DataInputStream data = new DataInputStream(new InflaterInputStream(in));
		if (data.readInt() != MAGIC || data.readInt() != VERSION || data.readInt() != MapRegion.SIZE) {
			throw new IOException("not an ATR map region of this version");
		}
		final int[] colors = new int[MapRegion.SIZE * MapRegion.SIZE];
		final short[] heights = new short[MapRegion.SIZE * MapRegion.SIZE];
		for (int i = 0; i < colors.length; i++) {
			colors[i] = data.readInt();
		}
		for (int i = 0; i < heights.length; i++) {
			heights[i] = data.readShort();
		}
		return new MapRegion(regionX, regionZ, colors, heights);
	}

	/** A small overview picture of a region, {@code size} pixels a side, in the same deflated form. */
	public static void writeTile(OutputStream out, int[] pixels, int size) throws IOException {
		final DeflaterOutputStream deflated = new DeflaterOutputStream(out);
		final DataOutputStream data = new DataOutputStream(deflated);
		data.writeInt(TILE_MAGIC);
		data.writeInt(VERSION);
		data.writeInt(size);
		for (final int pixel : pixels) {
			data.writeInt(pixel);
		}
		data.flush();
		deflated.finish();
	}

	public static int[] readTile(InputStream in, int size) throws IOException {
		final DataInputStream data = new DataInputStream(new InflaterInputStream(in));
		if (data.readInt() != TILE_MAGIC || data.readInt() != VERSION || data.readInt() != size) {
			throw new IOException("not an ATR map tile of this version");
		}
		final int[] pixels = new int[size * size];
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = data.readInt();
		}
		return pixels;
	}
}
