# Real stations the generator copies

> Reconstructed from code; original sources not in repo.

This file is rebuilt from `fabric/src/main/java/com/aureliatransit/architecture/station/RealStation.java`,
`StationGeometry.java`, `StylePalette.java` and the notes in `StationDescriptionParser.java`. The original
source list cited by `RealStation.java` is not in the tree, so the citations below are code locations, not
published references. Line numbers are for the reconstruction base (`5f2167c`).

The stations are not generic "looks after" a city: each one fixes the layout, dimensions, materials and
operator the generator copies. Where a fact is unknown or does not fit the game's limits, the generator
builds the nearest thing and says so (`RealStation.java:5-8`).

Scale: one block is about two thirds of a metre. `StationGeometry.metres(n)` converts metres to blocks at
1.5 blocks per metre (`StationGeometry.java`).

## Utrecht Centraal

`RealStation.UTRECHT_CENTRAAL` (`RealStation.java:20-26`)

| Fact | Value | Code source |
| --- | --- | --- |
| Architect / year | Benthem Crouwel, 2016 | `RealStation.java:21` |
| Kind | one closed hall over all the tracks | `RealStation.java:21-22`, `Kind.HALL_OVER_TRACKS` at `RealStation.java:40-41` |
| Setting | between the raised Stationsplein (east) and the Jaarbeursplein (west) | `RealStation.java:21-22` |
| Roof size | about 235 x 85 m and 18 m high | `RealStation.java:22-23` |
| Roof material | steel | `RealStation.java:23` |
| Roof waves | three, the highest over the trains | `RealStation.java:23-24` |
| Walls | glass walls hung from the roof | `RealStation.java:24` |
| Concourse | gate lines across the hall | `RealStation.java:24` |
| Platforms | 8 island platforms (16 tracks) | `RealStation.java:24-26` |
| Access | stairs, escalators and lifts down to the platforms | `RealStation.java:23-24` |
| Outside the hall | separate canopies on the platforms | `RealStation.java:25-26` |
| Operator | `Operator.NS` | `RealStation.java:26` |
| Enum values | islands 8, hallDepth 85, hallHeight 18, waves 3 | `RealStation.java:26` |

The planner's derived rules:

- A hall leaves 24 rows free at each end of the platforms (`RealStation.HALL_MARGIN`,
  `RealStation.java:51-52`).
- The shallowest hall the generator builds is 31 rows (`RealStation.MIN_HALL_DEPTH`,
  `RealStation.java:53-54`).
- The middle wave takes 56% of the width; the side waves rise 40% as high
  (`RealStation.MIDDLE_WAVE` and `RealStation.SIDE_WAVE`, `RealStation.java:147-151`).
- Utrecht's own roof follows the middle wave over the tracks and the lower waves over the bus and tram
  stations beyond them (`RealStation.roofProfile(...)`, `RealStation.java:166-177`).
- A real hall is only built over island platforms with a track each side
  (`RealStation.islandsOnly(...)`, `RealStation.java:193-203`).

Platform materials: the sources show Utrecht Centraal's canopies were renewed from 2010, some with solar
cells. Their shape and material are not in the sources, so the generator uses plain steel canopies on round
columns (`StylePalette.java:190-195`). The Utrecht platform palette also uses light paving, a warning edge,
platform lamps, steel benches and Dutch station signs (`StylePalette.java:196-201`).

## Hamburg Hauptbahnhof

`RealStation.HAMBURG_HBF` (`RealStation.java:27-35`)

| Fact | Value | Code source |
| --- | --- | --- |
| Architects / year | Reinhardt and Süßenguth, 1906 | `RealStation.java:28` |
| Kind | through station in a cutting under a steel and glass train shed | `RealStation.java:28-29`, `Kind.TRAIN_SHED` at `RealStation.java:42-43` |
| Shed length | 150 m | `RealStation.java:29` |
| Shed width | 114 m | `RealStation.java:29` |
| Shed height | up to 37 m | `RealStation.java:29` |
| Middle span | 73 m, with a lower aisle each side | `RealStation.java:29-30` |
| Platforms | 5 island platforms | `RealStation.java:30-31` |
| S-Bahn tracks | 3-4 | `RealStation.java:30` |
| Long-distance / regional tracks | 5-8 and 11-14 | `RealStation.java:30-31` |
| Through tracks without platforms | 9-10 | `RealStation.java:31` |
| Entrances | bridges at street level at both ends of the shed | `RealStation.java:31` |
| North side | the Wandelhalle, with its entrance halls | `RealStation.java:31` |
| Clock towers | two, 45 m over the street | `RealStation.java:31`, `RealStation.HAMBURG_TOWER_METRES` at `RealStation.java:48-49` |
| Operator | `Operator.DB` | `RealStation.java:35` |
| Enum values | islands 5, hallDepth 150, hallHeight 37, waves 3 | `RealStation.java:35` |
| Nave share | 73 / 114 of the width | `RealStation.HAMBURG_NAVE`, `RealStation.java:46-47` |
| Tower top | 45 m over the street | `RealStation.towerTopY()`, `RealStation.java:100-102` |
| Track count used by the plan | 10 (2 per island) | `RealStation.tracks()`, `RealStation.java:121-123` |

