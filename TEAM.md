# TEAM.md — how this repo is built by agents

This file is the operating agreement for **ATR**, Aurelia Transit Railway (`aurelia-works/Aurelia-Transit-Railway`).
Every agent reads it before touching the repo. It does not replace `CLAUDE.md`, which holds the build commands and the
compatibility invariants; it says who does what and how work is handed over.

Repo facts the rules below depend on: ATR is a Fabric 1.20.1 mod built with Gradle (`./gradlew`). It is a drop-in
replacement for Minecraft Transit Railway 4.0.5 and keeps that mod's id `mtr`, so MTR add-ons and old worlds must keep
working. One Gradle module `fabric`, plus the separate `aureliamap/` Gradle build.
CI runs on every pull request and `ci-ok` is the only check that matters.

## Roles

**Lead + builder (Claude Code).** Writes `SPEC.md` for each feature, splits the work, builds the hardest part. Effort
high, not max. At most **2 builder threads at once**, each in its own git worktree (`git worktree add`), never two
agents in one checkout.

**Reviewer (OpenCode Go).** Read-only. Reviews diffs, reports bugs with a reproduction and a severity of 1–3, and
never edits code. A reviewer that cannot reproduce a problem does not file it.

**Docs (OpenCode Go).** Owns `README.md`, `CHANGELOG.md` and `lang/` translation files only. Nothing else.

**Dispatcher (Grok Bot).** Files tasks, each with a full BRIEF. Never merges, never deploys, never edits code.

## Rules

- Every handoff uses the BRIEF format below. A task without a DONE WHEN is not a task.
- A task is done only when its DONE WHEN check passes. For this repo that usually means
  `./gradlew setupFiles build -x test` succeeds **and** the game launches with no new errors in the log; a change with
  no game surface may instead name the affected test classes and `DropInSignaturesTest`.
- Max **3 review rounds**, then hand it to Jahmeer.
- **Only Jahmeer merges to `master`.** Merge commits, never squash.
- One task = one branch = one pull request. If the PR changes only Markdown, `docs/` or `archive/`, CI skips the build
  and test jobs by design.
- Never commit build output, jars, worlds, logs, `.env` files or secrets. Run `git status --short` before every commit.

### Never touch without asking Jahmeer

These change how everything else builds or loads, so a mistake here is not a normal review finding:

- `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/**` — build, dependency and Gradle version.
- `fabric/src/main/resources/fabric.mod.json` (version and dependency fields) and
  `fabric/src/perfbench/resources/fabric.mod.json` — what the mod declares it needs and provides.
- `fabric/src/main/resources/*.mixins.json` and `fabric/src/main/resources/mtr.accesswidener` — a bad entry crashes at
  launch, before any test runs.
- `.github/workflows/**` and `.github/actions/**` — CI; a bad change can block every pull request.
- `libs/*.jar` — the Transport Simulation Core the whole mod compiles against.
- `buildSrc/**` — schema generators; a change there silently regenerates source.
- `fabric/ata-test-times.properties` — the CI shard balance.
- `LICENSE`, `LICENSE-MTR.txt`, `NOTICE.md` — licensing.
- Any secrets, tokens or `.env` files.

## BRIEF format

```
BRIEF FOR:   <role: lead+builder | reviewer | docs | dispatcher>
ROLE:        <what the agent is allowed to do, and what it must not>
GOAL:        <the one outcome, in a sentence>
CONTEXT:     <branch, worktree, files, prior decisions, links to SPEC.md or the PR>
DONE WHEN:   <a check anyone can run or look at>
DO NOT:      <what is out of scope; the risky paths above if relevant>
RETURN:      <exactly what comes back: PR link, test result lines, files changed, open questions>
```

## Where things are

| File | What it holds |
|---|---|
| `CLAUDE.md` | build commands, compatibility invariants, test rules |
| `PLAN.md` | the plan to V1, phase list and status |

`HANDOFF.md` (current state only, replaced not appended) and `SPEC.md` (per-feature spec, written by the lead before
building) are written on the feature branch while work is open; they are not kept on `master`.
