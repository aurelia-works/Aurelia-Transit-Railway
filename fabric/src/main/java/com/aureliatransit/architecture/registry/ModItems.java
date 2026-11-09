package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.fare.TransitCardItem;
import com.aureliatransit.architecture.timetable.PocketTimetableItem;
import com.aureliatransit.architecture.station.world.StationCodexItem;
import com.aureliatransit.architecture.station.world.AirportWrenchItem;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Items that are not blocks. Listed in the passenger equipment tab after its blocks.
 */
public final class ModItems {

	public static final Item TRANSIT_CARD = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("transit_card"),
			new TransitCardItem(new Item.Settings().maxCount(1)));
	public static final Item POCKET_TIMETABLE = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("pocket_timetable"),
			new PocketTimetableItem(new Item.Settings().maxCount(1)));
	/** The one ATR wrench: Station, Airport, Bridge and Earthworks modes; operators only. */
	public static final Item WRENCH = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("wrench"),
			new StationWrenchItem(new Item.Settings().maxCount(1)));
	/** Old station wrench id: kept registered so old worlds load; it is the same wrench, opening in Station mode. */
	public static final Item STATION_WRENCH = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("station_wrench"),
			new StationWrenchItem(new Item.Settings().maxCount(1)));
	/** Old airport wrench id: kept registered so old worlds load; it is the same wrench, opening in Airport mode. */
	public static final Item AIRPORT_WRENCH = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("airport_wrench"),
			new AirportWrenchItem(new Item.Settings().maxCount(1)));
	/** The station generator's guide; opens the Codex screen. */
	public static final Item STATION_CODEX = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("station_codex"),
			new StationCodexItem(new Item.Settings().maxCount(1)));

	private ModItems() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
