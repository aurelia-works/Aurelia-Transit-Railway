# Driving and signalling

Two things landed together: lineside signal aspects now follow the blocks ahead, and a driver gets a UK-style cab
(AWS, TPWS, door keys, a stop marker and a score). Everything visible is switched on the panel's new **Driving** tab.

## Keys

| Key | Default | What it does |
| --- | --- | --- |
| Accelerate | Up arrow (MTR) | One power notch up: P1 to P7 |
| Brake | Down arrow (MTR) | One brake notch down: B1 to B8; B8 is the emergency brake |
| Toggle doors | Left arrow (MTR) | MTR's own door toggle |
| AWS acknowledge | J | Cancels an AWS horn and the service brake it applied |
| TPWS reset | K | Releases a TPWS trip after the train is at a stand |
| Open left | Comma | Opens the doors if the left side is at a platform |
| Open right | Period | Opens the doors if the right side is at a platform |
| Close doors | Semicolon | Closes the doors |

The four new bindings have their own ids (`key.aurelia.driving.*`) in MTR's keybinding category, so existing
bindings and old options files are untouched. Rebind them in Options → Controls if they clash.

## Signal aspects

`org.mtr.aurelia.signal.SignalAspect` is the rule book:

| Head | clear blocks = 0 | 1 | 2 | 3 or more |
| --- | --- | --- | --- | --- |
| 2 aspect | red | green | green | green |
| 3 aspect | red | yellow | green | green |
| 4 aspect | red | yellow | double yellow | green |

A block only counts as clear when the walk has seen the end of it. A train further inside the same block still turns
the signal red, which the old MTR "yellow for five seconds" rule could not do. A signal with no rail on its facing
side is red. Unsignalled track with no colours keeps the MTR behaviour and shows proceed.

