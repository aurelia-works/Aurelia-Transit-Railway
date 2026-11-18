package com.aureliatransit.architecture.station;

import static com.aureliatransit.architecture.station.Landmark.BLOCK;
import static com.aureliatransit.architecture.station.Landmark.END;
import static com.aureliatransit.architecture.station.Landmark.SPAN;

import com.aureliatransit.architecture.station.Landmark.Cap;
import com.aureliatransit.architecture.station.Landmark.Facade;
import com.aureliatransit.architecture.station.Landmark.Roof;
import com.aureliatransit.architecture.station.Landmark.Shed;
import com.aureliatransit.architecture.station.Landmark.ShedProfile;
import com.aureliatransit.architecture.station.Landmark.Skin;
import com.aureliatransit.architecture.station.Landmark.Window;

import java.util.List;

/**
 * The sheets of the real stations the presets copy (Jah, 10-21), each from photos and published sizes (the reference
 * briefs list the sources). A station gets its sheet when it is named after it, as every preset is. One block is about
 * two thirds of a metre; heights are squeezed where the real building would pass the station's height limit.
 */
public final class Landmarks {

	static final String CLOCK_FACE = "aurelia_transit_architecture:amsterdam_clock_face_panel";

	// ---- materials ----

	/** Victorian red brick with pale stone dressings and a grey slate roof (St Pancras). */
	static final Skin GOTHIC_RED_BRICK = new Skin("minecraft:bricks", "minecraft:smooth_sandstone", "minecraft:cut_sandstone",
			"minecraft:gray_stained_glass_pane", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs",
			"minecraft:smooth_sandstone_stairs", "minecraft:smooth_sandstone");

	/** Antwerp's ornate limestone with travertine dressings, rusticated plinth, gilded ornament and slate roofs. */
	static final Skin ANTWERP_STONE = new Skin("aurelia_transit_architecture:antwerp_ornate_limestone",
			"aurelia_transit_architecture:roma_travertine", "aurelia_transit_architecture:frankfurt_sandstone_rusticated",
			"aurelia_transit_architecture:clear_float_glass_pane", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs",
			"minecraft:smooth_sandstone_stairs", "aurelia_transit_architecture:antwerp_gilded_ornament_trim");

	/** Beaux-Arts limestone over a grey granite base, bronze-framed thermal windows, under green copper (Grand Central). */
	static final Skin BEAUX_ARTS_STONE = new Skin("aurelia_transit_architecture:roma_travertine", "minecraft:smooth_sandstone",
			"minecraft:polished_andesite", "aurelia_transit_architecture:bronze_tinted_glass_pane", "minecraft:oxidized_cut_copper",
			"minecraft:oxidized_cut_copper_stairs", "minecraft:smooth_sandstone_stairs", null);

	/** Frankfurt's ochre sandstone, rusticated at the base, under blue-grey slate, with warm glass. */
	static final Skin FRANKFURT_SANDSTONE = new Skin("aurelia_transit_architecture:frankfurt_sandstone_ashlar", "minecraft:smooth_sandstone",
			"aurelia_transit_architecture:frankfurt_sandstone_rusticated", "aurelia_transit_architecture:bronze_tinted_glass_pane",
			"minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs", "minecraft:smooth_sandstone_stairs", null);

	/** Pale grey-beige sandstone over grey stone, under dark slate (Zurich). */
	static final Skin ZURICH_SANDSTONE = new Skin("aurelia_transit_architecture:roma_travertine", "minecraft:smooth_stone",
			"minecraft:stone_bricks", "aurelia_transit_architecture:clear_float_glass_pane", "minecraft:deepslate_tiles",
			"minecraft:deepslate_tile_stairs", "minecraft:stone_brick_stairs", null);

	/** Tokyo's red brick with white granite bands over a grey granite base, under dark Miyagi slate. */
	static final Skin TOKYO_BRICK = new Skin("minecraft:bricks", "minecraft:smooth_quartz", "minecraft:polished_andesite",
			"aurelia_transit_architecture:clear_float_glass_pane", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs",
			"minecraft:smooth_quartz_stairs", "minecraft:smooth_quartz");

	/** Beijing's warm-grey stone under blue-green glazed Chinese pavilion tiles. */
	static final Skin BEIJING_STONE = new Skin("minecraft:smooth_stone", "minecraft:polished_diorite", "minecraft:polished_andesite",
			"aurelia_transit_architecture:grey_tinted_glass_pane", "minecraft:prismarine_bricks", "minecraft:prismarine_brick_stairs",
			"minecraft:polished_diorite_stairs", null);

	/** The Moorish white stucco of Kuala Lumpur's station over a sandstone plinth. */
	static final Skin KL_WHITE = new Skin("minecraft:calcite", "minecraft:smooth_quartz", "minecraft:smooth_sandstone",
			"aurelia_transit_architecture:clear_float_glass_pane", "minecraft:quartz_block", "minecraft:quartz_stairs",
			"minecraft:smooth_quartz_stairs", "minecraft:smooth_quartz");

	/** Surbiton's crisp white-rendered concrete, quartz horizontal fins, blackstone plinth and flat membrane roofs. */
	static final Skin SURBITON_WHITE = new Skin("aurelia_transit_architecture:uk_white_render", "minecraft:smooth_quartz",
			"minecraft:polished_blackstone", "aurelia_transit_architecture:curtain_wall_glass", "minecraft:gray_concrete",
			"minecraft:smooth_quartz_stairs", "minecraft:smooth_quartz_stairs", "minecraft:smooth_quartz");

	/** Cream Paris limestone under blue-grey slate mansards. */
	static final Skin PARIS_LIMESTONE = new Skin("aurelia_transit_architecture:roma_travertine", "minecraft:smooth_quartz",
			"minecraft:smooth_stone", "aurelia_transit_architecture:clear_float_glass_pane", "minecraft:deepslate_tiles",
			"minecraft:deepslate_tile_stairs", "minecraft:smooth_quartz_stairs", null);

	/** Milan's pale Botticino stone, a grey stone base and flat stone roofs. */
	static final Skin MILAN_STONE = new Skin("minecraft:calcite", "minecraft:polished_diorite", "minecraft:smooth_stone",
			"aurelia_transit_architecture:clear_float_glass_pane", "minecraft:smooth_stone", "minecraft:stone_stairs",
			"minecraft:polished_diorite_stairs", null);

