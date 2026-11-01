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

## Residual issues

- `frankford` is at grade with no lift or escalator. That is defensible for a ground-level terminus, but it is the
  weakest access story of the six.
- `hong_kong` resolves its hall to a flat shed. It reads better than four isolated canopies; matching West Kowloon's
  curved roof needs a shed shape the generator does not have.
- No client screenshots were taken for this pass. The evidence is measured plans plus the audit, realism and passenger
  walk tests.
- The Studio "Build it" button still confirms the previous pending plan instead of binding to the previewed plan; that
  is a separate Studio UX bug and is not fixed here.

## Tests run

```
./gradlew :fabric:ataTest --offline --tests 'com.aureliatransit.architecture.station.StationPresetsTest'
./gradlew :fabric:ataTest --offline --tests 'com.aureliatransit.architecture.station.StationRealismTest'
./gradlew :fabric:ataTest --offline --tests 'com.aureliatransit.architecture.station.PassengerWalkTest'
```

All three passed.
