# Phase R: rendering and performance

Goal: rails and trains no longer decide the frame rate when shaders are on. The headline case is Jah's: 67 fps outside,
34-37 fps in tunnels with shaders. MTR issue #1287 has the other shape of the same problem: four parallel rails take
100 fps down to 40.

## What changed

### 1. Separate rail and train view distances

MTR 4.0.5 stops drawing rails at `MinecraftClientHelper.getRenderDistance() * 16` and occlusion-culls trains at the
same distance. Two new client settings draw them farther:

| Setting | Default | Meaning |
| --- | --- | --- |
| `railViewChunks` | 0 (Minecraft's) | Draw rails out to this many chunks, up to 64. 0 means Minecraft's render distance; a smaller value shortens the normal rail pass too. |
| `trainViewChunks` | 0 (no limit) | Draw trains out to this many chunks, up to 64. 0 means no extra limit; a smaller value hides cars past it. |
| `farDetail` | on | Draw far rails and trains as simple shapes. Off hides them past the view distance instead. |

Past Minecraft's render distance:

- **Rails** are flat textured quads, one every 8 blocks, drawn as one scheduled callback per far rail. The quad keeps
  the width, texture and vertical offset of the 2D rail each transport mode falls back to: standard rails use the rail
  texture at full width, boats at half width, cable cars the metal texture at quarter width, and airport rails the iron
  block texture at quarter width. The colour follows the rail colour while a rail-related item is held, as the near
  rails do. No rail model is requested, pieces behind the camera are skipped, and the lightmap is read once per far
  piece (not once per 0.5-block piece), so the far ribbon is lit correctly at night without the near-pass cost. A piece
  outside the loaded chunks has no light data and uses the camera's light instead of going black.
- **Trains** are one plain box per car in the vehicle's colour, with slightly shaded roof and end faces so it reads as a
  car and not a flat silhouette. The car's model and interior are never requested. A far box is drawn even when
  occlusion culling did not reach it, because the culling distance stops at the render distance, and skipped when it is
  behind the camera because there is no occlusion result to rely on; near cars keep the existing occlusion behaviour.

The far rail pass starts `RailLod.FAR_VIEW_OVERLAP` (16) blocks before Minecraft's render distance, so the flat ribbon is
already under the detailed rail when the detail pass ends. That hides the detail switch and leaves no gap at the
boundary. Far rail pieces are sampled from the rail start, so they stay in the same place while the camera turns and do
not shimmer.

The client only knows what the server sends. `InitClient` now requests data out to
`max(Minecraft's render distance, railViewChunks, trainViewChunks) * 16` blocks; with both settings at 0 the request is
unchanged. In the core jar, `DataRequest.getData` stores that radius on the server-side `Client`, and both
`Vehicle.writeVehiclePositions` and `Rail.tick1` use `Client.getUpdateRadius()` to decide what to send to that client;
`Client.setPositionAndUpdateRadius` does not clamp it. So the larger radius is honoured for rails and vehicles, not just
for stations.
- **Rails** are not occlusion-culled past the render distance. A far rail can therefore show through a mountain. That is
  the price of not walking the world for occlusion at that distance; the quads are cheap and the setting is off by
  default.

No Distant Horizons or other LOD mod is required, and nothing checks for one. The far pass reads only MTR's own rail and
vehicle data, so installing DH or another LOD mod does not change or break it.

Near-distance visuals are unchanged: every far path starts strictly past Minecraft's render distance, and the existing
4.0.5 rail and train LOD tiers are untouched.

### 2. Rail drawing cost

- Removed two `Vector3d` allocations per rail piece in `RenderRails.renderWithinRenderDistance`. The distance check is
  now plain `double` maths. This loop runs for every piece of every visible rail, every frame.
- The far pass samples rails at 8-block intervals instead of MTR's default 0.5-block rail repeat, and batches all far
  rails into one callback.
- Far pieces are sampled every 8 blocks instead of every 0.5-block rail repeat, so the far ribbon needs roughly one
  lightmap lookup and one quad per 8 blocks rather than one per half block.
- Caching sampled rail geometry per rail was considered and left out: it needs invalidation on every rail edit and
  would be a larger correctness risk than the maths it saves. The far pass already removes the dominant far cost.

### 3. Block entity counter

A client mixin counts calls to `BlockEntityRenderDispatcher.render` per frame. This is dispatcher calls, not a proof
that every block entity reached the screen, and a shader pack's shadow pass can make it higher than the visible count.
`MTRClient` snapshots the counter at `WorldRenderEvents.END`, so the panel shows the last completed frame. The
RENDERING tab shows:

`Block entities drawn: 123 (warn at 200)`

The line turns red when the count is above the player's warning threshold (`blockEntityWarn`, default 200, 0 = off).
This is a diagnostic, not a limiter: nothing is skipped because of it.

### 4. Panel and config

New rows at the end of the RENDERING tab: rail view chunks, train view chunks, far-detail toggle, block entity warning
threshold, and the live counter line above the rows. All new settings live under new keys in `config/mtr.json`; old
configs load unchanged because missing keys take the schema defaults.

### 5. Tests

- `RailLodTest`: far-view boundary (past Minecraft's distance, inside the rail view distance, off, far detail off) and
  the AABB distance helpers.
- `TrainLodTest`: the view tier (off, hidden past the view distance, box past Minecraft's distance, full inside it,
  far detail off, riding always full).
- `DropInSignaturesTest`: unchanged MTR 4.0.5 method signatures.

## How to measure

Do not measure in a menu or with the window unfocused. Use the same world, position, time of day, render distance,
shader pack and shader settings for every run. Turn vsync off and do not cap the frame rate.

1. **Plain MTR 4.0.5 vs ATR.** Two instances or two game directories with the same world and options. The plain MTR
   jar is the one `DropInSignaturesTest` reads (`minecraft-transit-railway-FABRIC-4.0.5+1.20.1.jar`). Keep add-ons
   identical; ATR-only add-ons must be removed from the plain run or the comparison is not like for like.
2. **Shaders on and off.** Use the same shader pack and the same pack settings. Record the pack name and version.
3. **Stand still for 60 seconds** at each spot after the chunks have loaded. Read the F3 average, then repeat once. Do
   not compare a cold fly-through with a warm standstill.
4. **Scenarios:**
   - Outside a large station, shaders off.
   - Outside the same station, shaders on.
   - Inside a tunnel, shaders on (Jah's 34-37 fps case).
   - Four parallel rails in view, shaders on (MTR issue #1287 case).
   - Far rail view 16, 32 and 48 chunks, shaders on.
   - Train view 16, 32 and 48 chunks, shaders on.
5. **MTR's own numbers:** with `perfOnF3` on, F3 also shows MTR draws/frame and MTR CPU ms/frame. Record both next to
   the FPS; FPS alone hides whether the bottleneck moved to the GPU or to MTR's CPU work.
6. **Panel counter:** open the Aurelia panel's RENDERING tab at each spot and record the block entity count. Use it to
   explain a frame-rate change that is not about rails or trains.
7. **Visual check:** walk the rail view distance in and out at a station and in a tunnel. At the boundary the flat
   ribbon should already sit under the detailed rail, with the same width and texture. Check a curve for gaps, a
   platform and a siding for colour, and a train for livery colour and car separation. Then check the far edge: rails
   and cars should disappear at the same distance on every side, not leave a half-drawn piece.

Reference numbers from Jah (not produced by this phase): 67 fps outside, 34-37 fps in tunnels with shaders. Reference
from MTR issue #1287: four rails take 100 fps to 40. These are the shapes to reproduce, not measurements of this build.

## Results

Nothing in this table was measured by the agent that wrote this phase: no game client was launched. Fill the cells on
Jah's machine.

| Scenario | Plain MTR 4.0.5 | ATR before this phase | ATR after this phase | Notes |
| --- | --- | --- | --- | --- |
| Outside station, shaders off | not measured | not measured | not measured | |
| Outside station, shaders on | not measured | not measured | not measured | |
| Tunnel, shaders on | not measured | not measured | not measured | Jah's reference: 34-37 fps |
| Four parallel rails, shaders on | not measured | not measured | not measured | MTR #1287 reference: 100 to 40 fps |
| Far rail view 16 chunks | not measured | not measured | not measured | |
| Far rail view 32 chunks | not measured | not measured | not measured | |
| Far rail view 48 chunks | not measured | not measured | not measured | |
| Train view 16 chunks | not measured | not measured | not measured | |
| Train view 32 chunks | not measured | not measured | not measured | |
| Train view 48 chunks | not measured | not measured | not measured | |
| Block entities at station | not measured | not measured | not measured | Panel counter |

## Far-distance look (honest rating)

**7/10.** This is a code-inspection rating: no game client was launched, so nothing was seen in motion.

What is right:

- Correct livery colour per train car, and the correct rail texture, width and vertical offset per transport mode.
- Lit by the world lightmap, so far rails and boxes are not full-bright at night.
- A 16-block overlap at the detail boundary and stable sampling from the rail start: no boundary gap and no shimmer as
  the camera turns.
- One box per car with shaded roof and end faces, using the car's own length and width.

What keeps it below 9:

- 3D rail styles lose their silhouette. Catenary masts, custom slabs and other 3D rail models become the 2D rail ribbon
  past Minecraft's render distance. At 32+ chunks that is small, but at the render-distance boundary it is a visible
  shape change.
- Curves are chords every 8 blocks. Tight curves are visibly polygonal at distance; a shorter interval costs more.
- The far edge is a hard cut. Rails and cars pop out at the view distance; there is no fade.
- Far trains are featureless boxes. A box is the requested cheap shape, but it has no window band, no nose shape and no
  livery detail, so a close far box looks like a coloured block.
- Far boxes past the render distance are not occlusion-culled, so they can show through terrain.
- Existing train LOD transitions (full -> no interior -> box) are unchanged and can still pop at their own chunk
  boundaries.
- No in-game check of the look, so the rating is deliberately conservative.

## Risks and limits

- Far rails are not occlusion-culled and can be visible through terrain. Far trains past the render distance are drawn
  without occlusion culling for the same reason.
- Far train boxes need the vehicle resource for the car colour. If the resource has not loaded yet, the car is not drawn;
  the full model path has the same wait.
- The block entity counter is a mixin into a vanilla renderer. It is read-only, but a mod that also mixins into
  `BlockEntityRenderDispatcher.render` could conflict. There is no such mod in the current pack.
- The far pass adds work when a view distance is set. Both distances default to 0, so an existing config changes nothing
  until the player turns them on.
- A larger train view distance also makes the server send, and the client simulate, more vehicles. The box proxy cuts
  drawing cost, not simulation cost. A large value on a busy network is a trade the player makes.
- Not tested with Distant Horizons, Iris shadow passes, or a dedicated server. The far pass is skipped in a shader
  pack's shadow pass and does not touch server code.

## How to revert

- Runtime: set `railViewChunks` and `trainViewChunks` to 0, or turn `farDetail` off. This disables the new drawing paths
  without a code change.
- Code: revert the phase R commit. The new config keys are additive, so a reverted build still loads a config written by
  this build (unknown keys are ignored by the schema reader, and the old build uses its defaults).

## Not tested

- No Minecraft client, server or shader pack was launched. Every FPS cell above is "not measured".
- No visual check of far rails or boxes, including curve seams and the tunnel case.
- No Distant Horizons, Iris or OptiFine run.
- No multiplayer or dedicated-server run.