	/** Mumbai's honey Kurla sandstone with pale limestone dressings, basalt plinth, red sandstone bands and terracotta tile roofs. */
	static final Skin MUMBAI_SANDSTONE = new Skin("aurelia_transit_architecture:frankfurt_sandstone_ashlar",
			"aurelia_transit_architecture:antwerp_ornate_limestone", "minecraft:polished_basalt",
			"aurelia_transit_architecture:clear_float_glass_pane", "minecraft:bricks", "minecraft:brick_stairs",
			"minecraft:sandstone_stairs", "minecraft:smooth_red_sandstone");

	/** Bangkok's cream Neo-Renaissance stucco with white classical trim, ochre bands, clay tiles and stained glass. */
	static final Skin BANGKOK_STUCCO = new Skin("minecraft:smooth_sandstone", "minecraft:smooth_quartz", "minecraft:stone_bricks",
			"minecraft:cyan_stained_glass_pane", "minecraft:bricks", "minecraft:brick_stairs", "minecraft:smooth_quartz_stairs",
			"minecraft:yellow_terracotta");

	/** Amsterdam Centraal's dark red brick banded with pale sandstone, over a sandstone plinth, under dark slate. */
	static final Skin AMSTERDAM_BRICK = new Skin("aurelia_transit_architecture:amsterdam_red_brick", "minecraft:smooth_sandstone",
			"minecraft:cut_sandstone", "aurelia_transit_architecture:amsterdam_arched_window", "minecraft:deepslate_tiles",
			"minecraft:deepslate_tile_stairs", "minecraft:smooth_sandstone_stairs",
			"aurelia_transit_architecture:amsterdam_brick_sandstone_band");

	/** A great arched window's look: sky-reflecting glass in dark iron glazing bars, so it reads against pale stone. */
	static Skin glazing(Skin s) {
		return s.withGlass("minecraft:light_blue_stained_glass_pane").withTrim("minecraft:polished_deepslate");
	}

	private Landmarks() {
	}

	/**
	 * London St Pancras (George Gilbert Scott's Midland Grand Hotel, 1868-76, and W. H. Barlow's train shed, 1868): the hotel
	 * across the head of the tracks in red Gretton brick with Ancaster stone bands, pointed Gothic windows in five storeys,
	 * steep slate roofs with dormers, a clock tower with a spire at the east end (on the right from Euston Road) and a lower
	 * spired tower at the west end, a gabled centre bay over pointed arches; behind it the single pointed span of the shed
	 * (74.8 m wide, 30.5 m high, springing from the platforms), its ribs pale sky blue, its roof slate below a glazed band.
	 */
	static final Landmark ST_PANCRAS = Landmark.head("st_pancras", "St Pancras", "St Pancras International", "London St Pancras")
			.shed(new Shed(ShedProfile.POINTED, 1, 0.42, StationGeometry.CANOPY_Y, "aurelia_transit_architecture:glass_wall",
					"minecraft:deepslate_tiles", 0.35, 1.0, "blue", "minecraft:bricks"))
			.skin(GOTHIC_RED_BRICK)
			.facade(new Facade(Window.POINTED, 5, 3, 6, 2, 3, 2, 5, true))
			// the Elizabeth clock tower at the east corner and the west spire tower, framing the gabled Gothic centre
			.tower(END - 5, 2, 4, 1, 64, Cap.SPIRE, 26, true)
			.tower(-END + 4, 3, 3, 1, 50, Cap.SPIRE, 18, false)
			.gable(0, 0, 8, 35, 11, false)
			.clock(0, 40, 0, 2)
			.arch(-10, 0, 2, 1, 7, true)
			.arch(0, 0, 3, 5, 10, true)
			.arch(10, 0, 2, 1, 7, true)
			.pinnacles(-18, 18, 0, 35, 9, "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone")
			// the hotel across the whole head: five storeys of polychrome brick under a steep slate roof with dormers
			.volume(-END, END, 0, 15, 34, Roof.GABLE_X, 2, true, true)
			.chimneys(-25, 25, 5, 46, 10)
			.chimneys(-30, 30, 10, 46, 12)
			// the booking hall behind it, to the shed's screen
			.facade(new Facade(Window.POINTED, 5, 3, 6, 2, 3, 2, 5, false))
			.hall(-END, END, 15, 60, 12)
			.build();

	/**
	 * Antwerpen-Centraal (Louis Delacenserie, 1895-1905): an ornate limestone head building with a great dome (about 60 m) over the
	 * hall, a tall glazed bay under it, an arcade of round arches along the street, two turreted towers with verdigris domes and
	 * lanterns and mansard-roofed wings; behind it Clement van Bogaert's arched iron and glass shed, 185 m long and 44 m high, its
	 * ribs springing from the platforms.
	 */
	static final Landmark ANTWERP = Landmark.head("antwerp", "Antwerpen Centraal", "Antwerp Central", "Antwerpen-Centraal")
			.shed(new Shed(ShedProfile.ROUND, 1, 0.48, StationGeometry.CANOPY_Y, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.15, 1.0, "green",
					"aurelia_transit_architecture:antwerp_ornate_limestone"))
			.skin(ANTWERP_STONE.withRoof("minecraft:oxidized_cut_copper", "minecraft:oxidized_cut_copper_stairs"))
			.facade(new Facade(Window.ROUND, 4, 2, 6, 2, 3, 5, 4, true))
			// the flanking towers with their copper-slate domes, between them the glazed bay over an arcade and the gilded clock
			.tower(-17, 3, 4, 1, 42, Cap.DOME, 0, false)
			.tower(17, 3, 4, 1, 42, Cap.DOME, 0, false)
			.skin(ANTWERP_STONE.withTrim("minecraft:gold_block"))
			.clock(0, 31, -1, 2)
			.skin(glazing(ANTWERP_STONE))
			.arch(0, 0, 9, 9, 25, false)
			.skin(ANTWERP_STONE)
			.arcade(-10, 10, 0, 5, 2, 1, 6)
			.volume(-13, 13, 0, 22, 33, Roof.FLAT, 1, true, false)
			// the great dome over the hall, verdigris and dark slate with a gilded lantern
			.skin(ANTWERP_STONE.withRoof("minecraft:oxidized_cut_copper", "minecraft:oxidized_cut_copper_stairs")
					.withTrim("minecraft:gold_block"))
			.dome(0, 11, 11, 34, 4, false, true)
			// the mansard wings and raised corner pavilions across the full head, and the hall behind them
			.skin(ANTWERP_STONE)
			.volume(-END + 9, -22, 0, 16, 26, Roof.MANSARD, 3, true, true)
			.volume(22, END - 9, 0, 16, 26, Roof.MANSARD, 3, true, true)
			.volume(-END, -END + 8, 0, 16, 30, Roof.MANSARD, 3, true, true)
			.volume(END - 8, END, 0, 16, 30, Roof.MANSARD, 3, true, true)
			.hall(-END, END, 17, 60, 14)
			.build();

