# B1 custom bridge generator

Status: first slice implemented. Capture, store, preview, build and undo work from `/atr_bridge`. The simple
`ItemBridgeCreator` items still build the old block deck and are unchanged.

## Decision

A bridge template is a captured **section**, not a single block and not a vanilla structure template.

- A section is a box of block states stored relative to an origin block. The origin is normally the rail block at the
  section's centre and level, so the template knows where the rail runs through it.
- Capture stores the section facing north. Stamping rotates the positions and the block states by the rail's quarter
  turn, then applies the requested height.
- The format is JSON under `config/aurelia/bridges`, not a vanilla `StructureTemplate`. A structure template carries
  block entity data the generator does not want, cannot skip MTR rail nodes while stamping, and only rotates in
  90-degree steps through its own placement code. A JSON palette is also readable and easy to copy between worlds.

## Commands

Operators only (permission level 2).

```
/atr_bridge capture <name> <from> <to> <origin> [north|east|south|west]
/atr_bridge preview <name> <rail node> <rail node> [height]
/atr_bridge build   <name> <rail node> <rail node> [height]
/atr_bridge undo
/atr_bridge list
/atr_bridge delete <name>
```

`from` and `to` are two opposite corners of the captured box. `origin` must be inside the box; it is the block the
template's `(0, 0, 0)` maps to when stamped. The optional facing says which way the captured section faces; without it
the player's horizontal facing is used. Build and preview name the rail by two of its MTR rail nodes, the same two
clicks the Bridge Creator takes. `height` offsets the whole section up or down from the rail's block level.

Typical use:

1. Build one good bridge section around a rail node, normally a cross-section one block deep.
2. Capture it with the rail block as the origin.
3. Preview it along the real rail.
4. Build it, check the result, and use `/atr_bridge undo` if it is wrong.

## What capture stores

- Every non-air block state in the box, with its properties sorted into one palette string, for example
  `minecraft:oak_stairs[facing=east,half=top]`.
- Nothing else. MTR rail nodes are skipped because they already exist along the rail; fluids are skipped; blocks with
  block entities are skipped because their state alone would lose the data.

A box is limited to 32,768 blocks and 128 blocks on each axis. A build is limited to 200,000 distinct placements.

## Build and undo

- The rail is sampled every block. Each sample's tangent picks the nearest 90-degree turn; blocks the same position
  reaches twice keep the first placement.
- Work runs in 10 ms slices per server tick, like the station builder, so a long bridge does not freeze a tick.
- A rail node or block entity already at a target position is left alone and counted in the finish message.
- Undo puts cells back in reverse order, and only while the cell still holds the state the build wrote there. The undo
  log is the last build only and is in memory, so a server restart clears it. It records the build's dimension, so a
  player must return to that dimension to undo it.

## Limitations and next steps

- **90-degree turns only.** Vanilla block states cannot rotate by 45 or 22.5 degrees. A curved rail therefore steps
  between quarter turns. Supporting finer angles needs either model-based sections or a block set with more facings.
- **Cross-sections work best.** A template with depth along the rail is stamped at every sample and overlaps itself.
  That is useful for a short portal or a one-off section, but a one-block-deep cross-section is the intended tile.
- **No supports every N blocks.** A repeated cross-section repeats its piers too. A separate "support interval"
  or a template with removable support cells is the next design question.
- **Creative/operator tooling.** There is no survival material cost and no two-corner item yet. The command is the
  first capture UX; a structure-block or two-corner item can be added once the template format settles.
- **No chunk streaming window.** A build reads and writes cells directly, so a very long bridge can make the server
  load chunks. The station builder's bounded site probe is the model to reuse if this becomes a problem.
- **Undo is not persisted.** Saving it with the world, like the station builder's log, is a later step.
- **Block entities are not captured.** Signs, chests, ticket machines and similar blocks need a separate per-position
  data format before they can be part of a template.

## Tests

- `BridgeTemplateTest` checks rotation, the capture frame inverse and JSON round-trip.
- `BridgeStampPlanTest` checks rail sampling, height and quarter-turn choice.
- `BridgeStateCodecTest` (ATA tests, inside Fabric Loader) checks block-state property round-trip and rotation.
- `BridgeGameTests` (headless server game test) captures a section, saves/loads it, samples a real MTR rail, places the
  blocks and undoes them in a world.

```
./gradlew :fabric:test -x runGameTest --offline
./gradlew :fabric:ataTest --offline --tests 'com.aureliatransit.architecture.bridge.world.BridgeStateCodecTest'
./gradlew :fabric:runGameTest -Pata.gametests=BridgeGameTests --offline
```
