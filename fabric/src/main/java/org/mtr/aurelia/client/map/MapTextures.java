package org.mtr.aurelia.client.map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.mtr.aurelia.map.MapRegion;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * One GPU texture per explored region, uploaded again when the region changed. Uploads are rationed per frame
 * ({@link #beginFrame}) so opening the big map or a burst of scanning never stalls a frame.
 */
public final class MapTextures {

	private static final int UPLOADS_PER_FRAME = 3;

	private final Map<Long, Entry> textures = new HashMap<>();
	private int uploadsLeft;

	private static final class Entry {
		final NativeImageBackedTexture texture;
		final Identifier id;
		int version = -1;

		Entry(NativeImageBackedTexture texture, Identifier id) {
			this.texture = texture;
			this.id = id;
		}
	}

	public void beginFrame() {
		uploadsLeft = UPLOADS_PER_FRAME;
	}

	/** The texture showing {@code region}, or null when it has none yet and this frame's uploads are spent. */
	@Nullable
	public Identifier texture(MapRegion region) {
		Entry entry = textures.get(region.key());
		if (entry != null && entry.version == region.version()) {
			return entry.id;
		}
		if (uploadsLeft <= 0) {
			return entry == null ? null : entry.id;
		}
		uploadsLeft--;
		if (entry == null) {
			final NativeImageBackedTexture texture = new NativeImageBackedTexture(MapRegion.SIZE, MapRegion.SIZE, false);
			final Identifier id = new Identifier("mtr", "atr_map/r_" + region.regionX + "_" + region.regionZ);
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
			entry = new Entry(texture, id);
			textures.put(region.key(), entry);
		}
		final NativeImage image = entry.texture.getImage();
		if (image != null) {
			for (int z = 0; z < MapRegion.SIZE; z++) {
				for (int x = 0; x < MapRegion.SIZE; x++) {
					image.setColor(x, z, abgr(region.color(x, z)));
				}
			}
			entry.texture.upload();
		}
		entry.version = region.version();
		return entry.id;
	}

	public void drop(long key) {
		final Entry entry = textures.remove(key);
		if (entry != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.id);
		}
	}

	public void clear() {
		for (final long key : textures.keySet().toArray(new Long[0])) {
			drop(key);
		}
	}

	/** NativeImage stores pixels as ABGR. */
	private static int abgr(int argb) {
		return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | (argb >> 16 & 0xFF);
	}
}