	/**
	 * Grand Central Terminal, New York (Reed and Stem, Warren and Wetmore, 1913): the 42nd Street front of pale limestone on a
	 * granite base, three great arched windows between paired columns, an attic with the Tiffany clock and the Glory of Commerce
	 * statues, a flat roof of green copper; inside, the Main Concourse under its teal sky with gold stars. Its tracks run
	 * under the city; here they stay at street level under the station's hall.
	 */
	static final Landmark GRAND_CENTRAL = Landmark.head("grand_central", "Grand Central", "Grand Central Terminal")
			.skin(BEAUX_ARTS_STONE.withTrim("minecraft:polished_deepslate"))
			.arch(-16, 0, 6, 6, 21, false)
			.arch(0, 0, 6, 6, 21, false)
			.arch(16, 0, 6, 6, 21, false)
			.skin(BEAUX_ARTS_STONE)
			// the plain stone front: a wide bay leaves it bare between the windows and columns
			.facade(new Facade(Window.NONE, 64, 1, 10, 3, 5, 5, 0, false))
			.colonnade(-25, -24, -1, 28, 1, "minecraft:smooth_quartz")
			.colonnade(-9, -8, -1, 28, 1, "minecraft:smooth_quartz")
			.colonnade(8, 9, -1, 28, 1, "minecraft:smooth_quartz")
			.colonnade(24, 25, -1, 28, 1, "minecraft:smooth_quartz")
			// the Tiffany clock and the Glory of Commerce sculptural group over the cornice
			.skin(BEAUX_ARTS_STONE.withTrim("minecraft:gold_block"))
			.clock(0, 35, -1, 3)
			.skin(BEAUX_ARTS_STONE)
			.gable(0, 0, 9, 31, 9, false)
			.pinnacles(-6, 6, 0, 34, 6, "minecraft:smooth_quartz", "minecraft:gold_block")
			.ceiling(-29, 29, 1, 59, 29, "minecraft:cyan_terracotta", "minecraft:ochre_froglight", 5)
			.volume(-30, 30, 0, 60, 30, Roof.FLAT, 1, true, false)
			// Beaux-Arts side bays where wider termini extend past the 61-cell main hall
			.facade(new Facade(Window.ROUND, 6, 2, 8, 2, 4, 5, 0, true))
			.volume(-END, -31, 0, 60, 26, Roof.FLAT, 1, true, false)
			.volume(31, END, 0, 60, 26, Roof.FLAT, 1, true, false)
			.build();

	/**
	 * Frankfurt (Main) Hauptbahnhof (Hermann Eggert, 1888, outer wings 1924): a Neo-Renaissance front of ochre sandstone, its middle
	 * a giant arched window under the Atlas group and the clock, flanked by slate-roofed wings, domed pavilions and 1924 outer wings;
	 * behind it three barrel-vaulted halls side by side, grey-green iron with glazed tops.
	 */
	static final Landmark FRANKFURT = Landmark.head("frankfurt", "Frankfurt Hbf", "Frankfurt Hauptbahnhof", "Frankfurt (Main) Hbf")
			.shed(new Shed(ShedProfile.ROUND, 3, 0.4, 0, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.3, 1.0, "green", "aurelia_transit_architecture:frankfurt_sandstone_ashlar"))
			.skin(FRANKFURT_SANDSTONE)
			.facade(new Facade(Window.ROUND, 5, 2, 6, 2, 3, 4, 0, true))
			// the Neo-Renaissance domed pavilions
			.tower(-34, 5, 5, 1, 32, Cap.DOME, 0, false)
			.tower(34, 5, 5, 1, 32, Cap.DOME, 0, false)
			// the giant arched window, the clock and the bronze Atlas group over it
			.clock(0, 35, -1, 2)
			.skin(glazing(FRANKFURT_SANDSTONE))
			.arch(0, 0, 11, 5, 21, false)
			.skin(FRANKFURT_SANDSTONE)
			.arch(-6, 0, 2, 1, 5, false)
			.arch(6, 0, 2, 1, 5, false)
			.gable(0, 0, 7, 33, 8, false)
			.pinnacles(-3, 3, 0, 39, 3, "minecraft:oxidized_cut_copper", "minecraft:gold_block")
			.volume(-16, 16, 0, 16, 32, Roof.FLAT, 1, true, false)
			// the 1888 slate wings with dormers, the 1924 outer wings to the head's ends, and the hall behind
			.volume(-28, -17, 0, 14, 24, Roof.GABLE_X, 1, true, true)
			.volume(17, 28, 0, 14, 24, Roof.GABLE_X, 1, true, true)
			.volume(-END, -40, 0, 14, 22, Roof.FLAT, 1, true, false)
			.volume(40, END, 0, 14, 22, Roof.FLAT, 1, true, false)
			.hall(-END, END, 15, 60, 12)
			.build();

