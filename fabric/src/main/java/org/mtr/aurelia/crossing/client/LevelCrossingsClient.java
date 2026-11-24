package org.mtr.aurelia.crossing.client;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import org.mtr.aurelia.crossing.LevelCrossings;

/** The client side of level crossings: the barrier's arm. */
public final class LevelCrossingsClient {

	private LevelCrossingsClient() {
	}

	public static void register() {
		BlockEntityRendererFactories.register(LevelCrossings.BARRIER_ENTITY, LevelCrossingBarrierRenderer::new);
	}
}