The parser currently says the two through tracks without platforms and the S-Bahn tunnel are not built
(`StationDescriptionParser.java:2138-2139`).

## Names and loose city words

- `RealStation.of("utrecht centraal")` and `RealStation.of("hamburg hauptbahnhof")` return the station;
  `"hamburg hbf"` also works (`RealStation.java:238-247`).
- `"utrecht station"`, `"utrecht central"` and `"utrecht cs"` may also mean Utrecht Centraal
  (`RealStation.loosely(...)`, `RealStation.java:226-230`).
- `"hamburg station"` is deliberately not the real station: it stays the city's look, because Hamburg Hbf
  brings a shed and a building of its own (`RealStation.java:226-230`).

## `StationScale` (UK vs. Dutch 4-Track Sizing)

- **Generic / UK stations**: `StationScale.forSize(building, platformedTracks, false)` gives `COMPACT` below `4` platformed tracks, `CITY` for `4–5` platformed tracks (matching scored UK town/suburban 4-track blocks: Surbiton `61 m`, Leamington Spa `75 m`, median `68 m`), and `GRAND` from `6` platformed tracks (`GRAND_BUILDING_TRACKS`). A 4-track station whose fast lines have no platform faces counts `2` platformed tracks (`COMPACT`, as at Berrylands and Winchfield).
- **Dutch stations**: `StationScale.forSize(building, platformedTracks, true)` gives `GRAND` from `4` platformed tracks (`CITY_BUILDING_TRACKS`) whenever a station carries a Dutch style, operator, or city look (`dutch`, `netherlands`, `holland`, `nl`, `ns`, `utrecht`, `leidsche`, `amsterdam`, `rotterdam`) and no explicit scale word (`compact`, `town`, `city`, `grand`) was given, matching long Dutch 4-track main-line halls (Nijmegen `223 m`, Lelystad Centrum `174 m`, Almere Centrum `154 m`). Explicit scale words always override.

## `Landmarks` (Real-Station Building & Shed Sheets)

### Terminus Head Sheets (`Anchor.HEAD`)
All terminus sheets follow `headFrom..headTo` (`END`) so wide 8- and 10-track termini carry the landmark's own wings and corner pavilions across the full head without generic side walls.

| Key | Station | Architect & Year | Key Architectural Features |
| --- | --- | --- | --- |
| `st_pancras` | London St Pancras | G. G. Scott / W. H. Barlow, 1868–76 | Polychrome red-brick Midland Grand Hotel, pointed Gothic windows, `82 m` east Elizabeth clock tower & west spire tower, single-span sky-blue pointed shed (`74.8 m` span). |
| `antwerp` | Antwerpen-Centraal | L. Delacenserie / C. van Bogaert, 1895–1905 | Ornate limestone & travertine palace, `60 m` central copper-slate dome with gilded lantern, twin domed towers framing the grand arch & gilded clock, mansard wings & single-span arched shed. |
| `grand_central` | Grand Central Terminal | Reed & Stem / Warren & Wetmore, 1913 | Indiana limestone Beaux-Arts 42nd Street facade, three `18 m` bronze-framed arched windows between paired columns, Tiffany clock & Glory of Commerce pediment, teal-and-gold-star concourse ceiling. |
| `frankfurt` | Frankfurt (Main) Hbf | H. Eggert, 1888 (wings 1924) | Ochre sandstone Neo-Renaissance portal with giant central arch, clock & bronze Atlas group, twin domed pavilions, 1924 outer wings, and three barrel-vaulted green-iron sheds. |
| `zurich` | Zürich HB | J. F. Wanner, 1871 | Sandstone triumphal-arch Nordfassade with clock & attic, flush slate-mansard wings ending in corner pavilions, tall round-arched `Haupthalle`, and three-span barrel shed. |
| `paris` | Paris Gare du Nord | J. I. Hittorff, 1861–65 | Cream limestone Ionic facade, giant thermal arch window & clock under a pediment crowned with destination statues, slate-mansard wings & corner pavilions, green-column gabled shed. |
| `milan` | Milano Centrale | U. Stacchini / A. Fava, 1931 | Monumental Botticino stone facade, three carriageway portals under the great Galleria arch, stepped attic & winged-horse corner piers, arcaded wings, and five barrel vaults. |
| `mumbai` | Mumbai CSMT | F. W. Stevens, 1878–87 | Victorian Indo-Saracenic Gothic in honey sandstone, limestone & red sandstone bands, ribbed octagonal stone central dome, stone-domed corner turrets, Gothic arcade, and clay-tiled wings. |
| `bangkok` | Bangkok Hua Lamphong | M. Tamagno, 1916 | Semicircular Neo-Renaissance cream-and-white stucco arched screen framing the stained-glass barrel vault with crown clock over a white columned portico, flanked by arcaded wings & pedimented pavilions. |
| `munich` | München Hbf | Krupp / Bundesbahn, 1958–60 | Post-war board-formed concrete & curtain-glass head concourse over a cantilevered steel canopy with ribbon-glazed wings and three low glazed barrel vaults. |