	/**
	 * Zurich Hauptbahnhof (Jakob Friedrich Wanner, 1871): a Neo-Renaissance front of pale sandstone round a triumphal-arch
	 * portal with a clock, long wings with half-round windows under slate mansards ending in corner pavilions, the tall Haupthalle
	 * behind it with its arched windows; the platforms under barrel halls side by side.
	 */
	static final Landmark ZURICH = Landmark.head("zurich", "Zurich HB", "Zurich Hauptbahnhof", "Zuerich HB")
			.shed(new Shed(ShedProfile.ROUND, 3, 0.3, 0, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.25, 1.0, "steel", "minecraft:stone_bricks"))
			.skin(ZURICH_SANDSTONE)
			.facade(new Facade(Window.ROUND, 4, 2, 6, 2, 3, 3, 0, true))
			// the triumphal arch: a great middle arch between two smaller ones, the clock and an attic over them
			.clock(0, 21, -1, 2)
			.arch(0, 0, 4, 1, 14, false)
			.arch(-7, 0, 2, 1, 8, false)
			.arch(7, 0, 2, 1, 8, false)
			.colonnade(-10, 10, -1, 18, 5, "minecraft:smooth_stone")
			.volume(-10, 10, 0, 14, 26, Roof.FLAT, 1, true, false)
			.upper(-6, 6, 0, 5, 27, 31, Roof.FLAT, 1)
			// the mansard wings flush with the street wall, ending in corner pavilions, and the Haupthalle behind them
			.volume(-END + 10, -11, 0, 16, 20, Roof.MANSARD, 3, true, true)
			.volume(11, END - 10, 0, 16, 20, Roof.MANSARD, 3, true, true)
			.volume(-END, -END + 9, 0, 16, 24, Roof.MANSARD, 3, true, true)
			.volume(END - 9, END, 0, 16, 24, Roof.MANSARD, 3, true, true)
			.facade(new Facade(Window.ROUND, 5, 2, 24, 4, 14, 3, 0, true))
			.hall(-END, END, 17, 60, 26, Roof.FLAT, 1)
			.build();

	/**
	 * Paris Gare du Nord (Jacques Ignace Hittorff, 1861-65): a cream limestone front, its middle pavilion one giant arched
	 * window under a pediment and clock, statues of the cities served along the cornice, corner pavilions and wings under slate
	 * mansards; behind it the three-nave shed on tall dark green cast-iron columns, its roof timber brown with a glazed nave.
	 */
	static final Landmark PARIS = Landmark.head("paris", "Paris Nord", "Gare du Nord", "Paris Gare du Nord")
			.shed(new Shed(ShedProfile.GABLE, 1, 0.3, 0, "aurelia_transit_architecture:glass_wall", "minecraft:spruce_planks", 0.45, 1.0,
					"green", "aurelia_transit_architecture:roma_travertine"))
			.skin(PARIS_LIMESTONE)
			.facade(new Facade(Window.RECT, 4, 2, 6, 2, 3, 3, 0, true))
			// the middle pavilion: the giant thermal window, the clock, Ionic pilasters, pediment and city statues
			.clock(0, 24, -1, 2)
			.skin(glazing(PARIS_LIMESTONE))
			.arch(0, 0, 9, 5, 18, false)
			.skin(PARIS_LIMESTONE)
			.colonnade(-12, 12, -1, 28, 6, "minecraft:smooth_quartz")
			.gable(0, 0, 13, 31, 7, false)
			.pinnacles(-12, 12, 0, 31, 6, "minecraft:smooth_quartz", "minecraft:chiseled_quartz_block")
			.volume(-13, 13, 0, 14, 30, Roof.FLAT, 1, true, false)
			// the corner pavilions and the wings under mansards across the full head, the hall behind
			.volume(-END, -END + 10, 0, 14, 30, Roof.MANSARD, 3, true, true)
			.volume(END - 10, END, 0, 14, 30, Roof.MANSARD, 3, true, true)
			.volume(-END + 11, -14, 0, 12, 24, Roof.MANSARD, 3, true, true)
			.volume(14, END - 11, 0, 12, 24, Roof.MANSARD, 3, true, true)
			.hall(-END, END, 15, 60, 12)
			.build();

	/**
	 * Milano Centrale (Ulisse Stacchini, 1931): a monumental pale stone front 200 m wide, its tall middle block cut by three
	 * great portals and a vast arch above them, winged-horse sculpture at its corners, long wings with arcades; behind it Alberto Fava's
	 * five steel and glass barrel vaults over the platforms.
	 */
	static final Landmark MILAN = Landmark.head("milan", "Milano Centrale", "Milan Central", "Milano Centrale station")
			.shed(new Shed(ShedProfile.ROUND, 5, 0.45, 0, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.3, 1.0, "steel", "minecraft:calcite"))
			.skin(MILAN_STONE)
			.facade(new Facade(Window.RECT, 5, 2, 7, 2, 4, 4, 0, true))
			// the middle block: three portals, the great Galleria arch over them, stepped attic and winged-horse piers
			.skin(MILAN_STONE.withGlass("minecraft:gray_stained_glass_pane").withTrim("minecraft:polished_deepslate"))
			.clock(0, 48, -1, 2)
			.arch(-14, 0, 5, 1, 15, false)
			.arch(0, 0, 5, 1, 15, false)
			.arch(14, 0, 5, 1, 15, false)
			.skin(glazing(MILAN_STONE))
			.arch(0, 0, 10, 23, 32, false)
			.skin(MILAN_STONE)
			.colonnade(-24, 24, -1, 22, 6, "minecraft:polished_diorite")
			.pinnacles(-24, -20, 0, 46, 4, "minecraft:polished_diorite", "minecraft:chiseled_quartz_block")
			.pinnacles(20, 24, 0, 46, 4, "minecraft:polished_diorite", "minecraft:chiseled_quartz_block")
			.volume(-24, 24, 0, 22, 45, Roof.FLAT, 1, true, false)
			.upper(-16, 16, 0, 16, 46, 51, Roof.FLAT, 1)
			// the wings with their ground arcades across the full head
			.arcade(-END + 3, -27, 0, 6, 2, 1, 6)
			.arcade(27, END - 3, 0, 6, 2, 1, 6)
			.volume(-END, -25, 0, 18, 30, Roof.FLAT, 1, true, false)
			.volume(25, END, 0, 18, 30, Roof.FLAT, 1, true, false)
			.hall(-END, END, 23, 60, 14)
			.build();

