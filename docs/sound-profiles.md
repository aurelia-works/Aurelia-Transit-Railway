# Sound profiles

A sound profile gives any train a recognisable voice: the shape of the motor or engine whine with speed, the door
chime, the door open and close sounds, the brake release, the compressor and the flange squeal. Profiles live in resource
packs, not in the world, so a pack can add one for its own trains and another pack's train can use it too.

A train with no profile sounds exactly as it did before this feature existed. Old worlds and `config/mtr.json` are not
touched: the choice is this client's, in a new file.

## Choosing a profile in game

Open the Aurelia panel, then the **Train sound** tab.

- **Sound profile** cycles through `None` and every loaded profile for the train you are riding, or the nearest train
  when you are not riding one.
- The six volume sliders below it set that train's motor, door, chime, brake, compressor and flange levels. The last two
  rows are the older global pitch and flange controls and still work as before.
- The choice and volumes are written to `config/aurelia/sound-profiles.json`, keyed by the train's MTR vehicle id:

```json
{
  "use_train_defaults": true,
  "vehicles": {
    "123456789": {
      "profile": "mtr:uk_emu",
      "volumes": {"motor": 0.9, "door": 0.8, "chime": 1.0, "brake": 0.8, "compressor": 0.5, "flange": 0.7}
    }
  }
}
```

`use_train_defaults` is the panel's **Use train defaults** switch, on for everyone until they turn it off. Deleting the file or a train's entry puts that train back on its own sound. If a profile id in the file no longer
exists, the train also keeps its own sound; nothing breaks.

## Built-in profiles

These ship in `assets/mtr/aurelia_sound_profiles/` and appear in the picker as soon as the game loads.

| Id | Name | Motor / engine | Doors and chime | Air |
| --- | --- | --- | --- | --- |
| `mtr:uk_emu` | UK EMU | Smooth AC traction whine with a gear-mesh tone that rises to a soft top end | Light two-tone chime, air-assisted doors, short seal thud | Single brake-release hiss |
| `mtr:tube` | Tube | Coarse DC motor with strong odd harmonics and blower noise | Three descending bell notes, heavy doors with a double clack | Long, loud release |
| `mtr:dmu` | DMU | Diesel firing pulses with turbo whistle and mechanical clatter; keeps idling when stopped | Warmer two-tone chime, heavier slam | Short, sharp release |
| `mtr:dutch_emu` | Dutch double-deck EMU | Bright, smooth inverter whine with a pronounced high gear tone | Two-tone descending chime, quick air doors | Mid-length release |
| `mtr:metro` | Metro | VVVF singing tone with strong inverter harmonics and a step-shaped acceleration | Three ascending bell notes, fast doors | Short, clean release |
| `mtr:tram` | Tram | Low gear whine and motor hum over track rumble | High two-note ping, light doors | Light release |

They are synthesised, not sampled. The exact notes and the honest realism ratings are in the phase PR.

## Adding a profile to a pack

Put a JSON file at:

```
assets/<namespace>/aurelia_sound_profiles/<name>.json
```

The profile id is `<namespace>:<name>`, for example `assets/mypack/aurelia_sound_profiles/regional_emu.json` becomes
`mypack:regional_emu`. Pack authors can use any loaded sound event id, including their own sounds and the game's. A pack
can override a built-in profile by using the same namespace and file name; the highest pack in the resource stack wins.

Minimal example:

```json
{
  "name": "Regional EMU",
  "motor": {
    "sounds": ["mypack:regional/motor"],
    "volume": 0.9,
    "pitch_curve": [
      {"speed": 0, "pitch": 0.8},
      {"speed": 80, "pitch": 1.2},
      {"speed": 160, "pitch": 1.5}
    ],
    "volume_curve": [
      {"speed": 0, "volume": 0.15},
      {"speed": 20, "volume": 0.8},
      {"speed": 160, "volume": 1.0}
    ]
  },
  "door_chime": "mypack:regional/chime",
  "door_open": "mypack:regional/door_open",
  "door_close": "mypack:regional/door_close",
  "brake_release": "mypack:regional/brake_release",
  "compressor_loop": "mypack:regional/compressor",
  "compressor_attack": "mypack:regional/compressor_attack",
  "compressor_release": "mypack:regional/compressor_release",
  "flange": ["mypack:regional/flange"]
}
```

Every field except `name` is optional. A missing field leaves that part of the train's own sound alone, so a profile can
change only the door chime if that is all a pack has.

### `motor`

| Key | Meaning |
| --- | --- |
| `sounds` | One or more looping motor or engine clips, layered together. Empty or missing keeps the train's own BVE or legacy motor. |
| `always` | `true` keeps the loop audible when stopped, for a diesel idle. Electric profiles leave this out or set it to `false`. |
| `volume` | Multiplier on the train's motor volume, 0-2. Default 1. |
| `pitch_curve` (alias `pitch`) | Points `{"speed": km/h, "pitch": multiplier}`. Interpolated and clamped at both ends. Playback pitch, so 1 is the recorded or generated pitch. |
| `volume_curve` | Points `{"speed": km/h, "volume": 0-1}`. Interpolated and clamped. Missing means a flat 1. |

