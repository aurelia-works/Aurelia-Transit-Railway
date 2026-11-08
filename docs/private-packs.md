# Private packs

Private packs are ordinary resource-pack and data-pack JSON. They carry the parts of a server's identity that should
not live in the ATR jar: operators, liveries, per-vehicle sound profiles, station presets and depot timetables. A pack
ships data only; ATR ships the format, never another server's content.

## Where a pack goes

One JSON file is one pack. Resource pack:

```
assets/<namespace>/aurelia_packs/<name>.json
```

Data pack:

```
data/<namespace>/aurelia_packs/<name>.json
```

Every loaded file is validated on load and on `F3+T`. The Aurelia panel has a **Packs** tab listing each loaded pack,
its error and warning counts, and every diagnostic as `file:line: message`. A file with a syntax error is skipped. A
single bad entry is skipped while the rest of its file loads. Nothing a pack contains can crash the game. Duplicate ids
inside one file are errors; the same id in two different files is a warning naming both files.

Use resource packs for operators, liveries and sound assignments: those are client-side. Data packs are read by the
server and are the right home for station presets and timetables; in single-player the panel lists both.

Packs are on by default. The **Private packs** switch on the Packs tab turns the loader off without removing files,
and the **Example pack** switch enables the built-in example (off by default). The switch is saved in
`config/aurelia/packs.json`; a missing file or key means on.

## Format

`format` is required and must be `1`. `name` is optional and falls back to the file name. Every section is optional.
The machine-readable schema is [`private-packs.schema.json`](private-packs.schema.json) (JSON Schema 2020-12); editors
that understand `$schema` can validate a pack as it is written.

```json
{
  "format": 1,
  "name": "Example Rail private pack",
  "operators": [
    {
      "id": "example_rail",
      "name": "Example Rail",
      "short_code": "EXR",
      "colours": {
        "primary": "#1F4E79",
        "secondary": "#F2C94C",
        "text": "#FFFFFF"
      }
    }
  ],
  "liveries": [
    {
      "id": "example_emu",
      "operator": "example_rail",
      "name": "Example EMU",
      "vehicles": ["example:emu_cab", "example:emu_trailer"],
      "textures": {
        "example:emu_cab": "example_rail:textures/vehicle/example_emu.png"
      }
    }
  ],
  "sounds": {
    "example:emu_cab": "mtr:uk_emu"
  },
  "station_presets": [
    {
      "id": "example_central",
      "name": "Example Central",
      "description": "four track modern station, two island platforms, 160 long, glass canopy, station building with footbridge, named Example Central",
      "style": "modern"
    }
  ],
  "timetables": [
    {
      "id": "example_weekday",
      "name": "Example Weekday",
      "depot": "Example Depot",
      "weekday": {
        "real_time": true,
        "departures": ["05:30", "05:45", "06:00", "06:15"]
      },
      "weekend": {
        "real_time": false,
        "frequencies": [0, 0, 0, 0, 0, 2, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 2, 2, 0, 0]
      }
    }
  ]
}
```

### Operators

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Lower-case pack id (`a-z`, `0-9`, `_`, `.`, `-`). Unique in the file. |
| `name` | yes | Display name, up to 80 characters. |
| `short_code` | yes | The code boards and signs show, up to 8 characters. |
| `colours.primary` | yes | `#RRGGBB`. |
| `colours.secondary` | no | `#RRGGBB`; defaults to `primary`. |
| `colours.text` | no | `#RRGGBB`; defaults to `#FFFFFF`. |

### Liveries

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Lower-case pack id, unique in the file. |
| `operator` | yes | An operator `id` from the same file. |
| `name` | no | Display name; defaults to the livery id. |
| `vehicles` | no | MTR vehicle resource ids (`namespace:path`) the scheme applies to. |
| `textures` | no | Object mapping a vehicle id to the texture resource that replaces its own. |

At least one of `vehicles` and `textures` must be present. MTR's own `mtr_custom_resources.json` still defines the
vehicles and models; a livery only names them.

### Sound profiles

```json
"sounds": {
  "namespace:vehicle": "namespace:profile"
}
```

This section only assigns profiles; it does not define them. A profile id points at SN's file
`assets/<namespace>/aurelia_sound_profiles/<name>.json`, for example `mtr:uk_emu` is
`assets/mtr/aurelia_sound_profiles/uk_emu.json`. The format and the built-in profiles are documented in
[`sound-profiles.md`](sound-profiles.md). If the file is not in the loaded resource packs, ATR keeps the
assignment and shows a warning with the file and line. It does not fail the pack. Sound assignments are not checked in
data packs because SN's profiles are client assets.

### Station presets

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Lower-case pack id, unique in the file. |
| `name` | yes | Display name, up to 80 characters. |
| `description` | yes | The station in the generator's own words, up to 512 characters. |
| `style` | no | A menu hint, up to 32 characters. |

`description` goes through the same parser, design rules and checks as a description typed into the wrench or Station
Studio. It is not a free-form blueprint.

### Timetables

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Lower-case pack id, unique in the file. |
| `name` | yes | Display name, up to 80 characters. |
| `depot` | yes | The depot name the timetable is for, up to 80 characters. |
| `weekday` | one of the two | The weekday plan. |
| `weekend` | one of the two | The weekend plan; without one the weekday plan runs every day. |

A plan is either real departure times or one frequency per hour, with the same shapes MTR's depot screen and ATR's
weekly timetables use:

- `real_time` is optional and defaults to `true`. Real-time plans follow the real calendar; the others follow the
  world's day count.
- `departures` is an array of `HH:MM` or `HH:MM:SS` strings, at most 2000. Times are sorted and wrapped into one day.
- `frequencies` is exactly 24 whole numbers from 0 to 20, one per hour, in MTR's quarter-trains-per-hour unit.
- Give `departures` or `frequencies`, not both, and at least one in every plan.

## Limits

- At most 64 pack files are read per source (resource packs or data packs).
- At most 512 entries are read from each section, 128 vehicle ids or texture overrides per livery, and 2000
  departures per plan.
- A file larger than 1 MiB is skipped.
- A file stops reporting after 50 diagnostics; the panel then says more were not shown.

## Example

The example above is the built-in **ATR example private pack**, off by default. It is also the file
`fabric/src/main/resources/resourcepacks/example_private_pack/assets/example_rail/aurelia_packs/example_rail.json`
in the source tree. It uses the built-in `mtr:uk_emu` and `mtr:metro` profiles, so it loads without warnings. Copy it
into your own pack, rename the namespace, ids and names, and keep `format: 1`.

## Compatibility

- Old worlds and old `config/mtr.json` files are untouched: packs add a new file type and a new `config/aurelia/packs.json`.
- A missing key takes its default; a missing section is empty.
- A pack never changes MTR's block, item or save ids.
- Do not ship another server's names, text, images or data. Only ship content you have the right to distribute.