	/**
	 * Chhatrapati Shivaji Maharaj Terminus, Mumbai (F. W. Stevens, 1878-87): Victorian Indo-Saracenic Gothic in honey sandstone with pale
	 * limestone and blue-grey basalt, pointed windows over a Gothic arcade, the great ribbed octagonal stone dome in the middle
	 * with Progress on top, a clock gable on the front, stone-domed turrets at the corners and clay-tiled wings; behind it a ridge and
	 * furrow steel shed.
	 */
	static final Landmark MUMBAI = Landmark.head("mumbai", "Mumbai CSMT", "Chhatrapati Shivaji Maharaj Terminus", "CSMT", "Victoria Terminus")
			.shed(new Shed(ShedProfile.GABLE, 4, 0.25, 0, "aurelia_transit_architecture:glass_wall", "minecraft:deepslate_tiles", 0.55, 1.0,
					"steel", "minecraft:smooth_sandstone"))
			.skin(MUMBAI_SANDSTONE.withRoof("minecraft:cut_sandstone", "minecraft:sandstone_stairs"))
			.facade(new Facade(Window.POINTED, 4, 2, 6, 2, 3, 2, 5, true))
			// the stone-domed corner turrets and the great ribbed octagonal stone dome
			.tower(-END + 4, 2, 3, 1, 34, Cap.DOME, 0, false)
			.tower(END - 4, 2, 3, 1, 34, Cap.DOME, 0, false)
			.tower(-17, 2, 2, 1, 33, Cap.DOME, 0, false)
			.tower(17, 2, 2, 1, 33, Cap.DOME, 0, false)
			.octTower(0, 12, 8, 1, 42, Cap.DOME, 0, false)
			// the clock gable, end gables and pointed Gothic arcade along the street, under clay-tile roofs
			.skin(MUMBAI_SANDSTONE)
			.clock(0, 34, 0, 2)
			.gable(0, 0, 9, 30, 9, false)
			.gable(-END + 8, 0, 6, 27, 7, false)
			.gable(END - 8, 0, 6, 27, 7, false)
			.arcade(-END + 6, END - 6, 0, 5, 2, 1, 6, true)
			.volume(-14, 14, 0, 22, 29, Roof.FLAT, 1, true, false)
			.volume(-END, -15, 0, 14, 26, Roof.GABLE_X, 2, true, true)
			.volume(15, END, 0, 14, 26, Roof.GABLE_X, 2, true, true)
			.hall(-END, END, 23, 60, 12)
			.build();

	/**
	 * Bangkok (Hua Lamphong, Mario Tamagno, 1916): an Italian Neo-Renaissance semicircular arched screen of cream stucco and white
	 * stone framing the stained-glass end of the barrel vault with a clock at its crown, over a white columned entrance portico,
	 * flanked by two-storey arcaded wings and pedimented end pavilions; behind it the barrel-vaulted steel shed.
	 */
	static final Landmark BANGKOK = Landmark.head("bangkok", "Krung Thep", "Bangkok", "Hua Lamphong", "Bangkok Hua Lamphong")
			.shed(new Shed(ShedProfile.ROUND, 1, 0.3, StationGeometry.CANOPY_Y, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.75, 1.0, "steel", "minecraft:smooth_sandstone"))
			.skin(BANGKOK_STUCCO)
			.facade(new Facade(Window.ROUND, 4, 2, 6, 1, 4, 1, 4, true))
			// the semicircular stained-glass vault screen in its concentric cream-and-white Neo-Renaissance archivolts
			.clock(0, 24, 0, 2)
			.arch(0, 0, 15, 11, 11, false)
			.skin(BANGKOK_STUCCO.withGlass("minecraft:smooth_sandstone"))
			.arch(0, 0, 18, 10, 11, false)
			.skin(BANGKOK_STUCCO.withRoof("minecraft:smooth_stone", "minecraft:brick_stairs"))
			.colonnade(-18, 18, -1, 9, 3, "minecraft:smooth_quartz")
			.volume(-20, 20, 0, 12, 10, Roof.FLAT, 1, true, false)
			// the pedimented end pavilions and two-storey arcaded portico wings across the full head
			.gable(-END + 5, 0, 5, 18, 4, false)
			.gable(END - 5, 0, 5, 18, 4, false)
			.volume(-END, -END + 10, 0, 14, 17, Roof.FLAT, 1, true, false)
			.volume(END - 10, END, 0, 14, 17, Roof.FLAT, 1, true, false)
			.skin(BANGKOK_STUCCO)
			.arcade(-END + 13, -23, 0, 4, 1, 1, 5)
			.arcade(23, END - 13, 0, 4, 1, 1, 5)
			.volume(-END + 11, -21, 0, 12, 13, Roof.GABLE_X, 1, true, false)
			.volume(21, END - 11, 0, 12, 13, Roof.GABLE_X, 1, true, false)
			.skin(BANGKOK_STUCCO.withRoof("minecraft:smooth_stone", "minecraft:brick_stairs"))
			.hall(-END, END, 13, 60, 12)
			.build();

	/** The shortest wings (in bays) a side sheet needs: its end pavilions stand where the wings end. */
	static final int MIN_SIDE_WINGS = 5;

	/**
	 * Tokyo Station, Marunouchi side (Tatsuno Kingo, 1914, restored 2012): a Queen Anne red brick building 335 m long, three storeys
	 * banded with white granite over a grey granite base, under dark Miyagi slate; its middle Imperial Pavilion rises to a stepped
	 * hipped slate crown, long three-storey slate-roofed wings run to both sides and each ends in a four-storey pavilion under an
	 * octagonal slate dome. Only the two end domes carry finials.
	 */
	static final Landmark TOKYO = Landmark.side("tokyo", "Tokyo", "Tokyo Station", "Tokyo Marunouchi", "Tokyo Eki")
			.skin(TOKYO_BRICK)
			.facade(new Facade(Window.ROUND, 5, 2, 6, 2, 3, 2, 3, true))
			// the octagonal drums and dark slate domes over the north and south end pavilions
			.octTower(END - 9, 10, 7, 1, 32, Cap.DOME, 0, false)
			.octTower(-END + 9, 10, 7, 1, 32, Cap.DOME, 0, false)
			.volume(END - 18, END, 0, 40, 24, Roof.HIPPED, 1)
			.volume(-END, -END + 18, 0, 40, 24, Roof.HIPPED, 1)
			// the central Imperial Pavilion: entrance arches and stepped hipped slate roofs (no central spire or clock)
			.arch(0, 0, 3, 5, 10, false)
			.arch(-8, 0, 2, 1, 7, false)
			.arch(8, 0, 2, 1, 7, false)
			.upper(-BLOCK, BLOCK, 0, 40, 22, 24, Roof.HIPPED, 1)
			.upper(-9, 9, 0, 16, 24, 28, Roof.HIPPED, 2)
			.upper(-BLOCK, -BLOCK + 5, 0, 10, 24, 26, Roof.HIPPED, 1)
			.upper(BLOCK - 5, BLOCK, 0, 10, 24, 26, Roof.HIPPED, 1)
			// the three-storey wings under pitched Miyagi slate roofs, meeting the central block cleanly
			.facade(new Facade(Window.RECT, 5, 2, 6, 2, 3, 2, 3, true))
			.volume(BLOCK + 1, END - 19, 0, 40, 18, Roof.GABLE_X, 1)
			.volume(-END + 19, -BLOCK - 1, 0, 40, 18, Roof.GABLE_X, 1)
			.build();

