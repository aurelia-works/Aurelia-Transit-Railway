# Voice packs

A voice pack is ordinary resource-pack data. It ships no Java code and no real announcer recordings; the built-in
pack and any pack you add contain only sounds you have the right to distribute.

## Where a pack goes

Put one or more JSON files in a resource pack at:

```
assets/<namespace>/aurelia_voice/<name>.json
```

Every JSON file is one pack. Packs load with the resource pack and reload on `F3+T`. At most 32 packs and 4096
fragments per pack are read.

## Format

```json
{
  "priority": 10,
  "fragments": {
    "phrase.calling_at": "my_pack:voice.calling_at",
    "number.3": { "sound": "my_pack:voice.three", "duration_ms": 600 },
    "station.central_station": "my_pack:voice.central"
  }
}
```

- `priority` is optional; higher wins. Ties are ordered by pack id.
- A fragment value is either a sound-event id or an object with `sound` and optional `duration_ms`.
- `duration_ms` is how long the next fragment waits. It defaults to 900 ms and is clamped to 50–15000 ms.
- A malformed fragment is skipped, not fatal.
- A missing fragment is skipped. If the system voice is available, the full sentence is spoken instead; otherwise the
  text still appears on screen.

## Keys

Keys are lower-case, accents removed, and punctuation becomes `_`:

- `phrase.<name>`: fixed wording, for example `phrase.calling_at`, `phrase.mind_the_gap`.
- `number.<0-99>`: spoken numbers.
- `station.<name>`: a station name, for example `station.central_station`.
- `route.<name>`: a route name.
- `platform.<name>`: a platform that is not a plain number.
- `chime.info`, `chime.alert`, `chime.delay`: the three chimes.

The English wording and the complete key list live in `AnnouncementText.Phrase` and `FragmentKeys`. A pack may cover
only station names, only numbers, or the whole sentence; it layers over the built-in chimes.

## Compatibility

- Packs are additive. Two packs can cover different fragments.
- Sound ids are namespaced; do not use another pack's namespace.
- The built-in ATA pack is at
  `assets/aurelia_transit_architecture/aurelia_voice/builtin.json`.
- Do not ship copyrighted station or announcer audio. Use your own recordings, generated speech, or silence.
