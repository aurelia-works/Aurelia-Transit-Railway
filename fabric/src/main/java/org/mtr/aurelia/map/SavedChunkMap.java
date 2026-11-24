package org.mtr.aurelia.map;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.chunk.PalettedContainer;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Draws one {@link MapRegion} from the chunks a world has saved, the way the client's explored map draws loaded chunks:
 * the top block's map colour, water darkened with depth, slopes lit or shaded. It reads the {@code .mca} region file
 * itself, read-only, so it never loads a chunk into the world, never waits on the game's chunk storage and can run on
 * any thread. Only fully generated chunks are drawn. Chunks the game has not saved yet are not seen.
 */
public final class SavedChunkMap {

	private static final Codec<PalettedContainer<BlockState>> BLOCKS = PalettedContainer.createPalettedContainerCodec(
			Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState()
	);
	private static final int SECTOR = 4096;
	private static final int CHUNKS = MapRegion.SIZE / 16;
	/** How far down to look past glass and other blocks the map does not colour (as the client's scanner does). */
	private static final int CLEAR_DEPTH = 8;
	private static final int MAX_WATER_DEPTH = 16;
	private static final byte NOT_WATER = -1;

	private SavedChunkMap() {
	}

	/** The {@code .mca} file holding a map region's chunks. A file holds 32 chunks a side, so two map regions. */
	public static Path mcaFile(Path regionFolder, int regionX, int regionZ) {
		return regionFolder.resolve("r." + Math.floorDiv(regionX * CHUNKS, 32) + "." + Math.floorDiv(regionZ * CHUNKS, 32) + ".mca");
	}