	/**
	 * Amsterdam Centraal, city side (P. J. H. Cuypers and A. L. van Gendt, 1881-89): a long Neo-Renaissance front of dark red
	 * brick banded with pale sandstone. Its raised middle is a tall gable over three spaced round-headed windows between two towers
	 * with steep pinnacled spires, the clock on the east one (on the right from the Stationsplein) and a wind dial on the west
	 * one; long two-storey wings run to both sides under steep slate roofs with rows of dormers, and end in stepped-gable pavilions.
	 * Sources and photos: ATA-research/stations/amsterdam.
	 */
	static final Landmark AMSTERDAM = Landmark.side("amsterdam", "Amsterdam Centraal", "Amsterdam Central", "Amsterdam CS",
					"Amsterdam Centraal Station")
			.skin(AMSTERDAM_BRICK)
			.facade(new Facade(Window.ROUND, 4, 2, 5, 1, 3, 2, 3, true))
			// the twin towers flanking the entrance: clock on the east tower, wind dial on the west
			.tower(14, 3, 3, 1, 36, Cap.SPIRE, 14, true)
			.tower(-14, 3, 3, 1, 36, Cap.SPIRE, 14, true)
			// the raised middle: the tall gable, three distinct round-headed windows with brick piers between, and steep slate roof behind
			.gable(0, 0, 10, 22, 10, true)
			.arch(-7, 0, 1, 8, 15, false)
			.arch(0, 0, 2, 8, 16, false)
			.arch(7, 0, 1, 8, 15, false)
			.pinnacles(-10, 10, 0, 23, 5, "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone")
			.volume(-BLOCK, BLOCK, 0, 40, 21, Roof.GABLE_X, 1)
			// the wings: two storeys under steep slate with dormers right up to the central block, ending in stepped-gable pavilions
			.facade(new Facade(Window.RECT, 4, 2, 6, 2, 3, 2, 3, true))
			.gable(END - 7, 0, 6, 20, 8, true)
			.gable(-END + 7, 0, 6, 20, 8, true)
			.volume(BLOCK + 1, END - 14, 0, 40, 15, Roof.GABLE_X, 1, false, true)
			.volume(-END + 14, -BLOCK - 1, 0, 40, 15, Roof.GABLE_X, 1, false, true)
			.volume(END - 13, END, 0, 40, 20, Roof.GABLE_Z, 2)
			.volume(-END, -END + 13, 0, 40, 20, Roof.GABLE_Z, 2)
			.build();

	/**
	 * Beijing Railway Station (Yang Tingbao, Chen Deng'ao and others, 1959): a monumental stone building 218 m wide, its grand
	 * arched central entrance flanked by twin clock towers under Chinese pavilion roofs of blue-green glazed tiles, with glazed-tile
	 * corner pavilions at both ends and long stone wings between.
	 */
	static final Landmark BEIJING = Landmark.side("beijing", "Beijing", "Beijing Station", "Beijing Railway Station")
			.skin(BEIJING_STONE)
			.facade(new Facade(Window.RECT, 5, 2, 6, 2, 3, 2, 0, true))
			// twin glazed-tile pavilion clock towers flanking the grand arched central hall, plus corner pavilion roofs
			.tower(-12, 4, 4, 1, 36, Cap.PYRAMID, 8, true)
			.tower(12, 4, 4, 1, 36, Cap.PYRAMID, 8, true)
			.tower(END - 6, 8, 4, 1, 29, Cap.PYRAMID, 7, false)
			.tower(-END + 6, 8, 4, 1, 29, Cap.PYRAMID, 7, false)
			.skin(glazing(BEIJING_STONE))
			.arch(0, 0, 6, 5, 14, false)
			// flat stone roofs on the central hall, crown, wings and end pavilions; blue-green glazed tiles on the four pavilion towers
			.skin(BEIJING_STONE.withRoof("minecraft:smooth_stone", "minecraft:stone_stairs"))
			.volume(-BLOCK, BLOCK, 0, 40, 22, Roof.FLAT, 1)
			.upper(-8, 8, 0, 40, 23, 26, Roof.FLAT, 1)
			.volume(BLOCK + 1, END - 13, 0, 40, 18, Roof.FLAT, 1)
			.volume(-END + 13, -BLOCK - 1, 0, 40, 18, Roof.FLAT, 1)
			.volume(END - 12, END, 0, 40, 23, Roof.FLAT, 1)
			.volume(-END, -END + 12, 0, 40, 23, Roof.FLAT, 1)
			.build();

