# Landmark reference briefs

> Reconstructed from code; original sources not in repo.

`Landmarks.java` says the sheets come from photos and published sizes, and that "the reference briefs list
the sources" (`Landmarks.java:15-18`). Those briefs are not in the tree. The only concrete path in the code
is `ATA-research/stations/amsterdam` (`Landmarks.java:374`), which is also absent. This file rebuilds the
briefs as a list of facts that are already in the code comments. Citations are code locations, not published
references. Line numbers are for the reconstruction base (`5f2167c`).

## How the sheets work

- A sheet is drawn in its building's front frame: `x` across the street front (0 in the middle, + to the
  right as seen from the street), `z` into the building from the street wall, `y` up from the floor
  (`Landmark.java:14-17`).
- A terminus sheet stands on its head building; a through station's sheet stands on the building beside the
  tracks (`Landmark.java:22-27`).
- One block is about two thirds of a metre. Heights are squeezed where the real building would pass the
  station's height limit (`Landmarks.java:17-18`).
- A station gets a sheet when it is named after it, as every preset is (`Landmarks.java:16-18`).
- A side sheet needs at least `MIN_SIDE_WINGS = 5` bays of wings; its end pavilions stand where the wings end
  (`Landmarks.java:346-347`).
- The sheet roster is `Landmarks.ALL` (`Landmarks.java:506-507`).
- `Landmarks.of(...)` matches a station's own name first, then a city word carried in the options when the
  station has no name of its own (`Landmarks.java:509-528`).

## Sheet facts

### St Pancras

`Landmarks.ST_PANCRAS` (`Landmarks.java:104-130`)

- George Gilbert Scott's Midland Grand Hotel, 1868-76, and W. H. Barlow's train shed, 1868.
- The hotel stands across the head of the tracks in red Gretton brick with Ancaster stone bands.
- Pointed Gothic windows in five storeys.
- Steep slate roofs with dormers.
- A clock tower with a spire at the east end (on the right from Euston Road) and a lower spired tower at the
  west end.
- A gabled centre bay over pointed arches.
- Behind it the single pointed span of the shed: 74.8 m wide, 30.5 m high, springing from the platforms.
- The shed ribs are pale sky blue; its roof is slate below a glazed band.
- Names: `St Pancras`, `St Pancras International`, `London St Pancras`.

### Antwerpen-Centraal

`Landmarks.ANTWERP` (`Landmarks.java:132-160`)

- Louis Delacenserie, 1895-1905.
- Grey stone head building with a great dome (about 60 m) over the hall.
- A tall glazed bay under the dome.
- An arcade of round arches along the street.
- Two corner towers with blue domes and lanterns.
- Mansard-roofed wings.
- Behind it Clement van Bogaert's iron and glass shed: 185 m long and 44 m high, its ribs springing from the
  platforms.
- Names: `Antwerpen Centraal`, `Antwerp Central`, `Antwerpen-Centraal`.

### Grand Central Terminal

`Landmarks.GRAND_CENTRAL` (`Landmarks.java:162-186`)

- Reed and Stem, Warren and Wetmore, 1913.
- 42nd Street front of pale limestone on a granite base.
- Three great arched windows between paired columns.
- An attic with the clock and the Glory of Commerce statues.
- A flat roof of green copper.
- Inside, the Main Concourse under its teal sky with gold stars.
- The real tracks run under the city; here they stay at street level under the station's hall.
- Names: `Grand Central`, `Grand Central Terminal`.

### Frankfurt (Main) Hauptbahnhof

`Landmarks.FRANKFURT` (`Landmarks.java:188-214`)

- Hermann Eggert, 1888.
- Neo-Renaissance front of ochre sandstone.
- The middle is a giant arched window under the Atlas group and the clock, flanked by lower wings and two end
  pavilions with domed caps.
- Behind it three barrel-vaulted halls side by side, grey-green iron with glazed tops.
- Names: `Frankfurt Hbf`, `Frankfurt Hauptbahnhof`, `Frankfurt (Main) Hbf`.

### Zurich Hauptbahnhof

`Landmarks.ZURICH` (`Landmarks.java:216-239`)

- Jakob Friedrich Wanner, 1871.
- Neo-Renaissance front of pale sandstone round a triumphal-arch portal with a clock.
- Long wings with half-round windows under slate mansards.
- The tall Haupthalle behind it with arched windows.
- The platforms under barrel halls side by side.
- Names: `Zurich HB`, `Zurich Hauptbahnhof`, `Zuerich HB`.

