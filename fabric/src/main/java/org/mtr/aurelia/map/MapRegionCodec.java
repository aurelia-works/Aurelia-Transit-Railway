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
}
