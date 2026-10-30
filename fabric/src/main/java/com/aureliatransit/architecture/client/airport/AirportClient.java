package com.aureliatransit.architecture.client.airport;

import com.aureliatransit.architecture.block.DeparturesBoardBlock;
import com.aureliatransit.architecture.block.JetBridgeBlock;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationData;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

/**
 * Client half of the jet bridge and the departures board. Both read data the client already has: vehicles for the
 * bridge, the live station snapshot for the board.
 */
public final class AirportClient {

	private AirportClient() {
	}

	public static void init() {
		JetBridgeBlock.samples = JetBridgeScanner::samples;
		DeparturesBoardBlock.snapshot = (pos, withServices) -> StationData.provider().resolve(pos, StationAssociation.AUTO, withServices);
		BlockEntityRendererFactories.register(ModBlockEntities.JET_BRIDGE, JetBridgeRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.DEPARTURES_BOARD, DeparturesBoardRenderer::new);
	}
}