### Paris Gare du Nord

`Landmarks.PARIS` (`Landmarks.java:241-263`)

- Jacques Ignace Hittorff, 1861-65.
- Cream limestone front.
- The middle pavilion has one giant arched window under a pediment.
- Statues of the cities served run along the cornice.
- Corner pavilions and wings under slate mansards.
- Behind it the three-nave shed on tall dark green cast-iron columns, its roof timber brown with a glazed
  nave.
- Names: `Paris Nord`, `Gare du Nord`, `Paris Gare du Nord`.

### Milano Centrale

`Landmarks.MILAN` (`Landmarks.java:265-293`)

- Ulisse Stacchini, 1931.
- Monumental pale stone front, 200 m wide.
- The tall middle block is cut by three great portals and a vast arch above them, with sculpture at its
  corners.
- Long wings with arcades.
- Behind it Alberto Fava's five steel and glass barrel vaults over the platforms.
- Names: `Milano Centrale`, `Milan Central`, `Milano Centrale station`.

### Mumbai CSMT

`Landmarks.MUMBAI` (`Landmarks.java:295-320`)

- F. W. Stevens, 1878-87.
- Victorian Gothic in honey sandstone with pale limestone and blue-grey basalt.
- Pointed windows over a round-arched arcade.
- The great ribbed octagonal dome in the middle with Progress on top.
- A clock gable on the front.
- Turrets with domed caps at the corners.
- Behind it a long ridge-and-furrow steel shed.
- Names: `Mumbai CSMT`, `Chhatrapati Shivaji Maharaj Terminus`, `CSMT`, `Victoria Terminus`.

### Bangkok Hua Lamphong

`Landmarks.BANGKOK` (`Landmarks.java:322-344`)

- Mario Tamagno, 1916.
- Italian Neo-Renaissance front of cream stucco round the great half-round glazed end of the shed, with a
  clock at its top.
- Two end pavilions with pediments and long two-storey arcaded wings.
- Behind it the barrel-vaulted shed of dark lattice steel with a skylight along its ridge.
- Names: `Krung Thep`, `Bangkok`, `Hua Lamphong`, `Bangkok Hua Lamphong`.

### Tokyo Station, Marunouchi side

`Landmarks.TOKYO` (`Landmarks.java:349-367`)

- Tatsuno Kingo, 1914.
- Red brick building 335 m long, three storeys with white stone bands over a granite base, under dark slate.
- Its middle block has a pediment over the entrance.
- Long wings run to both sides; each ends in a four-storey pavilion under a domed octagonal roof.
- The island platforms stand behind it, raised on a viaduct; here they stay at street level.
- The wings and pavilions follow the station's length.
- Names: `Tokyo`, `Tokyo Station`, `Tokyo Marunouchi`, `Tokyo Eki`.

### Amsterdam Centraal

`Landmarks.AMSTERDAM` (`Landmarks.java:369-399`)

- P. J. H. Cuypers and A. L. van Gendt, 1881-89.
- Long Neo-Renaissance front of dark red brick banded with pale sandstone.
- Its raised middle is a tall gable over three round-headed windows between two towers with steep pinnacled
  caps.
- The clock is on the east tower (on the right from the Stationsplein); a wind dial is on the west tower.
- Long two-storey wings run to both sides under steep slate roofs with rows of dormers, and end in gabled
  pavilions.
- The platforms and the arched sheds (45 m spans) stand behind it.
- Sources and photos named in the code: `ATA-research/stations/amsterdam`.
- The middle block stays the generic building, as at Tokyo, so the footbridge's lifts keep their shaft; the
  wings and pavilions follow the station's length.
- Names: `Amsterdam Centraal`, `Amsterdam Central`, `Amsterdam CS`, `Amsterdam Centraal Station`.

### Beijing Railway Station

`Landmarks.BEIJING` (`Landmarks.java:401-417`)

- Chen Deng'ao and others, 1959.
- Pale grey stone building 200 m wide.
- Tall middle hall under a clock tower.
- A tower at each end under a Chinese pavilion roof of blue-green glazed tiles.
- Long flat-roofed wings between.
- Names in this revision: `Beijing`, `Beijing Station`, `Beijing Railway Station`, `Beijing South`.

### Kuala Lumpur Railway Station

`Landmarks.KUALA_LUMPUR` (`Landmarks.java:419-438`)

- A. B. Hubback, 1910-17.
- Moorish white stucco.
- Long arcades of horseshoe arches.
- A skyline of onion domes, cupolas and slender minarets over a central block and both ends.
- Names: `Kuala Lumpur`, `Kuala Lumpur Railway Station`, `KL Railway Station`.

