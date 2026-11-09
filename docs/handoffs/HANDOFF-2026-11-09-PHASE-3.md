# HANDOFF 2026-11-09, phase 3 (H #68, plan hologram): done

## Done
- #68 merged as 26bbe93dc (squash). Sonnet verify-pr PASS on a7c096c; CI all 9 green.
- Rebased `feat/hologram-preview-2` on master (#61, #67). Conflicts were in the client init, ModPackets, StationActions and en_us.json; both sides kept.
- The in-game check found that the hologram never drew. The log said "Station hologram could not be meshed" (IllegalStateException): on 1.20.1, `BufferBuilder.quad` goes through the fast vertex path, which refuses a fixed colour. Fixed in a7c096c59 by setting the colour and alpha on each vertex.
- Shots: before `vis-h-hologram-side.png` (grass only); after `vis-h2-fixed-a/b`, `vis-h2-hologram-*`, `vis-h2-after-cancel.png` in `~/.t3/worktrees/mod/integrate-today/fabric/run/perf/screenshots/`.
- Worktree `t3-889e8cb6` and the branch deleted.

## Known, not fixed (reviewer nits)
- The client silently drops plans over 400k blocks (StationHologramRenderer MAX_BLOCKS).
- `StationHologram.show` returns silently when the player is in another dimension.
- The plan is compressed on the server thread (not measured on big plans).

## Local test note
- `:fabric:runGameTest` runs out of heap on this 8 GB Mac in AirportMtrRebuildGameTests (`RebuildDiff.of`), because the default heap is about 2 GB. CI passes it. Locally run `:fabric:test -x runGameTest` and let CI run the game tests.

## Next
Phase 4: RT (#59, `feat/route-control`, worktree `t3-9c7ee2a5`, branch `rt/trains-follow-2` = c42cfc693 with master merged, pushed to #59).
