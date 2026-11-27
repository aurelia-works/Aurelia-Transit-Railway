# HANDOFF 2026-11-09, phase 1 (AP #61 airport): done

## Done
- #61 merged as f5ca64a0d (squash). Sonnet verify-pr PASS on 8106d94; CI all 9 green in about 6 min.
- Branch `feat/airport-realism`, local `fix-61-timeout` and worktree `~/.t3/worktrees/mod/fix-61` deleted.
- The airport judge (Sonnet) ended at 8.0 overall in round ap28. Jah chose to merge at that level (2026-11-09) instead of looping to 9.
- CI fix: `WorldSiteProbe.under()` copied the rebuild site with `Map.copyOf`. Its linear probing on clustered `BlockPos` hashes made the in-place airport rebuild take 70 min on CI; it now takes 9 s. The game-test timeout is back to 45 min.

## Known, not fixed (follow-ups)
- The R in the AURELIA sign reads as F, because the glass mullions cut the letters (entrance pavilion panel).
- The kerb TAXI/BUS words are coarse at scale 1 (3x5 font).
- The compact airport has no pavilion (it keeps the one-row sign); not seen in game.
- The newer airport looks (pavilion, tower band, rooflights) have no dev-panel toggles.

## Next
Phase 2: W #67 (`feat/one-wrench-2`, worktree `~/.t3/worktrees/mod/t3-62a5f4dd`, head b48bf14d0).
Screenshot the ATR menu and the wrench (launch with `-Data.devScreenshots`; drop `open=menu` / `open=wrench` request files into `<run dir>/screenshot-requests/`), then Sonnet verify-pr, then merge.
