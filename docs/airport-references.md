# Airport references

The airport generator builds at **1.5 blocks to the metre** (`AirportSpec.BLOCKS_PER_METRE`). Every number below is the real
measurement followed by the block value the planner uses. The default regional airport is a **code C** strip (30 m / 45 blocks
wide); a description that asks for `45 m wide runway` gets the **code E** strip (68 blocks), the widest the plan holds.

ICAO Annex 14, Volume I (8th edition, 2018) is the source for the standards; FAA AIM 2-3-3 and the Heathrow airside stand
instructions agree on the marking geometry. Links are at the bottom; no images are stored in this repository.

## Runway

| Feature | Real | In the plan |
|---|---|---|
| Code C width | 30 m | 45 blocks (default) |
| Code E width | 45 m | 68 blocks (`MAX_RUNWAY_WIDTH`) |
| Code F width | 60 m | not built (above the plan's cap) |
| Threshold "piano keys" | 1.8 m wide, 30 m long, ~1.8 m gaps | 3 blocks wide, 45 blocks long |
| Number of threshold stripes | 4 / 6 / 8 / 12 / 16 by runway width 18 / 23 / 30 / 45 / 60 m | `PlanAirport.thresholdStripes` gives 4 / 6 / 8 / 12 for the widths the plan holds |
| Runway designation | 9 m high characters, 12 m past the threshold stripes | 3-scale glyphs 15 blocks high, number at 65 blocks (43 m) from the threshold |
| Centreline | 30 m stripe, 20 m gap, 0.9 m wide | `dash` 45, `dashGap` 30, 1 block wide |
| Aiming point | 45–60 m by 6 m, 400 m from the threshold on a code 4 runway | 68 by 9 blocks at 600 blocks for a 2,400 m+ runway; 450 blocks on the default 1,700 m runway |
| Touchdown-zone bars | 22.5 m by 3 m, pairs every 150 m from 150 m; 1/2/3/4/6 pairs by landing distance | `PlanAirport.touchdownBars`: 34 blocks long, 3 blocks wide, at 150 m intervals, 1–4 pairs on the default |
| Side stripes | 0.9 m wide, inside the pavement edge | 1 block, one column in from each edge |
| Edge lights | white, not more than 60 m apart | white `runway_edge_light` every 90 blocks (60 m) |
| Threshold lights | green, across the threshold | green `runway_threshold_light` at both ends |
| Surface | asphalt or grooved concrete | black concrete; the blast pads are polished andesite |

The aiming point is one bar per side, 45 m long (68 blocks) and 6 m wide (9 blocks) on runways of 1,200 m or more, exactly as
Annex 14's table prescribes; touchdown-zone pairs march out at 150 m intervals and stop within half the runway.

## Taxiway

| Feature | Real | In the plan |
|---|---|---|
| Width, code E | 23 m | 35 blocks (`taxi` in `AirportGeometry.REAL`) |
| Shoulder | 4.5 m each side, paved | part of the 35-block strip, laid in the taxiway's grey concrete |
| Centreline | 0.15 m wide, yellow | 1 block yellow |
| Edge lines | 0.15 m wide, yellow, broken at intersections | 1 block at `taxiU0 + 1` and `taxiU1 - 1`, broken at every link |
| Runway holding position | two solid and two dashed yellow lines, 0.15 m wide | four 1-block lines, solid pair nearest the runway, dashed pair beyond |
| Edge lights | blue, not more than 60 m apart | blue `taxiway_edge_light` every 90 blocks |
| Centreline lights | green, 15–30 m spacing | green `runway_threshold_light` every 45 blocks |
| Link width | the taxiway's own width | 35 blocks, with its centreline, edge lines, blue lights and hold line |

The parallel taxiway's centreline stands 286 blocks (191 m) from the runway's on the code E layout and 275 blocks (183 m) on the
default code C one, above Annex 14's 182.5 m code E separation. Two parallel runways are 353 blocks (235 m) apart on the code E
layout and 330 blocks (220 m) on the code C one, above the 210 m used for non-instrument parallel runways.

## Apron and stands

| Feature | Real | In the plan |
|---|---|---|
| Apron surface | light concrete slabs 6–8 m with joints | light grey concrete, darker joint every 12 blocks (8 m) |
| Stand pitch | about 40–50 m for an A320 family aircraft | 66 blocks (44 m) |
| Stand depth | 50–70 m from terminal to taxiway | 84 blocks (56 m) |
| Lead-in line | yellow, from the taxilane to the stop position | 1 block yellow along the stand row |
| Stop bar | white/yellow bar across the lead-in | white, 9 blocks wide, across the stand row |
| Stand number | painted at the stand | 2-scale two-digit number beside the stop bar |
| Equipment restraint area | red and white hatched boundary | red/white hatch along the terminal edge and a hatched box between stands |
| Taxilane | yellow centreline along the apron | stand lead-ins run into the taxiway centreline; no separate taxilane is drawn under the stands |

## Terminal and forecourt

| Feature | Real | In the plan |
|---|---|---|
| Terminal depth | 40–100 m regional terminal | 60 blocks (40 m) |
| Terminal height | 12–20 m, long and low | 18 blocks (12 m) walls, roof at 19 |
| Airside facade | full-height glass curtain wall with mullions | glass from the floor to the wall top, a mullion every 5 blocks (7.5 m), one-block roof overhang |
| Landside | glazed band over a plinth, entrance canopy | plinth, glass band, 9-block entrance, canopy over the door |
| Gate doors | one per stand, in the airside wall | 7 blocks wide, one per stand |
| Boarding bridge | 20–35 m telescoping tube, rotunda, drive column with wheels, cab at the door | 34-block (23 m) glass tube, 7 wide, metal bands every 4 blocks, widened 9-block rotunda at the wall, two drive columns with black wheels, cab block at the aircraft door |
| Forecourt road | 2–3 lanes plus kerbs | 18 blocks (12 m): two 2-block footways, kerbs, 12-block carriageway |
| Kerb | 0.15–0.3 m above the road | bottom slab at y = 1 (half a block) |
| Canopy | over the drop-off door | flat roof on two columns, 5 blocks above the footway |
| Street furniture | lamps, crossings, lane lines | lamps every 24 blocks (16 m), zebra crossing, dashed centre line |

## Control tower

| Feature | Real | In the plan |
|---|---|---|
| Height | 30–70 m | 72 blocks (48 m) to the roof |
| Shaft | 4–8 m across, concrete | 5 by 5 blocks (3.3 m) of white concrete, darker corner pilasters and window bands |
| Cab | 8–15 m across, glazed, leaning outward | 11 by 11 blocks (7.3 m), three rings that step from 7 to 9 to 11 blocks |
| Cab floor and roof | concrete slabs | light grey concrete floor and roof |
| Beacon | white or red obstacle light on the roof | sea lantern beacon on a short pole at the roof centre |

## Decorations are plain blocks

Everything in this pass is an ordinary block: concrete, glass, iron, sea lanterns, slabs. The only block entities in an airport
are the existing jet-bridge tunnel and departures-board display; the new tower, rotunda, drive columns, kerbs, canopy and
markings are static blocks.

## Reference images

Runway and markings:

1. [Bristol Airport runway with piano keys at the threshold](https://commons.wikimedia.org/wiki/File:15_Bristol_Airport_sloped_runway_with_runway_lights_in_early_morning_-_Bristol_UK.jpg)
2. [Runway markings diagram (threshold, designation, centreline, aiming point, touchdown zone)](https://commons.wikimedia.org/wiki/File:Runway_markings.svg)
3. [Runway landing designator numbers](https://commons.wikimedia.org/wiki/File:Runway_landing_designator_marking-Numbers.svg)
4. [Airport runway lights](https://commons.wikimedia.org/wiki/File:Airport_lights_1.webp)

Taxiway and holding positions:

5. [Taxiway holding position marking pattern A](https://commons.wikimedia.org/wiki/File:Holding_position_runway.svg)
6. [Taxiway with holding position sign, edge markings and runway holding position marking](https://commons.wikimedia.org/wiki/File:Taxiway_8255.JPG)
7. [Holding position marking on a taxiway at Mumbai Airport](https://commons.wikimedia.org/wiki/File:Mumbai_Airport_Holding_Position.png)
8. [Taxiway centreline light set into the pavement](https://commons.wikimedia.org/wiki/File:Taxiway_light_1667.JPG)

Apron and stands:

9. [Aircraft and vehicles on an apron with the terminal behind](https://commons.wikimedia.org/wiki/File:An_airport_apron_at_sunset.jpg)
10. [Apron stands 1–12 with lead-in lines at Khon Kaen Airport](https://commons.wikimedia.org/wiki/File:Khon_Kaen_Airport_Apron_Stands_1-12.jpg)

Terminal facade:

11. [Airside glass facade of a new terminal](https://commons.wikimedia.org/wiki/File:Airside_Facade_New_Terminal_Tuticorin_Airport_Aug25_A7CR_07474.jpg)
12. [Terminal window wall in sunlight](https://commons.wikimedia.org/wiki/File:Airport_terminal_window_sun_(Unsplash).jpg)

Jet bridges:

13. [Jet bridge No. 111 at Munich Airport](https://commons.wikimedia.org/wiki/File:Jet_bridge_No._111_at_Munich_Airport.jpg)
14. [Jet bridge at Washington Dulles](https://commons.wikimedia.org/wiki/File:Washington-Dulles-Airport-Jet-Bridge-1.jpg)
15. [Labelled jet bridge sketch: rotunda, telescoping tube, drive column, cab](https://commons.wikimedia.org/wiki/File:Jet_bridge_sketch_labelled_path.svg)

Control tower:

16. [Denver International Airport control tower](https://commons.wikimedia.org/wiki/File:DEN_Air_Traffic_Control_Tower.jpg)
17. [Los Angeles International Airport control tower](https://commons.wikimedia.org/wiki/File:Los_Angeles_International_Airport,_Air_Traffic_Control_Tower_1_2018-05-20.jpg)

Landside forecourt:

18. [Terminal 3 forecourt and drop-off area](https://commons.wikimedia.org/wiki/File:Terminal_3_Forecourt_-_geograph.org.uk_-_1289915.jpg)
19. [Terminal passenger drop-off zone](https://commons.wikimedia.org/wiki/File:New_PIT_terminal_passenger_drop_off_zone.jpg)
20. [Drop-off and pick-up zone in front of a terminal](https://commons.wikimedia.org/wiki/File:2015-04-14_00_35_22_Drop-off_and_pick-up_zone_in_front_of_Terminal_2_at_Salt_Lake_City_International_Airport,_Utah.jpg)

## Standards and text sources

- [ICAO Annex 14, Volume I — Aerodromes (8th edition, 2018)](https://www.iacm.gov.mz/app/uploads/2018/12/an_14_v1_Aerodromes_8ed._2018_rev.14_01.07.18.pdf)
- [FAA AIM, Chapter 2, Section 3 — Airport Marking Aids and Signs](https://www.faa.gov/air_traffic/publications/atpubs/aim_html/chap2_section_3.html)
- [Heathrow — Aircraft Aprons, Stands, Types and Markings](https://www.heathrow.com/content/dam/heathrow/web/common/documents/company/team-heathrow/airside/operational-safety-insttructions/ASGrOps_OSI_030_Aircraft_Aprons_Stands_Types_and_Markings_v3.pdf)
- [ICAO Annex 14 Chapter 6 extract — visual aids](https://www.flashtechnology.com/wp-content/uploads/2017/09/ICAO-Annex-14-Chapter-6-2013.pdf)
