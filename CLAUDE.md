# Aurelia Transit Railway

A fork of Minecraft Transit Railway 4.0.5 for Fabric 1.20.1. The mod id stays `mtr`, so MTR add-ons and worlds must keep working.

- Build: `./gradlew setupFiles build -x test`. The jar lands in `build/release/`.
- Unit tests: `./gradlew setupFiles :fabric:test -x runGameTest`.
- `DropInSignaturesTest` guards the MTR 4.0.5 methods add-ons hook. Do not rename or reshape those methods, and do not add lambdas to classes it checks, because that renumbers the existing ones.
- Old worlds and old configs must load unchanged. New saved data goes under new keys or in new files.

## Rule 1: tests

From David Andre's video (13:05): most tests an agent writes are written only so they pass. They bloat the code, slow CI down and test nothing.

- Do not write tests unless they are truly needed. Never write a test just to get a green run.
- Write a test only when the same kind of bug has happened before, or to guard something add-ons or saved worlds depend on. One or two targeted tests, not a suite.
- No tests for one-off edge cases that will never happen in a real game.
- No slow tests: no real-time waits and no long sweeps. If a test needs minutes, it needs a reason.
- Never skip, disable or weaken a test to make CI pass. Fix the code.

## Rule 2: subagents

From the same video (22:33): subagents only make sense for work that is clear, parallel, and split into chunks that are all the same.

- Use subagents only when the input, the steps and the output format are known up front and identical for every chunk, such as reading many files of the same kind.
- Do not use them for bug fixes, new features, refactors, or anything done here for the first time. Those have surprises and need one agent that sees the whole thing.
- If it is not clear that subagents fit, the answer is no.
- This account is on the $20 plan. Running agents in parallel spends the limit several times faster to finish slightly sooner, so the default is a single agent.

## How to work

- One session, one task: one bug, one feature, or one question.
- Bugs: reproduce first (a failing test that already exists, a game test, or exact steps), then fix.
- Review findings: rate each by how often it happens and how bad it is. Fix what is both likely and harmful. Skip rare, made-up scenarios. For each finding, ask "is this a real must-fix, or overthinking?"
- Do not run the same review over and over. A reviewer told to find bugs will always find something.
- Keep changes small and in the style of the code around them.
