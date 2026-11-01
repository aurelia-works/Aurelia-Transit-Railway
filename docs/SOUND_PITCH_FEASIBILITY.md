# Sound pitch: 0–16× feasibility

Owner request: allow in-game sounds generally to use a 0–16× pitch range, not just train sounds.

## Finding

Do not do 0–16× for all sounds. The current train-only range is the safe design.

- Minecraft clamps every `SoundInstance` in `SoundSystem.getAdjustedPitch` to 0.5–2.0.
- `TrainSoundPitchMixin` is a single HEAD injection in that method. It only lifts the clamp for the `mtr` namespace and
  only when the Train sound panel switch is on. `SoundTuning.pitch` clamps the request to 0.25–8.
- There is no supported way around the clamp without a mixin or replacing the sound engine. A mixin is still required;
  the current one is small, stable for 1.20.1, and already shipped.
- Pitch 0 is not a valid OpenAL pitch. It silences or errors instead of producing a useful sound.
- 16× is extreme: it aliases, shortens sounds to clicks, and adds resampling cost. Applying it to UI, mobs and every
  other mod would make ordinary sounds wrong.
- A global range also affects sounds ATR does not own, which is a compatibility problem, not just a taste problem.

## If a general range is still wanted

Use an opt-in control with a conservative range:

- 0.5–4× for all sounds, or
- 0.5–8× for an allowlist of namespaces/sound ids.

Keep the existing train range separate. Extend `TrainSoundPitchMixin` with a second config check rather than adding a
second mixin. Add a panel row and a unit test for the clamp; test UI, mob and train sounds at 0.5/2/4/8 in game.

## In-game test

- Toggle the train wide range and confirm BVE motors still sound as before.
- At 0.5, 2, 4 and 8, play a vanilla UI click, a mob sound and a train sound; check for silence, clicks or log errors.
- Confirm a sound from another mod is unchanged when the general option is off.

## Decision

No code change for 0–16×. The existing train-only range remains. A narrower opt-in general range can be scheduled as a
small feature if the owner wants it.