The `pitch_curve` is the important part of a train's character: a tram rises quickly and tops out low, a main-line EMU
rises steadily over a much wider speed range, and a diesel barely changes pitch at all.

### `motor_layers`

An optional array of extra looping sounds that play beside `motor`, each an object with the same keys as `motor`
(`sounds`, `volume`, `always`, `pitch_curve`, `volume_curve`). A layer without a `pitch_curve` keeps its recorded pitch
at every speed, which suits an inverter or fan hiss that should not rise with the traction notes:

```json
"motor_layers": [
  {
    "sounds": ["mypack:regional/inverter_hiss"],
    "volume_curve": [{"speed": 0, "volume": 0}, {"speed": 8, "volume": 0.8}, {"speed": 40, "volume": 1.0}]
  }
]
```

Layers follow the motor's power like `motor` does (unless `always` is set) and the train's motor volume slider. A layer
without sounds is skipped. Profiles without `motor_layers` sound as before.

### One-shot sounds

| Key | When it plays |
| --- | --- |
| `door_chime` | Once per car as the doors leave fully open. The per-train `chime` volume and MTR's global door chime volume both scale it. |
| `door_open` / `door_close` | When the doors start opening or closing. |
| `brake_release` | When the brake handle returns to release (BVE trains), or when a legacy train stops braking. |
| `compressor_loop` (alias `compressor`) | Looping sound while the main reservoir compressor runs. |
| `compressor_attack` / `compressor_release` | When the compressor starts or stops. |
| `flange` | One or more looping clips for curve squeal. The first/slot rules are the same as BVE flange arrays. |

Sound ids must be registered in a `sounds.json` in the same resource pack. Sound files are normal mono OGG Vorbis. A
looping motor or compressor clip works best at 6-8 s: a 2 s loop is easy to pick out on a long ride. Keep one-shots
short and the whole set small.

The generator in this repository makes long motor and compressor loops with slow load wobble and a slight pitch drift.
Every loop is rendered with an extra tail, crossfaded back into its head, rotated to a quiet Vorbis block boundary and
checked for a click-free wrap. Run the check after regenerating or after changing a loop:

```
python3 tools/sounds/generate_profile_sounds.py --check
```

It decodes every file whose name contains `motor`, `compressor` or `flange` and fails when the wrap step is more than
1.2 times the clip's own 99th-percentile sample step. The same table is printed after a normal generation run.

## Train defaults from the pack

A vehicle entry in `mtr_custom_resources.json` can name the profile its trains use before anyone picks one, with a tag:

```json
{
  "id": "my_pack_city_train",
  "name": "City Train",
  "tags": ["aurelia_sound:mtr:uk_emu"]
}
```

The tag is `aurelia_sound:` followed by the profile id. It is a plain tag, not a new MTR field, and the extra colon
keeps MTR's own tag filter ignoring it, so MTR and old packs load as before. The last matching tag wins; a blank
suffix (`aurelia_sound:` alone) is ignored, and so is a tag on a train whose cars have different types beyond the
first car (the first car's type decides).

Precedence for each train:

1. Your pick in `config/aurelia/sound-profiles.json` always wins, even when it names a profile whose pack is gone
   (then the train keeps its own sound; the tag is not used as a fallback).
2. With no pick and **Use train defaults** on (the default), the tag's profile plays. The panel's Train sound tab
   shows it as `Default from train: <name>`.
3. Otherwise the train keeps its own sound, exactly as before this feature existed.

An unknown id in the tag is ignored with a warning in `logs/latest.log` (`Unknown sound profile in aurelia_sound
tag`), so a missing pack never silences a train. Turning **Use train defaults** off puts every train with no pick
back on its own sound; the volume sliders still scale whichever profile plays.

## How profiles apply

- BVE trains (a `sound.cfg` base) receive every field: motor/engine replacement or pitch shaping, doors, chime, brake,
  compressor and flange.
- Legacy trains (the old `_acceleration_`/`_door_open` sound sets) receive the motor/engine, doors, chime and brake
  release. Their compressor and flange stay as before, because those sounds are not part of the legacy format.
- A profile never changes the train's model, save data or MTR ids.

## Generated audio and licensing

ATR's six built-in profiles use sounds generated by `tools/sounds/generate_profile_sounds.py`; every generated file is
listed with its licence in `fabric/src/main/resources/assets/aurelia/sounds/CREDITS.md`. The set is CC0.

If you add a recording to a profile, its licence must be CC0 or CC-BY, verified on the source page, and the file must be
listed in that CREDITS file (file, author, source URL, licence). Unverified candidates belong here, not in the jar:

- No candidate recordings are used by this phase. The profiles rely on synthesis so no unverified audio ships.

## Testing a profile

1. Put the pack in `resourcepacks/` and enable it, then reload resource packs (F3+T); profiles are read on every
   resource reload.
2. Open the panel's Train sound tab while riding or next to a train and cycle to the profile.
3. If nothing changes, check `logs/latest.log` for `[ATR] Skipping sound profile <id>` (bad JSON) or `Loaded N sound
   profiles` (count should include yours). A profile with an unknown sound id silently falls back for that one part.
