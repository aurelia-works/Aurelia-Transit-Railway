package com.aureliatransit.architecture.block.entity;

import com.aureliatransit.architecture.airport.AirportDepartures;
import com.aureliatransit.architecture.block.DeparturesBoardBlock;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * The rows the departures renderer draws. Refreshed every second on the client from the live station snapshot.
 * Not saved.
 */
public class DeparturesBoardBlockEntity extends BlockEntity {

	static final int ROWS = 5;
	private static final int REFRESH_TICKS = 20;

	private int cooldown;
	private List<String> lines = List.of();

	public DeparturesBoardBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DEPARTURES_BOARD, pos, state);
	}

	public List<String> lines() {
		return lines;
	}

	public void clientTick() {
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		cooldown = REFRESH_TICKS;
		refresh();
	}

	/** Reads the snapshot once. Tests call this to skip the cooldown. */
	public void refresh() {
		lines = AirportDepartures.lines(DeparturesBoardBlock.resolve(getPos()), System.currentTimeMillis(), ROWS);
	}
}
