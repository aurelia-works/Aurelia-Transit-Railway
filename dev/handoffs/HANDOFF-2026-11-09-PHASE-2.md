# HANDOFF 2026-11-09, phase 2 (W #67, one wrench and one menu): done

## Done
- #67 merged as 8fc27bed0 (squash). Sonnet verify-pr PASS on b1054bc; CI all 9 green.
- Before the merge: master (with #61) merged in cleanly. The in-game check found that all four wrench mode tabs read "Station". Fixed in b1054bc31 (`StationWrenchScreen.initModes` used the open mode's label for every tab).
- Screens seen in game: the ATR menu with and without the wrench held, and the wrench in Station, Airport, Bridge and Earthworks modes. Shots are in `~/.t3/worktrees/mod/integrate-today/fabric/run/perf/screenshots/w-*.png` and `w3-*.png`.
- Branch `feat/one-wrench-2` and worktree `t3-62a5f4dd` deleted. The older `t3/one-wrench` worktree (t3-55c92a07) is left alone.

## How the shots were taken (reusable)
- Link `fabric/run` -> `~/.t3/worktrees/mod/integrate-today/fabric/run` in the worktree (untracked; remove before cleanup).
- `JAVA_TOOL_OPTIONS="-Xmx3000m -Data.devScreenshots=true -Datr.visualshots=$P/vis-w2.txt -Datr.visualshots.watch=$P/shots-queue-w" ./gradlew :fabric:runPerfbench -Patr.world="ATR Visual"`
- Hold a mode: a queue script with `cmd item replace entity @s weapon.mainhand with aurelia_transit_architecture:station_wrench{WrenchMode:"airport"}`. Then drop `open=wrench wait=60` into `$P/screenshot-requests/<name>`. To quit, put `quit` in the queue.

## Follow-ups (cosmetic)
- In the ATR menu the Dev tab wraps onto a second row, and the subtitle sits right under it (AtrMenuScreen).
- The "Kuala Lumpur" look button label is clipped (this was there before #67).

## Next
Phase 3: H, `feat/hologram-preview-2` (worktree `~/.t3/worktrees/mod/t3-889e8cb6`, 2b674a12e). Rebase on master, check it in game, open a PR, run verify-pr, merge.