**A block** is a run of rails sharing the same set of signal colours, which is how MTR stores signal blocks. The
client walks the rail graph from the signal's node, through the far node of each rail, and follows the straightest
rail that has the same colours (and the signal's filter colours when it has filters). This is cached per head for
400 ms, so the lamps are not recomputed per frame.

**A route set** has no real meaning in MTR: there is no interlocking. A route counts as set when a signalled rail
exists on the signal's facing side. At a junction the straightest matching rail is followed rather than a set
branch. This is the main difference from a real interlocking.

The aspect count comes from the physical head (2, 3 or 4 lamps), so the same block state produces the right
sequence for each head. The 2- and 3-aspect renderers never receive the double-yellow index.

## AWS

While manually driving, passing a signal that shows caution or danger sounds the AWS **horn**; passing a clear
signal rings the **bell**. Both are one shot at the signal. The horn leaves the sunflower lit until the AWS button
(J) is pressed. If it is not pressed within the configured time (3 s by default, 1–6 s on the panel), a full
service brake goes on (B7). Pressing J cancels the warning and releases the brake request; the driver then has to
release the brake handle with the normal brake/power keys.

The horn and bell are synthesized for ATR (`assets/aurelia/sounds/`), not sampled from a real cab.

## TPWS

A TPWS trip puts the emergency brake on (B8) and lights the TPWS indicator. It latches:

- **Overspeed:** within 400 m of a restrictive signal, the current speed above the lower of the track and manual
  limits by 5% + 1 km/h. Real TPWS overspeed loops sit at signal approaches and speed restrictions, not on every
  metre of line, so plain-line overspeed is scored but not tripped.
- **SPAD:** passing a signal whose block is occupied, i.e. a signal that was red.

Stop the train, then press K. The reset is refused while the train is moving. The emergency brake stays applied
until the driver notches up after the reset.

MTR does not store TPWS loop positions, so overspeed is a continuous monitor rather than a pair of loops at a
particular signal. The SPAD sensor uses the same block-boundary data as AWS.

## Doors

MTR has one door toggle. The two open keys add the side check a real driver makes: the key does nothing and says
"No platform on the left/right" unless platform blocks are next to that side of the train. The train's reversed
state is taken into account. At a bay with platforms on both sides MTR opens both, because its door system has no
independent per-side latch. The close key always sends the MTR toggle, which closes at a stand and is a no-op while
moving.

## Stop marker

When a platform stop is within 400 m, a yellow/black stop board is drawn beside the track at the front stop point for
this train. The HUD also shows a stop tape, the distance to the marker, and the train's car count and length, so the
same marker works for a 2-car unit and a 10-car set. The board is geometry only; no new sign art.

## Score

One run starts when the train begins moving (above 0.5 m/s) and ends at a platform stop within 5 m of the marker.
Stopping at a signal before the platform does not end the run, so the clock keeps going. A stop short of the
platform ends the run with a poor accuracy and a new run starts from the next departure.

- Base 1000 points.
- Up to 300 for stop accuracy: full marks within 0.3 m, zero at 3 m and beyond.
- −300 per SPAD, −100 per overspeed event (the first tick over the limit, not every tick).
- −1 point per 10 s over three minutes. MTR gives the client no timetable target, so time is a gentle cap, not a
  punctuality score.

The HUD shows the current run while moving and the last run after stopping. The **Score HUD** switch hides the last
line; the run is still scored.

## Panel

The **Driving** tab has:

- AWS on/off, TPWS on/off, Score HUD on/off.
- Stop board on/off.
- AWS acknowledge time, 1.0–6.0 s.
- HUD size, 75–150%. The HUD also shrinks to fit a small window.

They are new client-config keys (`drivingAws`, `drivingTpws`, `drivingScoreHud`, `drivingStopMarker`, `drivingAwsSeconds`,
`drivingHudScale`) in `config/mtr.json`. Old configs and worlds load unchanged; missing keys take their defaults.

## Test it in game

1. Put a 4-aspect head on a line with four signal blocks ahead. Drive up: with the first block occupied it is red;
   clear the first block and occupy the next for yellow; clear two for double yellow; clear three for green.
2. Repeat with 3- and 2-aspect heads. The 3-aspect skips double yellow; the 2-aspect only shows red or green.
3. Face a signal at a dead end (no rail on its facing side) and confirm red.
4. Drive manually past a caution. The horn should sound; press J and the warning should clear without a brake. Do it
   again and ignore it: after about three seconds the train should brake itself at B7.
5. Pass a clear signal and listen for the bell.
6. Run past a red with TPWS on. The emergency brake should apply and the HUD should say `TPWS TRIP`; press K while
   still moving and the reset should be refused. Stop, press K, then notch up.
7. Approach a restrictive signal at more than 5% + 1 km/h above the limit and confirm the TPWS overspeed trip. The
   same overspeed on plain line should count on the score but not trip.
8. Stop at a platform and press comma and period. Only the platform side should open. Press semicolon to close. Try
   the wrong side and check the "No platform" message.
9. Approach a platform: the stop board should appear beside the track and the HUD tape should count down. Note the
   car count and length on the HUD.
10. Complete a run and read the `LAST` line: points, stop error, SPADs, overspeeds, time.
11. Open the panel's Driving tab and change every switch and slider. Restart; they should persist.
12. Check F3 while several signals are in view: aspects are cached, so frame time should not change noticeably.

## Known limits

- No interlocking, route setting, approach locking or route indicators. Junctions choose the straightest matching
  rail.
- Only the three lamp heads MTR already has: no flashing aspects, shunt/position-light signals or theatre indicators.
- AWS and TPWS infer their magnets from block boundaries. A colour change with no physical signal is ignored; a
  very long block gives only an approximate next-signal distance.
- The HUD assumes a four-aspect head for the *next signal* display when the physical head cannot be found; the AWS
  horn/bell uses the head it did find.
- MTR's notches are P1–P7 and B1–B8, not a specific class's P1–P4/B1–B7; the emergency brake is B8.
- Door open-left/open-right gate MTR's single toggle, so a bay platform with both sides open opens all doors.
- The stop board is geometry only, without a "4 car stop" plate.
- Sounds are synthesized, not recorded from a real AWS/TPWS installation.
