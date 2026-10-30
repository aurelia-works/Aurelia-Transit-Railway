package com.aureliatransit.architecture.block.entity;

import com.aureliatransit.architecture.airport.AircraftSample;
import com.aureliatransit.architecture.airport.JetBridgeMotion;
import com.aureliatransit.architecture.block.JetBridgeBlock;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Holds how far the tunnel has travelled, from 0 (retracted) to 1 (at the door). Not saved: the next client tick
 * derives it again from the airplanes the client can see.
 */
public class JetBridgeBlockEntity extends BlockEntity {

	private float extension;

	public JetBridgeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.JET_BRIDGE, pos, state);
	}

	public float extension() {
		return extension;
	}

	public void clientTick() {
		drive(JetBridgeBlock.samples.get());
	}

	/** One step toward the airplanes in {@code samples}. Game tests call this without a client. */
	public void drive(List<AircraftSample> samples) {
		final BlockPos at = getPos();
		extension = JetBridgeMotion.step(extension, JetBridgeMotion.wants(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, samples));
	}
}
