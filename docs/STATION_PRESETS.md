# Station preset realism pass

Goal: bring the shipped station presets to at least a 7/10 realistic quality bar without weakening the checks or
inventing facilities the generator cannot build.

## What changed

Commit `9cc19b548` on `feat/batch-1-6`:

| Preset | Change | Measured result |
|---|---|---|
| `teddington` | side platforms `6 wide` | 2 m platforms are now 6 blocks; 43 lift blocks (was 19), 56 stair blocks, 6 name signs; audit clean |
| `modern` | add `escalators`, name it "Riverside" | 6 name signs (was 0), 160 escalator blocks, 67 lift blocks; audit clean |
| `frankford` | drop the dead `escalators` word; `glass canopy` | 7,166 blocks; at-grade terminus, no lift/escalator needed; audit clean |
| `sixty_ninth_street` | drop the dead `escalators` word; `glass canopy` | 14,320 blocks; 38 lift blocks, 6 name signs; audit clean |
| `rosenthaler_platz` | add `escalators` | 40 escalator blocks, 24 lift blocks, 3 name signs; audit clean |
| `hong_kong` | add `glass hall roof` | 58,335 blocks; roof is a hall instead of four isolated canopies; audit clean |

`StationPresetsTest` now also requires every preset to have a non-empty name, at least one name sign, and an escalator
block whenever its description asks for escalators. This catches the old unnamed Modern preset and the two terminate
presets that asked for escalators but built none.

## Studio determinism

`applyPreset` in `studio.js` now clears every control the preset does not carry before generating:

- `class`, `era`, `facing`, `operator`
- the advanced platform list (`platforms`, `listOn`, `plats`)
- `bed`, `esc`, `walls`, `trav`, `gates`, `door`, `swaps`
- seed locks and the planner seed (`seeds.locks = {}`, `seeds.detail = ""`)

Before this, clicking a preset could inherit a stale travelator choice, escalator setting, platform list or planner seed,
so the same preset could build different stations. The preset's own words are now authoritative.

## Independent review

Reviewed by `opencode-go/muse-spark-1.3-contributor#low` on commit `9cc19b548`.

**Verdict: PASS, overall 7.6/10; every changed preset at least 7.0.**

| Preset | Score |
|---|---|
| teddington | 8.5 |
| modern | 7.5 |
| frankford | 7.0 |
| sixty_ninth_street | 7.5 |
| rosenthaler_platz | 8.0 |
| hong_kong | 7.0 |

The reviewer measured plans with the real parser/planner/audit and checked role counts for lifts, escalators and name
signs. No cosmetic-only edits passed.

## Reference images

These are reference material only; no third-party images are shipped in the mod. Licences are on each file page.

