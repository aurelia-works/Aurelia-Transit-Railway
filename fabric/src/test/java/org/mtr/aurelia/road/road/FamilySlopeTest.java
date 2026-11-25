/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Family;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.PaletteRole;
import org.mtr.aurelia.road.road.raster.Column;
import org.mtr.aurelia.road.road.raster.Rasteriser;
import org.mtr.aurelia.road.road.raster.RoadPiece;
import org.mtr.aurelia.road.road.solve.RoadSolution;
import org.mtr.aurelia.road.road.solve.RoadSolver;

/**
 * R-9's acceptance: a sloped road in the Vanilla palette uses one family per band. 300 random climbing roads (every
 * urban layout, straights and bends, anywhere in the world, rising or falling 1 to 6 blocks), planned as a player's
 * request is. In every column, the road's blocks other than the foundation and the cleared air are the full block of
 * that column's role and, on a half step, that same family's slab on top of it; nothing else. Across the run every role
 * the layouts have stands on a half step somewhere, so each family's slab is really used.
 */
class FamilySlopeTest {
    private static final List<RoadProfile> LAYOUTS = List.of(RoadProfiles.CITY_17, RoadProfiles.RESIDENTIAL_15, RoadProfiles.RESIDENTIAL_17,
            RoadProfiles.ALLEY_7);

    @Test
    void aSlopedRoadInTheVanillaPaletteUsesOneFamilyPerBand() {
        Palette vanilla = BuiltInPalettes.vanilla();
        Random random = new Random(20261010L);
        Map<PaletteRole, Integer> halves = new EnumMap<>(PaletteRole.class);
        Map<String, Integer> refused = new TreeMap<>();
        Set<PaletteRole> roles = new HashSet<>();
        int accepted = 0;
        long columns = 0;
        for (int attempt = 0; accepted < 300; attempt++) {
            assertTrue(attempt < 20_000, "too few roads accepted");
            RoadProfile profile = LAYOUTS.get(attempt % LAYOUTS.size());
            int x = random.nextInt(2_000_001) - 1_000_000;
            int z = random.nextInt(2_000_001) - 1_000_000;
            int y = random.nextInt(200);
            Heading from = Heading.of(random.nextInt(16));
            int rise = (1 + random.nextInt(6)) * (random.nextBoolean() ? 1 : -1);
            End start = End.at(x, y, z, from);
            End end;
            if (attempt % 2 == 0) {
                End along = RoadPlanner.snapEnd(start, 40 + random.nextInt(120));
                end = End.at(along.x(), y + rise, along.z(), from);
            } else {
                Vec2 p = from.tangent().times(60 + random.nextInt(100)).plus(from.normal().times(random.nextInt(81) - 40));
                end = End.at(x + (int) StrictMath.rint(p.x()), y + rise, z + (int) StrictMath.rint(p.z()), Heading.of(random.nextInt(16)));
            }
            RoadPlanParams params = (attempt % 2 == 0 ? RoadPlanParams.straight(profile.id(), start, end) : RoadPlanParams.curve(profile.id(), start, end))
                    .withPalette(vanilla.id());
            PlanResult result = RoadPlanner.plan(params, WorldLimits.OVERWORLD.withModBlocks(Set.of()));
            if (!result.isPlanned()) {
                refused.merge(result.code().orElseThrow(), 1, Integer::sum);
                continue;
            }
            accepted++;
            // Each column's role, from the road's own pieces (as the planner rasterised them).
            RoadSolution.Solved solved = (RoadSolution.Solved) RoadSolver.solve(profile, params.preset(), params.vertical(), start, end);
            Map<BlockXZ, Column> byColumn = new HashMap<>();
            for (RoadPiece piece : RoadPlanner.rasterPieces(profile, solved.pieces(), params.dashes())) {
                byColumn.putAll(Rasteriser.columns(piece));
            }
            Map<BlockXZ, Set<String>> states = new HashMap<>();
            for (BlockPlan.Block block : result.plan().orElseThrow().blocks()) {
                if (!block.state().equals("minecraft:air") && !block.state().equals(vanilla.foundation())) {
                    states.computeIfAbsent(new BlockXZ(block.x(), block.z()), c -> new HashSet<>()).add(block.state());
                }
            }
            for (Map.Entry<BlockXZ, Set<String>> entry : states.entrySet()) {
                // A column outside the raster is road under a lane node past a 22.5° end: lane surface (R-6).
                Column column = byColumn.getOrDefault(entry.getKey(), new Column(Band.LANE, false, true));
                PaletteRole role = PaletteRole.of(column);
                roles.add(role);
                Family family = vanilla.family(role).orElseThrow();
                Set<String> used = entry.getValue();
                String where = profile.id() + " " + start + " -> " + end + " at " + entry.getKey() + " (" + role.id() + ")";
                assertTrue(used.contains(family.full()), where + ": no " + family.full() + " in " + used);
                assertTrue(Set.of(family.full(), family.half().orElseThrow()).containsAll(used), where + ": " + used + " isn't one family");
                if (used.contains(family.half().orElseThrow())) {
                    halves.merge(role, 1, Integer::sum);
                }
                columns++;
            }
        }
        System.out.printf(Locale.ROOT, "R-9: 300 sloped roads in Vanilla (%,d columns), one family per band; half steps by role %s; refused %s%n", columns,
                halves, refused);
        assertEquals(Set.of(PaletteRole.SURFACE, PaletteRole.MARKING_WHITE, PaletteRole.KERB, PaletteRole.FOOTWAY, PaletteRole.GUTTER), roles,
                "the urban layouts' roles");
        assertEquals(roles, halves.keySet(), "every role stood on a half step somewhere");
    }

    @Test
    void theVanillaPalettesFamiliesAreMatched() {
        // ADR-0044, owner 2026-10-10: each part's full block with its own slab.
        Palette vanilla = BuiltInPalettes.vanilla();
        for (PaletteRole role : List.of(PaletteRole.SURFACE, PaletteRole.MARKING_WHITE, PaletteRole.KERB, PaletteRole.FOOTWAY, PaletteRole.GUTTER)) {
            Family family = vanilla.family(role).orElseThrow();
            assertEquals(family.full() + "_slab[type=bottom,waterlogged=false]", family.half().orElseThrow(), role.id());
        }
        assertEquals("minecraft:polished_deepslate", vanilla.family(PaletteRole.SURFACE).orElseThrow().full());
        assertEquals("minecraft:smooth_quartz", vanilla.family(PaletteRole.MARKING_WHITE).orElseThrow().full());
        assertEquals("minecraft:polished_andesite", vanilla.family(PaletteRole.KERB).orElseThrow().full());
        assertEquals("minecraft:smooth_stone", vanilla.family(PaletteRole.FOOTWAY).orElseThrow().full());
        assertEquals("minecraft:polished_andesite", vanilla.family(PaletteRole.GUTTER).orElseThrow().full());
        assertEquals("minecraft:stone", vanilla.foundation());
        assertEquals("minecraft:blue_wool", vanilla.nodeMarker());
        assertEquals(2, vanilla.steps());
    }
}
