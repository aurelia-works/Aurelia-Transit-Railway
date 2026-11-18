# Real Stations and Landmark Sheets

Reference dimensions, material choices, and sizing rules for shipped real stations in `RealStation`, `Landmarks`, and `StationScale` (`1.5` blocks per real metre). Reference photographs and per-station notes live in `ATA-research/stations/`.

## `RealStation` (Measured Station Cross-Sections)

- **Utrecht Centraal** (`RealStation.UTRECHT_CENTRAAL`, Benthem Crouwel, 2011–16): Elevated glazed concourse hall spanning the tracks between two raised squares (`Stationsplein` and `Jaarbeursplein`), crowned by a three-wave roof whose central wave crests over the track group (`roofProfile`). Platform canopies were renewed from 2010 onward (some fitted with solar cells); because published architectural drawings do not specify a single canopy profile across all islands, plain steel canopies on round columns (`canopy_flat` on `steel_column_round`) stand in under the hall.
- **Hamburg Hauptbahnhof** (`RealStation.HAMBURG_HBF`, Reinhardt & Süßenguth, 1906): Single monumental arched steel-and-glass train shed (`73 m` main span) entered via end bridges (`Wandelhalle`) and flanked by two `45 m` square sandstone-and-clinker clock towers (`tallTowerSize`).

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
