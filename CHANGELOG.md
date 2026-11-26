# Changelog

## Unreleased (planned as 1.2.0, 4.0.5+atr.1.2.0, on 2 December)

### Added
- Express trains go first where lines meet. A train on a line set to MTR's "High Speed" route type holds the junction ahead of it, and a slower train from another line waits at its signal until it is through, for at most a minute. The Engine tab's signaller lets other lines go first too. Switch: "Express trains go first at junctions", on by default (#141).
- Passengers travel to any station the network reaches and change trains on the way, with up to two changes. They board only a train or bus that stops where they need to go. Switch: "Passengers change trains", on by default (#136).
- Announcements can speak with ATR's own downloadable voices, picked by the game's language, from the Sound tab. Nothing is added to the jar; the computer's voice is the fallback (#142).
- A driver key box with keys hanging inside the cabinet. A key handed to a driver leaves its hook empty until it runs out (#131).
- Tapping at a gate or card reader shows a small card pop-up. The transit card screen shows the balance, today's spend and journeys, and the Station Codex has a leather cover, sliding tabs and a page turn (#131).
- The train picker shows the hovered train in 3D on a piece of track (#132).
- Stations on the map show quiet, busy or packed from the people waiting there (#134).
- The Engine tab shows how long the train simulation takes each tick (#138).
- Stratos road building, with permission from its owner: road geometry (#147), the road engine (#162), server side (#166), road blocks with paint drawn from road data (#174), road tools, preview and HUD (#176) and the road hub (#179).
- Each road lane gets hidden MTR track along it, so a train can follow the road. Going back to a version without road track leaves the track already built in the world's MTR data; remove it with MTR's tools (#180, #190).
- Roads keep left or right: a world driving-side switch (#188), and operators can switch a whole world's roads from the hub (#195).
- A road can be edited where other roads join it (#189), and junction templates snap onto road ends, mirrored for right-hand traffic (#191).
- Gullies and crossings on roads. With Distant Horizons installed, its far view shows road edits within seconds; it stays optional (#193).
- The hub has its own icons for junctions, crossings and zip lines, and clearer tooltips (#196).
- The map shows homes, landmarks and a station list (#165).
- Station class words halt, local, hub and terminal; a halt gets a ticket machine (#153).
- Airside shops in airport terminals, in walking order (#156).
- A city style builds its city's real station shape on through stations (#187).
- MTR 4.1's train picker tiers and tag search (#159).

### Changed
- Timetabled trains leave at the same minutes each hour and catch up at the end of the line (#140).
- Trains slow down gently on uphill track (#139).
- A train keeps its doors open while people are still boarding (#137).
- Planes stay at the gate for a turnaround that fits their size (#135).
- New worlds start with passengers on and people drawn on the platforms. With no platform blocks, people wait behind the platform screen doors (#133).
- The old ATR menu is gone. Its buttons are in the Aurelia panel's Architecture tab, and the menu key opens the panel (#145).
- ATR runs on a simulation engine built from its own fork of MTR's engine, from the same source as before. Saves load unchanged (#128).
- One versioned client settings file, with the ten old ones migrated (#146).
- One colour and control system for every ATR screen (#157), and a fully dark train picker (#151).
- Building, zip lines and the dashboard work for operators and the single-player host (#169). The panel's Tools tab is for operators only (#173).
- Wing waiting rooms open into the paid hall only (#168).
- Zip lines get a seat with ropes and a thick shaded cable (#178).
- The neural announcer voice is English only (#155).
- Performance: no per-tick reflection, idle simulator tasks, per-second cargo writes or per-row list sorts (#164).

### Removed
- The minimap (#154).
- Road cars and the Bus & Road dashboard; hire bikes stay (#172).
- Crew roles and the driver extras; AWS and TPWS stay (#171).
- The `/aurelia_live_stress` developer command (#170).

### Fixed
- MTR 4.0.6's fixes: vehicles no longer snap or jump while syncing, and the Class 345 and Class 377 have their new sounds (#143).
- A reverse escalator runs down, and board text fits its window (#129).
- Pressing Esc twice on the dashboard no longer crashes the game (#130).
- Players who aren't operators see why the Engine tab is locked (#138).
- MTR 4.1 leftovers: no double riding, one door toggle, and multi-node bridge and tunnel tools (#167).
- A city or grand station building gets its class's platform length, not 48 (#185).
- Hill slowdown works together with MAGIC's speed-limit redirect (#194).
- The smallest GUI scale shows the hub's layout between the strip's arrows (#192).

### Pending: open pull requests, not merged yet
Move each line up when its pull request merges, and delete what misses the release.
- MTR 4.1's per-train rail sounds and dyeable PIDS (#148).
- Escalators get glass balustrades above a thin steel skirt (#152).
- The map: unexplored squares filled from saved chunks (#163), and the dashboard map shows the whole explored world (#160).
- The Resource Pack Creator page, with Blender and Blockbench model import (#184).

### Fixed
- The dashboard's Resource Pack Creator opens a working creator again: it was an empty page in every ATR jar. It also imports Blender models as glTF Binary (.glb), loads Blender's OBJ export with any texture file names, and shows its icons without internet (#184).

## 1.1.0 public beta (4.0.5+atr.1.1.0), Minecraft 1.20.1, Fabric

Public beta. Back up your world before trying it. Drawn tracks and the station and airport editor screen (E1, E2) have not been tried in game yet.

### Added
- The wrench builds bridges along a laid rail: box-girder viaducts on bearings, steel trusses, brick arch viaducts, cable-stayed and suspension spans, tunnel portals, and overhead-line masts with wire. Tower and pylon lamps flash at night (#115).
- Jet bridges dock at the A320 and A350 front door, so you can walk onto the plane. Planes taxi in nose first and are pushed back off the stand, and taxiway curves get smooth yellow lines (#115).
- Stations can have up to 32 tracks, the real-station presets use their real track counts, and preset templates can be edited (#116).
- One transit dashboard for rail, road, boat, cable car and plane replaces the separate train, bus and car screens. The old dashboard items still load (#117).
- Station descriptions can be up to 2400 characters, numbers can be written as words ("twenty four"), and Studio keeps the words typed after a preset name (#123).

### Changed
- Escalators have brushed-steel side panels under the handrail, from the deck up to the rail and round the curved ends. This applies to every MTR escalator, including ones already placed in a world (#126).
- A station building takes its size from real stations with the same number of platformed tracks: up to 3 gets a small block, 4 or 5 a city building, 6 or more a grand one (#112). Dutch 4-track halls are grand again, and landmark fronts follow their photos more closely (#118).
- Station Studio has a new layout: one navigation bar, a seed board with locks and quick presets, a grouped camera bar and a tidier checks panel (#119). Screen readers hear counts and status changes, and the labels are plainer (#125).
- Deep stations follow their real depth and length: Angel is 27 m deep, Canary Wharf is full length (#120).

### Fixed
- The airport entrance canopy stands on columns on the footway, never in the road, and the car park rows start at the car park's own edge (#124).
- The dashboard opens on dedicated servers (#117).
- The Controls screen lists the ATR wrench, menu, panel and riding keys under an "Aurelia Transit Railway" heading instead of a raw key name (#127).

## 1.0.3 (4.0.5+atr.1.0.3), Minecraft 1.20.1, Fabric

### Added
- Rail nodes can be placed at 11.25° steps. Texture packs keep working because the node marker reuses the existing 22.5° models; the rail itself uses the finer angle.
- Metallic steel panels: plain, dark, perforated, galvanised and brushed.
- Custom bridge generator: `/atr_bridge capture` stores a bridge section as a template, `/atr_bridge preview` and `/atr_bridge build` stamp it along an MTR rail with quarter-turn rotation and height, and `/atr_bridge undo` restores the last build. See `docs/B1_CUSTOM_BRIDGE.md`.
- On-train next-stop and arriving announcements can use voice packs, with the old AureliaEX clips kept as a fallback and the system voice for missing station names.
- Voice-pack format documented in `docs/VOICE_PACKS.md`.
- Passenger demo: `/atr_passengers demo [count]` fills the nearest MTR platform with waiting passengers and `/atr_passenger_draw on` shows the figures in game. See `docs/PASSENGER_DEMO.md`.
- Passengers are drawn as varied people: 24 painted passenger skins, different heights, phones, bags, suitcases and idle movement. The panel's Figures button switches between People, Solid boxes and Outlines. People walk to a train's real doors and through them when boarding and getting off; the panel sets how many walk per train.
- Passengers now show as solid people standing on the platform, with size, colour and outline switches on the panel, and people on a platform with no route no longer disappear.
- Platform door markers sit where the doors of the longest train stop (#98).
- A bus station at the station front: lettered bays, shelters and departure poles (#99).
- Stair rails climb as one sloped handrail (#101).
- Airport terminal buildings: hall, glazed band and entrance canopy (#95).

### Changed
- A station entrance is a porch: canopy posts, a fascia under the canopy edge, rails beside the ramps and a glass transom over the opening. Inside, the concourse is a hall, with a plinth aisle, a double-height void just inside a side-building door and piers down the room. Fare gates are unchanged.
- The wings of a city or grand station are waiting rooms: a departure board, a shop or kiosk against the walls and islands of seats with a bin and a planter, with the walks between the platform doors kept clear.
- Stations and airports build anywhere the player confirms: `replace_terrain` and the approval step are gone (`confirm replace_terrain` still works as an alias). Every build saves the previous block states and block entity data in its own file under `data/ata_station_undo/`, so undo restores the ground block for block even after a server restart. Builds rise layer by layer, paced by a blocks-per-tick slider on the Architecture tab of the Aurelia panel and `/ata_station pace`.
- Station preset realism pass: Teddington is 6 blocks wide with lifts, Modern is named and has escalators, Frankford and 69th Street no longer promise escalators they did not build, Rosenthaler Platz has escalators, and West Kowloon has a glass hall roof. Station Studio presets no longer inherit stale controls, locks or the previous planner seed. See `docs/STATION_PRESETS.md`.

### Fixed
- The Aurelia panel's twelve tabs wrap into rows on narrow windows, so their labels stay readable (#43).
- A taxiway meets a runway on a curve. The square link's corner is a quarter-circle a plane can follow, on the generated field and on a drawn taxiway that meets a drawn runway at a right angle.
- Station Studio draws escalator slopes, steps and handrails (#103).

## 1.0.2 (4.0.5+atr.1.0.2), Minecraft 1.20.1, Fabric

### Added
- Next-stop announcements while riding (#19).
- Flange squeal on curves (#20).
- Pocket timetable printed from a platform (#21).
- Zone fares, a daily cap and card tap history (#22).
- A-to-B journey planner on the terminal and AureliaMap (#23).
- Suspensions hold a route's trains at the depot (#24).
- Load and unload freight at a stopped train (#25).
- Jet bridges that meet the plane, and a live departures board (#26).
- Split or couple a stopped train at a station (#29).
- Depot controls: instant deploy, clear vehicles, and per-depot all-doors and announcement switches (#35).
- Train sound: a pitch range past vanilla's 0.5-2, smoother motor steps for old-style trains, door chimes, and a curve squeal volume, all on a new Train sound panel tab (#36).

### Fixed
- Coupled trains keep the rear train's freight (#32).
- Departure screens show a route's edited destination without a path refresh (#34).
- Replacing a rail node block removes its rails, and breaking one no longer syncs twice (#34).
- A long low-FPS step out of a doorway no longer leaves the player inside the car (#34).
- Text-to-speech announcements stay silent when the Master or Voice volume is off (#34).

## 1.0.1 (4.0.5+atr.1.0.1), Minecraft 1.20.1, Fabric

### Fixed
- Route maps no longer reset the drawing scale on the render thread while a worker thread is still drawing with it.
- Fare gates remember who paid per world. Two dimensions that share a block position no longer share one payer entry.

### Project
- Repository URLs now use `aurelia-works/Aurelia-Transit-Railway`. GitHub redirects the old name.
- README no longer sends people to upstream images, the old todo list, Modrinth, Crowdin, Discord, Patreon, or the upstream email.
- The Crowdin workflow stays manual. It still names the upstream Crowdin project and must not run until ATR has its own project.

## 1.0.0 (4.0.5+atr.1.0.0), Minecraft 1.20.1, Fabric

First release of Aurelia Transit Railway, a maintained fork of Minecraft Transit Railway 4.0.5.

### Drop-in
- Mod id stays `mtr`; version reports `4.0.5+atr.1.0.0`. JCM, MAGIC, Tianjin, the London Underground add-on and train packs keep working, and MTR worlds open unchanged.
- A test (`DropInSignaturesTest`) guards the MTR 4.0.5 methods that add-ons hook.

### Speed
- Rendering and model-loading fixes from Aurelia Transit Utilities: merged train parts, a shader-pack fast path, cheaper shadows, level of detail for trains and rails, a fast OBJ reader, background model loading, door checks, freeing unused models, F3 counters. Each has a switch.

### New
- **Aurelia panel**: one place for every tool and switch, with the Announcer and platform calls.
- **Rail Builder**: one smart tool; creators build faster; sit or stand while riding (X key).
- **Aurelia Transit Architecture blocks, Station Studio and wrenches** ship inside the jar (replaces the separate ATA mod).
- **AureliaMap**: a 3D web map with a live layer of lines, stations and trains.
- Web map: opens fitted to the network, less label clutter, solid lines, a clearer station panel.
- **Moving pantographs**: the Class 802 raises its pantograph on the route and lowers it at the depot; resource packs can add the same with new optional part fields; switches on the Aurelia panel.
- Opaque lift style, configurable ticket currency and free barriers.

### Fixed
- No rain, rain splash or rain sound inside train cars.
- Platform gate maps no longer stretch past 10 blocks.
- Airplanes stay on the platform and the glideslope when landing.
- Lift floor menu: the whole row is clickable.

### Credits
Based on Minecraft Transit Railway by jonafanho (MIT). See LICENSE.