	/**
	 * Kuala Lumpur Railway Station (A. B. Hubback, 1910-17): Indo-Saracenic white stucco, long arcades of pointed Moorish arches,
	 * a skyline of Mughal onion domes, chhatri cupolas and slender minarets over the central block and both ends.
	 */
	static final Landmark KUALA_LUMPUR = Landmark.side("kuala_lumpur", "Kuala Lumpur", "Kuala Lumpur Railway Station", "KL Railway Station")
			.skin(KL_WHITE)
			.facade(new Facade(Window.POINTED, 4, 2, 7, 2, 4, 1, 0, true))
			.octTower(0, 10, 6, 1, 28, Cap.ONION, 11, false)
			.octTower(END - 6, 10, 5, 1, 24, Cap.ONION, 9, false)
			.octTower(-END + 6, 10, 5, 1, 24, Cap.ONION, 9, false)
			.tower(-13, 2, 2, 1, 31, Cap.CUPOLA, 0, false)
			.tower(13, 2, 2, 1, 31, Cap.CUPOLA, 0, false)
			.tower(END - 13, 2, 1, 1, 27, Cap.CUPOLA, 0, false)
			.tower(-END + 13, 2, 1, 1, 27, Cap.CUPOLA, 0, false)
			.arch(0, 0, 3, 5, 11, true)
			.arch(-7, 0, 2, 1, 7, true)
			.arch(7, 0, 2, 1, 7, true)
			.arcade(-END + 13, -BLOCK - 2, 0, 4, 1, 1, 5, true)
			.arcade(BLOCK + 2, END - 13, 0, 4, 1, 1, 5, true)
			.pinnacles(-BLOCK + 2, BLOCK - 2, 0, 24, 5, "minecraft:smooth_quartz", "minecraft:chiseled_quartz_block")
			.volume(-BLOCK, BLOCK, 0, 40, 23, Roof.FLAT, 1)
			.volume(BLOCK + 1, END - 13, 0, 40, 15, Roof.FLAT, 1)
			.volume(-END + 13, -BLOCK - 1, 0, 40, 15, Roof.FLAT, 1)
			.volume(END - 12, END, 0, 40, 19, Roof.FLAT, 1)
			.volume(-END, -END + 12, 0, 40, 19, Roof.FLAT, 1)
			.build();

	/**
	 * Surbiton (J. R. Scott, Southern Railway, 1937): Streamline Moderne in crisp white-rendered concrete, a tall cubist central
	 * booking hall with vertical mullioned glazing over a projecting flat canopy, the iconic square Art Deco clock tower beside it,
	 * and lower two-storey horizontal steel-ribbon wings.
	 */
	static final Landmark SURBITON = Landmark.side("surbiton", "Surbiton")
			.skin(SURBITON_WHITE)
			.facade(new Facade(Window.CURTAIN, 5, 3, 18, 3, 13, 2, 0, true))
			.tower(BLOCK - 3, 3, 3, 1, 29, Cap.FLAT, 0, true)
			.colonnade(-9, 9, -1, 5, 6, "minecraft:smooth_quartz")
			.volume(-BLOCK, BLOCK, 0, 40, 20, Roof.FLAT, 1)
			.upper(-BLOCK + 2, BLOCK - 7, 0, 12, 21, 23, Roof.FLAT, 1)
			.skin(SURBITON_WHITE.withGlass("aurelia_transit_architecture:uk_steel_window"))
			.facade(new Facade(Window.RIBBON, 5, 4, 5, 2, 2, 1, 4, true))
			.volume(-SPAN, -BLOCK - 1, 0, 40, 12, Roof.FLAT, 1)
			.volume(BLOCK + 1, SPAN, 0, 40, 12, Roof.FLAT, 1)
			.build();

	/** Munich's board-formed concrete and curtain glass under a flat stone roof (the Hauptbahnhof's 1950s concourse). */
	static final Skin MUNICH_CONCRETE = new Skin("aurelia_transit_architecture:munich_exposed_concrete",
			"aurelia_transit_architecture:munich_board_formed_concrete", "minecraft:polished_andesite",
			"aurelia_transit_architecture:curtain_wall_glass", "minecraft:smooth_stone", "minecraft:stone_stairs",
			"minecraft:polished_andesite_stairs", "minecraft:gray_concrete");

	/** Berlin's brushed steel and blue-tinted curtain glass. */
	static final Skin BERLIN_GLASS = new Skin("aurelia_transit_architecture:metro_steel_cladding", "minecraft:light_gray_concrete",
			"minecraft:polished_andesite", "aurelia_transit_architecture:blue_tinted_glass_pane", "minecraft:gray_concrete",
			"minecraft:polished_andesite_stairs", "minecraft:polished_andesite_stairs", "minecraft:white_concrete");

	/** Coventry's Staffordshire blue engineering brick, pale concrete fascia and curtain glass (1962). */
	static final Skin COVENTRY_MODERNIST = new Skin("aurelia_transit_architecture:uk_blue_engineering_brick",
			"aurelia_transit_architecture:metro_pale_concrete", "aurelia_transit_architecture:uk_blue_engineering_brick",
			"aurelia_transit_architecture:curtain_wall_glass", "minecraft:gray_concrete", "minecraft:smooth_quartz_stairs",
			"minecraft:smooth_quartz_stairs", "minecraft:smooth_quartz");

	/**
	 * Munich Hauptbahnhof (terminus, 32 tracks): the 1950s concourse across the head of the tracks, a long low block of board-formed
	 * concrete and glass with a tall glazed middle over a cantilevered entrance canopy and ribbon-windowed wings across the full head;
	 * behind it the platform hall, a row of low glazed barrel vaults on concrete.
	 */
	static final Landmark MUNICH = Landmark.head("munich", "Munich Hbf", "Munchen Hbf", "Muenchen Hbf", "Munich Hauptbahnhof")
			.shed(new Shed(ShedProfile.ROUND, 3, 0.25, 0, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.2, 1.0, "steel", "aurelia_transit_architecture:munich_exposed_concrete"))
			.skin(MUNICH_CONCRETE)
			.clock(0, 18, -1, 1)
			.colonnade(-20, 20, -1, 6, 4, "aurelia_transit_architecture:metro_column_steel")
			.facade(new Facade(Window.CURTAIN, 4, 3, 6, 1, 4, 1, 0, true))
			.volume(-18, 18, 0, 16, 21, Roof.FLAT, 1, true, false)
			.facade(new Facade(Window.RIBBON, 4, 3, 5, 1, 3, 1, 0, true))
			.volume(-END, -19, 0, 14, 14, Roof.FLAT, 1, true, false)
			.volume(19, END, 0, 14, 14, Roof.FLAT, 1, true, false)
			.hall(-END, END, 15, 60, 12)
			.build();

