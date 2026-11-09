package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * The old airport wrench id, kept so old worlds load: it is the one ATR wrench with Airport as its own mode. It works the same
 * way (right-click the air for its screen, right-click a block to plan an airport there, sneak + right-click to build, left-click
 * to cancel; operators only, checked on the server by {@link StationActions#allowed}) and goes through the same parser, planner,
 * survey and build, but whatever its words say, what it plans is an airport
 * ({@link StationDescriptionParser#airportWords}).
 */
public class AirportWrenchItem extends StationWrenchItem {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".airport_wrench";

	/** Client only: opens the airport wrench screen for the stack in {@code hand}. Set by the client initializer. */
	public static Consumer<Hand> openAirportScreen = hand -> {
	};

	public AirportWrenchItem(Settings settings) {
		super(settings);
	}

	@Override
	protected WrenchMode defaultMode() {
		return WrenchMode.AIRPORT;
	}

	@Override
	protected String wordsToPlan(String description) {
		return StationDescriptionParser.airportWords(description);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		final String description = description(stack);
		if (!description.isEmpty()) {
			tooltip.add(Text.literal("\"" + description + "\"").formatted(Formatting.AQUA));
		}
		tooltip.add(Text.translatable(TIP + ".edit").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(TIP + ".plan").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(TIP + ".build").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(TIP + ".cancel").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(TIP + ".ops").formatted(Formatting.DARK_GRAY));
	}
}