### Through-Station Side Sheets (`Anchor.SIDE`)
Grand side sheets follow `buildingFrom..buildingTo` (`END`) and meet the central concourse block (`BLOCK`) cleanly at both odd and even bay counts; town/modernist side sheets (`TOWN`: Surbiton, Coventry, plus Berlin) use `SPAN` and `BLOCK` so they fit city and grand scales alike without closing wing waiting-room doorways or concourse lifts.

| Key | Station | Architect & Year | Key Architectural Features |
| --- | --- | --- | --- |
| `surbiton` | Surbiton (`CITY`) | J. R. Scott, 1937 | Streamline Moderne white-rendered concrete cubist booking hall with vertical mullioned glass & flat quartz canopy, square Art Deco clock tower beside the hall, two-storey steel-ribbon wings (`61 m`). |
| `coventry` | Coventry (`CITY`) | W. R. Headley, 1962 | Post-war modernist double-height curtain-glazed booking hall cube under a projecting pale concrete fascia canopy, flanked by low Staffordshire blue engineering brick clerestory wings. |
| `amsterdam` | Amsterdam Centraal (`GRAND`) | P. J. H. Cuypers & A. L. van Gendt, 1881–89 | Dark red brick banded with sandstone, twin pinnacled spire towers (east clock, west wind dial) flanking the stepped central gable & spaced round-headed windows, steep slate wings with dormers & stepped-gable end pavilions. |
| `tokyo` | Tokyo Marunouchi (`GRAND`) | Tatsuno Kingo, 1914 (restored 2012) | `335 m` Queen Anne red brick with white granite bands, stepped hipped slate Imperial Pavilion over the centre, three-storey slate wings, and four-storey octagonal domed north/south pavilions. |
| `berlin` | Berlin Hbf (`GRAND`) | M. von Gerkan / gmp, 2006 | Brushed-steel and blue-glass portal hall flanked by lower glass wings and intersected by the twin `46 m` glazed stirrup bridge blocks (`Bügelbauten`) over a single-span glass barrel shed. |
| `beijing` | Beijing Station (`GRAND`) | Yang Tingbao & Chen Deng'ao, 1959 | `218 m` Sino-Soviet stone facade, grand central arched glass portal flanked by twin clock towers under blue-green glazed Chinese pavilion roofs, with glazed-tile corner pavilions at both ends. |
| `kuala_lumpur` | Kuala Lumpur (`GRAND`) | A. B. Hubback, 1910–17 | Indo-Saracenic white stucco (`calcite` & `quartz`), pointed Moorish wing arcades, chhatri parapet pinnacles, slender cupola minarets, and central & end Mughal onion domes. |

## Gaps left by the reconstruction

- The published-source list that `RealStation.java:6-7` points at is not in the repository. Only the facts
  reproduced in the enum and its comments are recorded here.
- Utrecht Centraal's platform-canopy shape and material are not in the repository; `StylePalette.java`
  records that plain steel canopies on round columns stand in.
- Hamburg's through tracks 9-10 without platforms and the S-Bahn tunnel are documented in the real-station
  comment but are not built by the parser (`StationDescriptionParser.java:2138-2139`).
