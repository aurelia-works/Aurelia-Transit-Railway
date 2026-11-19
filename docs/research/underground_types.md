# Underground station types

> Reconstructed from code; original sources not in repo.

Rebuilt from `UndergroundType.java`, `PlanDeep.java`, `StationPresets.java` and the comments in
`DeepStationTest.java`. The original research file cited by `PlanDeep.java` and `UndergroundType.java` is not
in the tree, so every fact below is a code location first. Line numbers are for the reconstruction base
(`5f2167c`).

The underground presets use four designs. Depth, tiles and signs are filled in per station; the shapes come
from `PlanDeep` (`UndergroundType.java:3-6`, `PlanDeep.java:8-21`).

## The four types

### Shallow U-Bahn box

`UndergroundType.SHALLOW` (`UndergroundType.java:8-9`)

- A box just under the street.
- Stairs run from the platforms straight up to it.
- Example in the code: Berlin's Rosenthaler Platz, about 5 m down.
- Default depth is 0 because the shallow box has its own depth rules.

### Deep tube

`UndergroundType.DEEP_TUBE` (`UndergroundType.java:10-14`)

- A bored tube station.
- Twin round bores, a track and its platform in each.
- The platforms face each other across a one-column pillar of lining.
- Cross-passages run through the pillar.
- One long escalator shaft runs from the platforms' low end up to a ticket hall at the surface
  (`PlanDeep.java:13-15`).
- Examples: Angel and Westminster.
- Angel is 27 m down, with the longest escalators on the Underground (`PlanDeep.java:15`,
  `UndergroundType.java:11-13`).
- Default depth is 40 blocks (`UndergroundType.java:14`).

The plan's layout constants (`PlanDeep.java:24-38`):

| Constant | Value | Meaning |
| --- | --- | --- |
| `STEP_CLEAR` | 4 | void cells over an escalator step: the step, two to walk through and two clear |
| `HALL_DEPTH` | 14 | rows of ticket hall between the shaft's top landing and the hall's street wall |
| `FORECOURT` | 2 | rows of paved forecourt in front of the hall |
| `HALL_OVER_SHAFT` | 7 | rows of hall that overlap the shaft's last steps |
| `HALL_HEIGHT` | 5 | cells of hall above its floor |
| `JUNCTION` | 4 | platform rows open across the pillar at the shaft end |
| `CROSS_PITCH` | 14 | rows between cross-passages through the pillar |

`tubeAccessRows(depth)` gives the rows before a tube's platforms: the shaft (`depth + 1` rows of run and
landings), the hall and its forecourt (`PlanDeep.java:45-48`). The shaft has three escalators side by side,
two climbing and one running down (`PlanDeep.java:171-181`).

### Cut-and-cover box

`UndergroundType.CUT_AND_COVER` (`UndergroundType.java:15-19`)

- One deep rectangular box.
- A mezzanine at each end.
- Long escalator banks.
- A glass canopy over each street entrance.
- Examples: Canary Wharf and Vijzelgracht.
- Canary Wharf is 24 m down.
- Default depth is 36 blocks (`UndergroundType.java:19`).

Plan rules (`PlanDeep.java:50-71`, `PlanDeep.java:483-558`):

- `streetRise(depth)` is a third of the depth, clamped between 4 and 12 blocks.
- `mezzY(depth)` is the mezzanine floor: the street escalators land on it, the platform banks start from it.
- `boxEndRows(depth)` is the street bank's run and landing, the gate line, and the platform bank's top
  landing.
- `boxMinLength(depth)` is both platform banks plus some platform between them.
- `BANK_WIDTH` is 6 columns: three escalators of two columns each (`PlanDeep.java:483-484`).

### Stacked deep tube

`UndergroundType.DEEP_STACKED` (`UndergroundType.java:20-24`)

- A bored tube whose two platforms are at different heights.
- Westminster's deep Jubilee platforms are one above the other in a 39 m (58 block) box
  (`UndergroundType.java:21-22`).
- `StationGeometry.STACK_RISE` is the height between the two bores (`UndergroundType.java:22`).
- A bank of escalators and a three-stop lift join the levels (`UndergroundType.java:22-23`).
- Default depth is 58 blocks (`UndergroundType.java:24`).

`PlanDeep.raiseSecondBore(...)` moves the second bore up and across over the first, and moves the layout with
it (`PlanDeep.java:209-247`). `stackedBank(...)` runs two escalators along the platforms and a passage
through the pillar onto the upper platform (`PlanDeep.java:249-281`).

## Presets and the facts they carry

`StationPresets.java:83-96` and the comments at `StationPresets.java:83-86`.

| Preset | Type | Documented fact | Code source |
| --- | --- | --- | --- |
| Angel | deep tube | 27 m down; escalators only ("no ramps": no lift); TfL signs | `StationPresets.java:83-88`; `DeepStationTest.java:122` |
| Canary Wharf | cut-and-cover box | 24 m down; its real 265 m is capped at the generator's 300-block limit; TfL signs | `StationPresets.java:84-86`, `StationPresets.java:89-90` |
| Vijzelgracht | cut-and-cover box | 26 m down; 125 m island platform, built 188 blocks long; GVB signs | `StationPresets.java:84-86`, `StationPresets.java:91-92` |
| Westminster | stacked deep tube | deep Jubilee platforms in a 39 m (58 block) box; step-free; grey tiles; TfL signs | `StationPresets.java:93-96` |

Depth is written either as metres or as blocks. The parser converts metres at 1.5 blocks per metre
(`StationDescriptionParser.java:936-978`, `StationGeometry.BLOCKS_PER_METRE`).

Westminster's tile choice: the Jubilee platforms are bare concrete and steel; the green Holden tiles are on
the sub-surface platforms (`StationPresets.java:94`).

## Documented cuts and gaps

- Westminster's District/Circle sub-surface level is not built (`StationPresets.java:93`).
- The repository gives no depth, track/platform layout, length or orientation for that sub-surface level.
  `PlanDeep` has two bores or one box, not a second shallow level above a stacked pair, so adding it would
  be a larger change than filling in a preset.
- The original research file is absent. The facts above are only what the code comments and presets record.