	/**
	 * The map region drawn from the saved chunks, or null when none of its chunks is saved and fully generated.
	 *
	 * @param bottomY the world's lowest block
	 * @param height  the world's height in blocks
	 */
	@Nullable
	public static MapRegion render(Path regionFolder, int regionX, int regionZ, int bottomY, int height) throws IOException {
		final Path file = mcaFile(regionFolder, regionX, regionZ);
		if (!Files.isRegularFile(file)) {
			return null;
		}
		final int[] base = new int[MapRegion.SIZE * MapRegion.SIZE];
		final short[] heights = new short[MapRegion.SIZE * MapRegion.SIZE];
		final byte[] depths = new byte[MapRegion.SIZE * MapRegion.SIZE];
		Arrays.fill(heights, MapRegion.UNKNOWN_HEIGHT);
		final Column column = new Column(bottomY, height);
		boolean any = false;
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
			final ByteBuffer header = readFully(channel, 0, SECTOR);
			if (header == null) {
				return null;
			}
			for (int chunkZ = 0; chunkZ < CHUNKS; chunkZ++) {
				for (int chunkX = 0; chunkX < CHUNKS; chunkX++) {
					final int index = (regionX * CHUNKS + chunkX & 31) + (regionZ * CHUNKS + chunkZ & 31) * 32;
					final int entry = header.getInt(index * 4);
					final NbtCompound chunk = readChunk(channel, entry >>> 8, entry & 0xFF);
					if (chunk != null && column.load(chunk)) {
						any |= column.draw(chunkX * 16, chunkZ * 16, base, heights, depths);
					}
				}
			}
		}
		if (!any) {
			return null;
		}
		final int[] colors = new int[MapRegion.SIZE * MapRegion.SIZE];
		for (int z = 0; z < MapRegion.SIZE; z++) {
			for (int x = 0; x < MapRegion.SIZE; x++) {
				final int index = z * MapRegion.SIZE + x;
				if (heights[index] == MapRegion.UNKNOWN_HEIGHT) {
					continue;
				}
				final int brightness = depths[index] == NOT_WATER
						? MapShade.brightness(heights[index], z == 0 ? MapRegion.UNKNOWN_HEIGHT : heights[index - MapRegion.SIZE])
						: MapShade.waterBrightness(depths[index], regionX * MapRegion.SIZE + x, regionZ * MapRegion.SIZE + z);
				colors[index] = MapShade.shade(base[index], brightness);
			}
		}
		return new MapRegion(regionX, regionZ, colors, heights);
	}

	@Nullable
	private static NbtCompound readChunk(FileChannel channel, int sectorOffset, int sectorCount) throws IOException {
		if (sectorOffset < 2 || sectorCount == 0) {
			return null;
		}
		final ByteBuffer data = readFully(channel, (long) sectorOffset * SECTOR, sectorCount * SECTOR);
		if (data == null) {
			return null;
		}
		final int length = data.getInt();
		final int compression = data.get();
		// a chunk too big for its sectors lives in a separate .mcc file; the map leaves it out
		if (length <= 1 || length - 1 > data.remaining() || (compression & 0x80) != 0) {
			return null;
		}
		final InputStream raw = new ByteArrayInputStream(data.array(), 5, length - 1);
		final InputStream in = switch (compression) {
			case 1 -> new GZIPInputStream(raw);
			case 2 -> new InflaterInputStream(raw);
			case 3 -> raw;
			default -> null;
		};
		if (in == null) {
			return null;
		}
		try (DataInputStream stream = new DataInputStream(new BufferedInputStream(in))) {
			return NbtIo.read(stream);
		} catch (IOException | RuntimeException e) {
			// written while being read, or damaged: the map leaves the chunk out
			return null;
		}
	}

	@Nullable
	private static ByteBuffer readFully(FileChannel channel, long position, int length) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocate(length);
		while (buffer.hasRemaining()) {
			if (channel.read(buffer, position + buffer.position()) < 0) {
				return null;
			}
		}
		buffer.flip();
		return buffer;
	}

	/** One saved chunk's surface: its height map, and its sections decoded only when a column reaches them. */
	private static final class Column {

		private final int bottomY;
		private final int height;
		private final int minSection;
		private final int bits;
		private final NbtCompound[] sections;
		private final PalettedContainer<?>[] decoded;
		private final boolean[] tried;
		private long[] surface = new long[0];

		private Column(int bottomY, int height) {
			this.bottomY = bottomY;
			this.height = height;
			minSection = bottomY >> 4;
			bits = 32 - Integer.numberOfLeadingZeros(height);
			sections = new NbtCompound[height >> 4];
			decoded = new PalettedContainer<?>[height >> 4];
			tried = new boolean[height >> 4];
		}

		/** Takes a chunk; false when it is not fully generated or has no surface height map. */
		private boolean load(NbtCompound chunk) {
			final String status = chunk.getString("Status");
			if (!status.equals("full") && !status.equals("minecraft:full")) {
				return false;
			}
			surface = chunk.getCompound("Heightmaps").getLongArray("WORLD_SURFACE");
			if (surface.length == 0) {
				return false;
			}
			Arrays.fill(sections, null);
			Arrays.fill(decoded, null);
			Arrays.fill(tried, false);
			final NbtList list = chunk.getList("sections", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				final NbtCompound section = list.getCompound(i);
				final int index = section.getByte("Y") - minSection;
				if (index >= 0 && index < sections.length) {
					sections[index] = section;
				}
			}
			return true;
		}

		/** Draws the loaded chunk's 16 by 16 columns at {@code (offsetX, offsetZ)} in the region. Returns true when any column was drawn. */
		private boolean draw(int offsetX, int offsetZ, int[] base, short[] heights, byte[] depths) {
			boolean any = false;
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					final int stored = surfaceAt(x + z * 16);
					if (stored <= 0) {
						continue;
					}
					int y = bottomY + stored - 1;
					BlockState state = block(x, y, z);
					MapColor color = state.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
					for (int i = 0; color == MapColor.CLEAR && i < CLEAR_DEPTH && y > bottomY; i++) {
						y--;
						state = block(x, y, z);
						color = state.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
					}
					if (color == MapColor.CLEAR) {
						continue;
					}
					final int index = (offsetZ + z) * MapRegion.SIZE + offsetX + x;
					byte depth = NOT_WATER;
					if (!state.getFluidState().isEmpty()) {
						depth = 0;
						while (depth < MAX_WATER_DEPTH && y - depth - 1 > bottomY && !block(x, y - depth - 1, z).getFluidState().isEmpty()) {
							depth++;
						}
					}
					base[index] = color.color;
					heights[index] = (short) y;
					depths[index] = depth;
					any = true;
				}
			}
			return any;
		}

		/** A height map entry: the packed format since 1.16, a whole number of entries per long. */
		private int surfaceAt(int index) {
			final int perLong = 64 / bits;
			final int word = index / perLong;
			if (word >= surface.length) {
				return 0;
			}
			return (int) (surface[word] >>> (index % perLong) * bits & (1L << bits) - 1);
		}

		private BlockState block(int x, int y, int z) {
			final int index = (y >> 4) - minSection;
			if (index < 0 || index >= sections.length) {
				return Blocks.AIR.getDefaultState();
			}
			if (!tried[index]) {
				tried[index] = true;
				final NbtCompound section = sections[index];
				if (section != null && section.contains("block_states", NbtElement.COMPOUND_TYPE)) {
					decoded[index] = BLOCKS.parse(NbtOps.INSTANCE, section.getCompound("block_states")).result().orElse(null);
				}
			}
			final PalettedContainer<?> container = decoded[index];
			return container == null ? Blocks.AIR.getDefaultState() : (BlockState) container.get(x, y & 15, z);
		}
	}
}