	/**
	 * Berlin Hauptbahnhof (Meinhard von Gerkan / gmp, 2006): the east-west glass barrel hall intersected by the iconic pair of
	 * 46 m glazed stirrup bridge blocks (Buegelbauten) linked overhead across the full-height glass portal, flanked by lower glass
	 * concourse wings.
	 */
	static final Landmark BERLIN = Landmark.side("berlin", "Berlin Hbf", "Berlin Hauptbahnhof")
			.shed(new Shed(ShedProfile.ROUND, 1, 0.3, StationGeometry.CANOPY_Y, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.1, 1.0, "steel", null))
			.skin(BERLIN_GLASS)
			.facade(new Facade(Window.CURTAIN, 4, 3, 6, 1, 4, 1, 6, true))
			.volume(-BLOCK, BLOCK, 0, 40, 23, Roof.FLAT, 1)
			.volume(-SPAN, -BLOCK - 1, 0, 40, 17, Roof.FLAT, 1)
			.volume(BLOCK + 1, SPAN, 0, 40, 17, Roof.FLAT, 1)
			.upper(-BLOCK, -BLOCK + 7, 0, 40, 24, 36, Roof.FLAT, 1)
			.upper(BLOCK - 7, BLOCK, 0, 40, 24, 36, Roof.FLAT, 1)
			.upper(-BLOCK + 8, BLOCK - 8, 2, 12, 32, 36, Roof.FLAT, 1)
			.build();

	/**
	 * Coventry (W. R. Headley, British Railways, 1962): a crisp post-war modernist double-height curtain-glazed booking hall cube
	 * with a projecting flat white fascia canopy, flanked by long low wings of Staffordshire blue engineering brick with clerestory
	 * ribbon glazing.
	 */
	static final Landmark COVENTRY = Landmark.side("coventry", "Coventry", "Coventry Station")
			.skin(COVENTRY_MODERNIST)
			.colonnade(-9, 9, -1, 5, 6, "aurelia_transit_architecture:steel_column_square")
			.facade(new Facade(Window.CURTAIN, 4, 3, 7, 1, 5, 1, 0, true))
			.volume(-BLOCK, BLOCK, 0, 40, 18, Roof.FLAT, 1)
			.facade(new Facade(Window.RIBBON, 5, 4, 5, 2, 2, 2, 0, true))
			.volume(-SPAN, -BLOCK - 1, 0, 40, 12, Roof.FLAT, 1)
			.volume(BLOCK + 1, SPAN, 0, 40, 12, Roof.FLAT, 1)
			.build();

	static final List<Landmark> ALL = List.of(ST_PANCRAS, ANTWERP, GRAND_CENTRAL, FRANKFURT, ZURICH, PARIS, MILAN, MUMBAI, BANGKOK, MUNICH, BERLIN,
			COVENTRY, TOKYO, AMSTERDAM, BEIJING, KUALA_LUMPUR, SURBITON);

	/**
	 * The town stations among the landmarks, whose real buildings are city-sized blocks: Surbiton's runs 61 m (Coventry has no
	 * building outline to measure). This list is what sizes a landmark: one not in it gets a grand building, so a new town
	 * landmark goes here.
	 */
	static final List<Landmark> TOWN = List.of(SURBITON, COVENTRY);

	/**
	 * The building size of the real station the words name (by name or by a city word), or null for none, so a real station
	 * is built at its own size and not the one its track count gives an ordinary station: city for a {@link #TOWN} station,
	 * grand for the main stations.
	 */
	public static StationScale ownScale(String name, String sheet) {
		// the name wins over a city word, as in of()
		for (final String words : new String[] {name, sheet}) {
			for (final Landmark landmark : ALL) {
				if (words != null && !words.isEmpty() && landmark.named(words)) {
					return TOWN.contains(landmark) ? StationScale.CITY : StationScale.GRAND;
				}
			}
		}
		return null;
	}

	/** The sheet of the real station {@code spec} is named after, or null. */
	public static Landmark of(StationSpec spec) {
		final String name = spec.name();
		for (final Landmark landmark : ALL) {
			if (landmark.named(name)) {
				return landmark;
			}
		}
		// a station without a name of its own that a city word names ("frankfurt terminus") is that city's station
		// a head sheet only on a plain main-station terminus, a side sheet on a through station with its building: the sheets are as
		// wide as the real buildings and are checked at preset size
		if (name.isEmpty() && !spec.options().sheet().isEmpty() && spec.buildingScale() != StationScale.COMPACT) {
			for (final Landmark landmark : ALL) {
				if (landmark.named(spec.options().sheet()) && carries(landmark, spec)) {
					return landmark;
				}
			}
		}
		return null;
	}

	/** True when the station can take the landmark's sheet by a city word. */
	private static boolean carries(Landmark landmark, StationSpec spec) {
		if (landmark.anchor() != Landmark.Anchor.HEAD) {
			// Amsterdam Centraal's long wings leave dark interior cells on a side-platform building (its own platform layout is
			// islands), so a side-platform station keeps the city's look instead of the sheet
			final boolean amsterdamFits = landmark != AMSTERDAM || spec.platformLayout() == PlatformLayout.ISLAND;
			return !spec.terminus() && spec.sideBuilding() && spec.trackCount() >= 2 && amsterdamFits;
		}
		if (!spec.terminus() || spec.hub() || spec.trackCount() < 6 || spec.trackCount() % 2 != 0 || spec.platformLength() < 96) {
			return false;
		}
		// where a sweep of every card setting found the sheet unbuildable (10-25), the city's own look is built instead: a single
		// arch shed under a sheet whose own shed has several spans (Milan) leaves girders on nothing; St Pancras's towers float on six
		// tracks; Paris and St Pancras on ten leave indoor spots unlit; a front on a city-size building floats a fixture (Bangkok,
		// Zurich)
		if (landmark.shed() != null && landmark.shed().spans() > 1 && spec.roof() == RoofType.HALL && spec.shed() == ShedShape.ARCH) {
			return false;
		}
		if (landmark == ST_PANCRAS && spec.trackCount() != 8 || landmark == PARIS && spec.trackCount() > 8) {
			return false;
		}
		return spec.buildingScale() != StationScale.CITY;
	}
}