- Teddington: [Wikimedia Commons category](https://commons.wikimedia.org/wiki/Category:Teddington_railway_station) and
  [Wikipedia](https://en.wikipedia.org/wiki/Teddington_railway_station) (two platforms joined by a footbridge).
- Frankford: [Wikipedia](https://en.wikipedia.org/wiki/Frankford_Transit_Center) (at-grade SEPTA terminus with an island
  platform and a canopy).
- 69th Street: [Wikimedia Commons category](https://commons.wikimedia.org/wiki/Category:69th_Street_Transportation_Center)
  and [Wikipedia](https://en.wikipedia.org/wiki/69th_Street_Transit_Center) (island and side platforms in front of the
  brick hall).
- Rosenthaler Platz: [Wikimedia Commons file](https://commons.wikimedia.org/wiki/File:Ubahn-Rosenthalerplatz.JPG) and
  [Wikipedia](https://en.wikipedia.org/wiki/Rosenthaler_Platz_(Berlin_U-Bahn)) (U8 island platform, stairs and escalators).
- West Kowloon: [Wikimedia Commons ceiling category](https://commons.wikimedia.org/wiki/Category:Ceiling_of_Hong_Kong_West_Kowloon_Station)
  (the large covered hall).
- Modern/Riverside is a style preset, not a claim about one real station.

## Residual issues

- `frankford` is at grade with no lift or escalator. That is defensible for a ground-level terminus, but it is the
  weakest access story of the six.
- `hong_kong` resolves its hall to a flat shed. It reads better than four isolated canopies; matching West Kowloon's
  curved roof needs a shed shape the generator does not have.
- No client screenshots were taken for this pass. The evidence is measured plans plus the audit, realism and passenger
  walk tests.

## Tests run

```
./gradlew :fabric:ataTest --offline --tests 'com.aureliatransit.architecture.station.StationPresetsTest'
./gradlew :fabric:ataTest --offline --tests 'com.aureliatransit.architecture.station.StationRealismTest'
./gradlew :fabric:ataTest --offline --tests 'com.aureliatransit.architecture.station.PassengerWalkTest'
```

All three passed.

## Realistic track counts and single-level notes (`feat/realistic-track-limits`)

With `StationSpec.MAX_TRACKS` raised to `32` (`PlatformSpec.MAX_PLATFORMS = 16`), each major station preset builds its
published platformed track count from the `ATA-research/stations/*/notes.md` briefs, with explicit single-level and
plain-word island approximations where the generator models one railway level:

| Preset | Built tracks & layout | Real-world basis (`ATA-research`) |
|---|---|---|
| `paris` (`Paris Nord`) | 32 tracks, 16 islands (terminus) | SNCF Gare du Nord's 32 surface mainline and Transilien suburban platform tracks under the train shed (level -3 RER B/D platforms omitted). |
| `grand_central` (`Grand Central`) | 30 tracks, 15 islands (terminus) | The 30 platform tracks of Grand Central Terminal's upper intercity/Metro-North level (of 44 platform tracks across upper and lower levels), modeled on one terminus level. |
| `frankfurt` (`Frankfurt Hbf`) | 24 tracks, 12 islands (terminus) | The 24 surface tracks (`Gleis 1–24`) across the Querbahnsteig under the five platform halls; outer short siding `1a` and underground S-/U-Bahn levels are omitted. |
| `milan` (`Milano Centrale`) | 24 tracks, 12 islands (terminus) | All 24 stub-end tracks (`binari tronchi 1–24`) under Alberto Fava's five-vault train shed. |
| `beijing` (`Beijing South`) | 24 tracks, 12 islands (through) | All 24 tracks / 24 platform faces; `twelve island platforms` is the plain-word approximation for 11 island + 2 side platforms since even track counts pair cleanly into islands. |
| `tokyo` (`Tokyo`) | 20 tracks, 10 islands (through) | All 10 elevated island platforms / 20 tracks of the main Marunouchi–Yaesu surface/viaduct station (deep underground Sobu and Keiyo levels omitted). |
| `mumbai` (`Mumbai CSMT`) | 18 tracks, 9 islands (terminus) | Represents all 18 terminus platforms (11 long-distance + 7 suburban EMU) together within a single-level terminus hall. |
| `utrecht` (`Utrecht Centraal`) | 16 tracks, 8 islands (through) | All 8 island platforms / 16 platform tracks beneath Benthem Crouwel's wave-roofed concourse hall. |
| `zurich` (`Zurich HB`) | 16 tracks, 8 islands (terminus) | All 16 surface Kopfbahnhof tracks (`Gleis 3–18`) in the Gleishalle; `eight island platforms` approximates 7 middle + 2 side platforms (16 faces; underground through levels omitted). |
| `st_pancras` (`St Pancras`) | 15 tracks, island & side (terminus) | All 15 platforms of the restored station (13 terminus platforms in Barlow's shed and its 2007 extension + 2 Thameslink platforms, combined on one level). |
| `hong_kong` (`West Kowloon`) | 15 tracks, island & side (terminus) | All 15 designed tracks of Hong Kong West Kowloon's B4 platform level, modeled at grade under a glass hall roof. |
| `antwerp` (`Antwerpen Centraal`) | 14 tracks, 7 islands (terminus) | All 14 platform tracks modeled on one terminus level under Van Bogaert's shed (the real station stacks 6 dead-end + 4 dead-end + 4 through tunnel tracks across levels +1, -1, and -2). |
| `bangkok` (`Hua Lamphong`) | 14 tracks, 7 islands (terminus) | Matches Bangkok Hua Lamphong's 14-track / 14-platform 1916 arched terminus hall (`BuildingStyle.BANGKOK` and `Landmarks.BANGKOK` model Tamagno's Hua Lamphong rather than the multi-level 26-platform / 38-track Krung Thep Aphiwat hub). |
| `munich` (`Munchen Ost`) | 12 tracks, 6 islands (through) | München Ost's 12 platform tracks on 6 island platforms (unplatformed middle through tracks 9–10 omitted). |
| `amsterdam` (`Amsterdam Centraal`) | 11 tracks, island & side (through) | Amsterdam Centraal's 11 platformed tracks (of 15 total tracks, omitting unplatformed middle release tracks 3, 6, 9, and 12). |
| `hamburg_hbf` (`Hamburg Hauptbahnhof`) | 10 tracks, 5 islands (through) | Hamburg Hbf's 5 island platforms / 10 platformed tracks under the 3-span shed (unplatformed tracks 9–10 and the S-Bahn tunnel omitted). |
| `berlin` (`Berlin Hbf`) | 6 tracks, 3 islands (through) | The 6 east-west Stadtbahn tracks on 3 island platforms under the upper glass barrel hall (lower north-south tunnel level omitted). |
