# 1.2.0 beta: in-game test checklist

About an hour, riskiest first. Stop and file a bug at the first crash. Use a copy of a world, never the only one.

Set up first (5 min): the release jar `ATR-fabric-4.0.5+atr.1.2.0-mc1.20.1.jar`, Fabric Loader 0.20.1, Fabric API for 1.20.1, and no other `mtr` jar. Keep `logs/latest.log` from every run.

| # | Test | What to do | Pass |
|---|---|---|---|
| 1 | Add-ons (10 min) | Add MTR Rail Works 0.2.3 and MAGIC 1.5.1 to `mods`, launch, open a world, and click a few nodes with the Rail Builder. | The game reaches the title screen and the world with no crash. Rail Builder clicks build rails. MRW builds at the default size, which is expected. |
| 2 | Old world with cut blocks (5 min) | Open a world from 1.1.0 that has a cargo loader, a cargo unloader, a driver key box and a Station Codex in it. | The world loads. The cargo blocks are still there and move no items. Right-clicking the key box opens MTR's checkbox screen. The Codex is named "(retired)" and does nothing. |
| 3 | Key box (3 min) | In a depot, power a driver key box with redstone. | MTR's keys drop out, and the box shows MTR's spinning keys, not keys on hooks. |
| 4 | Two players (10 min) | Open to LAN (or run a server), join from a second account, both ride one train, and have one player build a small station. | Both players see the same train position and doors. The other player sees the build rise, and nobody gets kicked or desynced. |
| 5 | Station Studio build (8 min) | From the wrench's Station page, open Station Studio. Change tracks and the city, press Build it, then undo. Then make a plan with the wrench, change Studio, and press Build it again. | The built station matches the Studio preview, and undo puts the ground back. If the pending plan differs from Studio, chat asks you to check it and press Build it again, instead of building the old plan. |
| 6 | Bridge mode (5 min) | Lay a rail across a valley, choose Bridge in the wrench, click its two nodes, build a viaduct, then undo. Repeat with a suspension span. | The deck follows the rail with piers on the ground. Undo removes it all, and the rail is untouched. |
| 7 | Road categories (6 min) | Open the road hub (**B**) and build City 17, Bus lanes 29, Rural 21 and Highway 33. Add a junction on the rural road. | Each road shows its own markings (bus lanes, centre line, hard shoulders). The junction joins the rural road. The junction tool refuses bus-lane and highway arms, which is expected. |
| 8 | New fronts (5 min) | In Studio, build city cards for Hong Kong (terminus), St Pancras (6+ tracks), Bangkok, Munich and Antwerp. | Hong Kong has West Kowloon's sweeping glass roof, St Pancras has its gable front with nothing floating, and Bangkok has its arch clear of the concourse. Munich and Antwerp are each distinct, not a box with a clock tower. |
| 9 | New halls (5 min) | Walk inside a Utrecht, an Amsterdam Centraal and a Hamburg station. | Utrecht has white tree columns under a white ceiling. Amsterdam has blue and white Delft tile walls over a dark stone floor. Hamburg has steel trusses under a grey coffered ceiling. Nothing blocks a door, stair or gate. |
| 10 | Airport curves (3 min) | Plan an airport and fly or walk over a taxiway bend. | The yellow centre and edge lines curve as one joined line, with no notches or corners sticking out. |

If time is left: restart the game and undo the last build. Pass is that it undoes.
