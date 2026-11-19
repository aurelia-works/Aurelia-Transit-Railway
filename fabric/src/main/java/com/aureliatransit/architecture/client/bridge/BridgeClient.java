package com.aureliatransit.architecture.client.bridge;

import com.aureliatransit.architecture.registry.ModBlockEntities;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

/** Client half of the rail bridge builder: drawing its stays, cables, diagonals and overhead line. */
public final class BridgeClient {

	private BridgeClient() {
	}

	public static void init() {
		BlockEntityRendererFactories.register(ModBlockEntities.BRIDGE_MEMBER, BridgeMemberRenderer::new);
	}
}
