# Aurelia Transit Railway

A fork of Minecraft Transit Railway 4.0.5 for Fabric 1.20.1. The mod id stays `mtr`, so MTR add-ons and worlds must keep working. The plan and what is open: `PLAN.md`.

## Commands
- Build: `./gradlew setupFiles build -x test`. The jar lands in `build/release/`.
- Compile only (use this while working): `./gradlew setupFiles :fabric:compileJava --offline`.
- MTR unit tests: `./gradlew setupFiles :fabric:test -x runGameTest`.
- Generator tests (ATA): `./gradlew setupFiles :fabric:ataTest --offline --tests '*ClassName'`.
- Game tests run in CI only. Locally they run out of memory, and `:fabric:test` starts them unless the command has `-x runGameTest`.
- Dependency versions are pinned in `gradle.properties`. A newer Fabric Loader or Fabric API is a one-line change there.

## Compatibility
- `DropInSignaturesTest` guards the MTR 4.0.5 methods add-ons hook. Do not rename or reshape those methods, and do not add lambdas to the classes it checks, because that renumbers the existing ones.
- Old worlds, saved data and configs must load unchanged. New saved data goes under new keys or in new files.
- Block and item ids never change. Old items that were replaced stay registered.

## Rule 1: tests
- A new test is allowed when it guards a real failure: a bug that came back, or add-on or saved-world compatibility. It also has to be worth its CI time: no real-time waits and no long sweeps without a reason.
- Never write a test only to get a green run or to cover code you just wrote.
- Never skip, disable or weaken a test to make CI pass. Fix the code.
- Run the affected test classes once when the change is done. CI runs the full suites.

## Rule 2: subagents
- One agent per bug, feature or refactor: those have surprises and need one agent that sees the whole thing.
- Subagents fit only work that is the same shape for every chunk, with the input, steps and output known up front. The `verify-pr` reviewer is the standing exception.
- Agents in parallel spend the usage limit several times faster, so the default is one.

## CI
- A pull request that changes only Markdown, `docs/` or `archive/` skips the build and test jobs. Anything else runs everything.
- `ci-ok` is the one check that says a run is good. It fails when a job failed, was cancelled, or was skipped on a code change.
- `evidence` wants four headings in the PR body: What changed, Testing, Risk, How to revert.

## How to work
- One session, one task. One phase or fix per branch and PR; merge commits, never squash.
- Bugs: reproduce first (a failing test that already exists, a game test, or exact steps), then fix.
- Anything visible is judged from same-camera before and after shots, not from passing tests. Station Studio (offline) is the quick way to get them.
- Review findings: fix what is both likely and harmful. Skip rare, made-up scenarios, and do not run the same review again and again.
- Keep changes small and in the style of the code around them.
