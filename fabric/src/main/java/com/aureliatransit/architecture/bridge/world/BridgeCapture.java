package com.aureliatransit.architecture.bridge.world;

import com.aureliatransit.architecture.bridge.BridgeTemplate;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.StylePalette;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a bridge section out of the world into a {@link BridgeTemplate}. The box is inclusive, {@code origin} is the
 * rail block the section belongs to, and {@code facing} is the direction the captured section faces. Air, fluids, MTR
 * rail nodes and block entities are left out: the first two are not bridge material, the rail node already exists along
 * the rail being stamped, and a block entity would need its own data rather than a block state.
 */
public final class BridgeCapture {

	/** A bridge section larger than this is a mistake or a whole station, not a template. */
	public static final int MAX_VOLUME = 32768;

	public record Result(BridgeTemplate template, int skippedRailNodes, int skippedBlockEntities, int skippedFluids) {
	}

	private BridgeCapture() {
	}

	public static Result capture(ServerWorld world, BlockPos from, BlockPos to, BlockPos origin, Heading facing) {
		final int minX = Math.min(from.getX(), to.getX());
		final int minY = Math.min(from.getY(), to.getY());
		final int minZ = Math.min(from.getZ(), to.getZ());
		final int maxX = Math.max(from.getX(), to.getX());
		final int maxY = Math.max(from.getY(), to.getY());
		final int maxZ = Math.max(from.getZ(), to.getZ());
		final int sizeX = maxX - minX + 1;
		final int sizeY = maxY - minY + 1;
		final int sizeZ = maxZ - minZ + 1;
		if (sizeX > BridgeTemplate.MAX_EXTENT || sizeY > BridgeTemplate.MAX_EXTENT || sizeZ > BridgeTemplate.MAX_EXTENT) {
			throw new IllegalArgumentException("A template may be at most " + BridgeTemplate.MAX_EXTENT + " blocks on each axis; this box is " + sizeX + "x" + sizeY + "x" + sizeZ + ".");
		}
		final long volume = (long) sizeX * sizeY * sizeZ;
		if (volume > MAX_VOLUME) {
			throw new IllegalArgumentException("A template may cover at most " + MAX_VOLUME + " blocks; this box has " + volume + ".");
		}
		if (origin.getX() < minX || origin.getX() > maxX || origin.getY() < minY || origin.getY() > maxY || origin.getZ() < minZ || origin.getZ() > maxZ) {
			throw new IllegalArgumentException("The origin must be inside the captured box.");
		}

		final Map<String, Integer> paletteIndex = new LinkedHashMap<>();
		final List<String> palette = new ArrayList<>();
		final List<BridgeTemplate.Cell> cells = new ArrayList<>();
		int skippedRailNodes = 0;
		int skippedBlockEntities = 0;
		int skippedFluids = 0;
		for (final BlockPos pos : BlockPos.iterate(minX, minY, minZ, maxX, maxY, maxZ)) {
			final BlockState state = world.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (state.getBlock() instanceof FluidBlock) {
				skippedFluids++;
				continue;
			}
			if (Registries.BLOCK.getId(state.getBlock()).toString().equals(StylePalette.MTR_RAIL_NODE)) {
				skippedRailNodes++;
				continue;
			}
			if (state.hasBlockEntity()) {
				skippedBlockEntities++;
				continue;
			}
			// Store every state facing north, so stamping can rotate the whole palette by the rail's quarter turn.
			final String encoded = BridgeStateCodec.encode(BridgeStateCodec.rotate(state, 4 - facing.ordinal()));
			final Integer index = paletteIndex.get(encoded);
			final int paletteEntry;
			if (index == null) {
				paletteEntry = palette.size();
				palette.add(encoded);
				paletteIndex.put(encoded, paletteEntry);
			} else {
				paletteEntry = index;
			}
			final int[] local = BridgeTemplate.toLocal(pos.getX() - origin.getX(), pos.getZ() - origin.getZ(), facing.ordinal());
			cells.add(new BridgeTemplate.Cell(local[0], pos.getY() - origin.getY(), local[1], paletteEntry));
		}
		if (cells.isEmpty()) {
			throw new IllegalArgumentException("The captured box has no blocks that a template can store.");
		}
		return new Result(BridgeTemplate.of(sizeX, sizeY, sizeZ, palette, cells), skippedRailNodes, skippedBlockEntities, skippedFluids);
	}
}
