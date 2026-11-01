package com.aureliatransit.architecture.bridge.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The palette format saved in every bridge template must survive a server restart. A saved shape with two properties is
 * enough to prove that block ids, property names and property values all round-trip.
 */
public class BridgeStateCodecTest {

	@BeforeAll
	public static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	@Test
	public void roundTripsBlockStateProperties() {
		final BlockState state = Blocks.OAK_STAIRS.getDefaultState()
				.with(Properties.HORIZONTAL_FACING, Direction.EAST)
				.with(StairsBlock.HALF, net.minecraft.block.enums.BlockHalf.TOP);
		final String encoded = BridgeStateCodec.encode(state);
		Assertions.assertTrue(encoded.startsWith("minecraft:oak_stairs["), encoded);
		Assertions.assertTrue(encoded.contains("facing=east"), encoded);
		Assertions.assertTrue(encoded.contains("half=top"), encoded);
		Assertions.assertEquals(state, BridgeStateCodec.decode(encoded));
	}

	@Test
	public void rotatesStatesWithTheSection() {
		final BlockState state = Blocks.OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST);
		final BlockState south = BridgeStateCodec.rotate(state, 1);
		Assertions.assertEquals(Direction.SOUTH, south.get(Properties.HORIZONTAL_FACING));
	}

	@Test
	public void anUninstalledBlockDecodesToNull() {
		Assertions.assertNull(BridgeStateCodec.decode("not_a_mod:missing_bridge_block[facing=north]"));
	}
}
