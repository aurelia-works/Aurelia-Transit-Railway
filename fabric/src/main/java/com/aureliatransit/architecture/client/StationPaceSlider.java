package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.station.world.StationBuildSettings;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * The station build pace slider, on the Aurelia panel's Architecture tab and the wrench screen: the steps in
 * {@link StationBuildSettings#STEPS}, showing the value the server last sent. Dragging asks the server for the new pace; it
 * clamps and echoes it, so every operator's screen agrees.
 */
public final class StationPaceSlider extends SliderWidget {

	private static final String KEY = "screen.aurelia_transit_architecture.station_wrench.";
	private int sent = StationPaceClient.blocksPerTick();

	public StationPaceSlider(int x, int y, int width) {
		super(x, y, width, 20, Text.empty(), position(StationPaceClient.blocksPerTick()));
		updateMessage();
	}

	private static double position(int blocks) {
		final int[] steps = StationBuildSettings.STEPS;
		final int nearest = StationBuildSettings.nearestStep(blocks);
		for (int i = 0; i < steps.length; i++) {
			if (steps[i] == nearest) {
				return steps.length == 1 ? 0 : i / (double) (steps.length - 1);
			}
		}
		return 0;
	}

	private int blocks() {
		final int[] steps = StationBuildSettings.STEPS;
		final int step = Math.max(0, Math.min(steps.length - 1, (int) Math.round(value * (steps.length - 1))));
		return steps[step];
	}

	@Override
	protected void updateMessage() {
		setMessage(Text.translatable(KEY + "pace", blocks()));
	}

	@Override
	protected void applyValue() {
		final int blocks = blocks();
		if (blocks != sent) {
			sent = blocks;
			StationPaceClient.set(blocks);
		}
	}
}