### Surbiton

`Landmarks.SURBITON` (`Landmarks.java:440-446`)

- J. R. Scott, 1937.
- White streamlined art deco.
- Long bands of windows.
- A slender glazed tower over the entrance.
- Name: `Surbiton`.

### Munich Hauptbahnhof

`Landmarks.MUNICH` (`Landmarks.java:463-478`)

- Terminus, 32 tracks.
- The 1950s concourse across the head of the tracks: a long low block of board-formed concrete and glass
  with a tall glazed middle over the entrance and a ribbon-windowed storey in the wings.
- Behind it the platform hall, a row of low glazed barrel vaults on concrete.
- Names: `Munich Hbf`, `Munchen Hbf`, `Muenchen Hbf`, `Munich Hauptbahnhof`.

### Berlin Hauptbahnhof

`Landmarks.BERLIN` (`Landmarks.java:480-492`)

- Through station, 2006.
- The east-west hall's glass front.
- A steel and blue glass concourse building with its full-height glazed middle.
- Two glass office bridges across the hall's end.
- A glass barrel behind.
- Names: `Berlin Hbf`, `Berlin Hauptbahnhof`.

### Coventry

`Landmarks.COVENTRY` (`Landmarks.java:494-504`)

- 1962, Lloyd and Headley.
- A long low block of blue engineering brick.
- A double-height glazed booking hall in the middle.
- A flat white canopy slab.
- Names: `Coventry`, `Coventry Station`.

## Materials recorded in the code

| Skin | Documented materials | Code source |
| --- | --- | --- |
| `GOTHIC_RED_BRICK` | Victorian red brick, pale stone dressings, grey slate roof | `Landmarks.java:26-29` |
| `ANTWERP_STONE` | grey stone, pale bands, dark slate roofs, blue slate domes | `Landmarks.java:31-34` |
| `BEAUX_ARTS_STONE` | Beaux-Arts limestone over grey granite, green copper | `Landmarks.java:36-39` |
| `FRANKFURT_SANDSTONE` | ochre sandstone, rusticated base, blue-grey slate, brown glass | `Landmarks.java:41-44` |
| `ZURICH_SANDSTONE` | pale grey-beige sandstone over grey stone, dark slate | `Landmarks.java:46-49` |
| `TOKYO_BRICK` | red brick, white stone bands, grey granite base, dark slate | `Landmarks.java:51-54` |
| `BEIJING_STONE` | pale grey stone, blue-green glazed tiles | `Landmarks.java:56-59` |
| `KL_WHITE` | Moorish white over a sandstone plinth | `Landmarks.java:61-64` |
| `SURBITON_WHITE` | white-rendered brick, flat roofs, blue-tinted glass | `Landmarks.java:66-69` |
| `PARIS_LIMESTONE` | cream Paris limestone, blue-grey slate mansards | `Landmarks.java:71-74` |
| `MILAN_STONE` | pale Botticino stone, grey stone base, flat stone roofs | `Landmarks.java:76-79` |
| `MUMBAI_SANDSTONE` | honey sandstone, pale limestone, blue-grey basalt, red sandstone bands, dark slate | `Landmarks.java:81-84` |
| `BANGKOK_STUCCO` | cream stucco, white trim, gold bands, red-brown tiles, blue-green glass | `Landmarks.java:86-89` |
| `AMSTERDAM_BRICK` | dark red brick, pale sandstone bands, sandstone plinth, dark slate | `Landmarks.java:91-94` |
| `MUNICH_CONCRETE` | board-formed concrete and curtain glass under a flat stone roof | `Landmarks.java:448-451` |
| `BERLIN_GLASS` | steel and blue glass | `Landmarks.java:453-456` |
| `COVENTRY_MODERNIST` | blue engineering brick, pale concrete, curtain glass | `Landmarks.java:458-461` |

## Gaps left by the reconstruction

- The original reference briefs and their published sources are absent. This file records only the facts
  that survive in `Landmarks.java`'s sheet comments and the relevant skin comments.
- The only explicit external path, `ATA-research/stations/amsterdam`, is absent from the tree.
- Some sheet details in the code are implementation choices rather than documented real facts. They are
  listed here only where the code comment states them, such as Tokyo's and Amsterdam's middle blocks staying
  generic so the footbridge lifts keep their shaft (`Landmarks.java:363`, `Landmarks.java:375-376`).
