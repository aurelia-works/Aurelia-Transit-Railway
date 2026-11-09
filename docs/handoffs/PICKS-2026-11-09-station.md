# Jah's picks, 2026-11-09 evening (in-game look at Utrecht Leidsche Rijn, run 44, and Station Studio)

Spoken feedback, so some words are guesses ("canary mast" = catenary mast, "tabs" = taps). Overall: he likes it a lot.
Nothing here is started. Screenshots: `~/.t3/userdata/attachments/fc90feb0-5b08-45aa-b928-8de6705eb855-*.png`
(683574f7 wrench menu, 7f371411 ticket machine and pylon in Studio, 1e97394c platform in Studio).

## Station: things that don't connect
1. Line-side fence has gaps. Where a catenary mast stands, the fence run breaks.
2. Noise barrier does not join the catenary mast: the mast is its own block and breaks the barrier.
3. Some girders do not reach the square steel column.
4. Help points float on their own short post. They belong under the platform PIDS, fixed to the round steel column.
5. Analog clock looks like it floats. Make it more realistic and hang it from, or stand it on, something.
6. The NS lift tower's pole does not land on a full block.
7. Lift tower ABOVE the canopy (Jah confirmed): it should be the dark steel panel block only, nothing else.
8. INSIDE the lift tower (Jah confirmed), and at the entrance looking in: use the full block that is on the platform
   side instead of the post.

## Station: layout and behaviour
9. Entrance pylon stands right next to the ticket machine (Studio shot, red box). Turn it 90 degrees or move it.
   The ticket machine itself is fine (two blocks tall).
10. Card readers do nothing. Tapping should be required, and a player who has not tapped is blocked from the station.
11. Seal the station so nobody gets in around the readers "by any means"; close the open sides with glass.

## Wrench menu (W, #67), shot at 1920x1052
12. The Station tab is too tall: the rows under "Station name" run off the bottom of the screen. "Kuala Lumpur" is clipped.
13. Bridge tab: he does not know what "Capture bridge" and the bridge preview mean. Simplify and reword.
14. Airport tab: fine. Earthworks "coming soon": fine.

## Other
15. Airport: build it one block into the ground, not raised above it.
16. Default A320: adjust the model in Blockbench.
17. Bicycles: make them rideable (this is PLAN.md phase L2, bike share).
18. "The next thing is like a test": he feels a test pass comes next.

19. Rideable cars (asked later the same evening, "look at the MTR car add-on"). Looked at MTR Traffic Addon by opaleq
    (github.com/0PALEQ/MTR-Traffic-Addon): generated traffic on an MTR-based road network, spawn/despawn connectors,
    signal phases, sedan models; needs MTR 4.0.4+, Fabric 1.20.1. Riding or driving is not mentioned. Licence is custom,
    all rights reserved: no code or models may be copied. Not planned yet; Jah to say whether it becomes a phase.
20. Jah wants the picks round run as an unattended loop (skill `auto-loop`). Contract not written, loop not started.

## Already approved earlier the same evening (ATR Check-in)
- Rebuild the pre-#65 station in ATR Visual with before/after shots.
- Aurelia panel: fix the overlap and restyle the whole panel, own branch.
- Phase 7, all nine phases in plan order.
