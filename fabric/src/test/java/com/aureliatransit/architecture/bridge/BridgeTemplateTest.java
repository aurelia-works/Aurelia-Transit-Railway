package com.aureliatransit.architecture.bridge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BridgeTemplateTest {

	private static final BridgeTemplate TEMPLATE = BridgeTemplate.of(4, 2, 5, List.of("minecraft:stone", "minecraft:oak_stairs[facing=north]"),
			List.of(new BridgeTemplate.Cell(2, 0, 0, 0), new BridgeTemplate.Cell(0, 1, 3, 1)));

	@Test
	public void rotatesAndLiftsTheSection() {
		final List<BridgeTemplate.Placed> north = TEMPLATE.stamp(10, 64, 20, 0, 0);
		Assertions.assertEquals(new BridgeTemplate.Placed(12, 64, 20, 0), north.get(0), "right of north is east");
		Assertions.assertEquals(new BridgeTemplate.Placed(10, 65, 17, 1), north.get(1), "forward of north is north");

		final List<BridgeTemplate.Placed> east = TEMPLATE.stamp(10, 64, 20, 1, 2);
		Assertions.assertEquals(new BridgeTemplate.Placed(10, 66, 22, 0), east.get(0), "right of east is south");
		Assertions.assertEquals(new BridgeTemplate.Placed(13, 67, 20, 1), east.get(1), "forward of east is east");
	}

	@Test
	public void captureFrameIsTheInverseOfRotation() {
		for (int quarterTurns = 0; quarterTurns < 4; quarterTurns++) {
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					final int[] world = BridgeTemplate.rotate(x, z, quarterTurns);
					Assertions.assertArrayEquals(new int[]{x, z}, BridgeTemplate.toLocal(world[0], world[1], quarterTurns));
				}
			}
		}
	}

	@Test
	public void jsonRoundTrips() {
		final BridgeTemplate copy = BridgeTemplate.fromJson(TEMPLATE.toJson().toString());
		Assertions.assertEquals(TEMPLATE.sizeX(), copy.sizeX());
		Assertions.assertEquals(TEMPLATE.sizeY(), copy.sizeY());
		Assertions.assertEquals(TEMPLATE.sizeZ(), copy.sizeZ());
		Assertions.assertEquals(TEMPLATE.palette(), copy.palette());
		Assertions.assertEquals(TEMPLATE.cells(), copy.cells());
	}
}
