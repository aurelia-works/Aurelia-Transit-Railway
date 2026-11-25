/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.BlockPlan.SectionPos;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.spi.PreviewSink;

/** The preview model: outside faces, the block grid, sections and what changed (spec mod-suite-v1 §6.3, task F-4). */
class PreviewModelTest {
    private static BlockPlan plan(int[]... blocks) {
        BlockPlan.Builder plan = BlockPlan.builder(3465);
        for (int[] b : blocks) {
            plan.set(b[0], b[1], b[2], b.length > 3 ? "minecraft:dirt" : "minecraft:stone");
        }
        return plan.build();
    }

    private static PreviewModel road(int x0, int z0, Heading heading, int length) {
        End start = End.at(x0, 64, z0, heading);
        RoadPlanParams params = RoadPlanParams.straight("city-17", start, RoadPlanner.endAlong(start, length));
        BlockPlan plan = RoadPlanner.plan(params, WorldLimits.OVERWORLD).plan().orElseThrow();
        return PreviewModel.of(plan, RoadPlanner.handles(params));
    }

    @Test
    void aSingleBlockHasSixFacesAndTwelveEdges() {
        PreviewModel model = PreviewModel.of(plan(new int[] {3, 64, -5}), List.of());
        assertEquals(6, model.faceCount());
        assertEquals(12, model.edgeCount());
        assertEquals(1, model.sections().size());
        SectionMesh mesh = model.sections().get(SectionPos.of(3, 64, -5));
        assertEquals(List.of("minecraft:stone"), mesh.states());
        Set<Side> sides = new HashSet<>();
        mesh.faces().forEach(face -> sides.add(face.side()));
        assertEquals(Set.of(Side.values()), sides);
    }

    @Test
    void touchingBlocksShareNoFaceAndNoEdge() {
        PreviewModel model = PreviewModel.of(plan(new int[] {0, 64, 0}, new int[] {1, 64, 0, 1}), List.of());
        assertEquals(10, model.faceCount(), "the face between them is inside");
        assertEquals(20, model.edgeCount(), "12 + 12, less the 4 edges around the shared face counted once");
        SectionMesh mesh = model.sections().values().iterator().next();
        assertEquals(List.of("minecraft:stone", "minecraft:dirt"), mesh.states());
    }

    @Test
    void edgesAreNeverRepeatedAcrossSectionBorders() {
        // A 4 × 4 × 4 cube straddling the corner of 8 sections.
        List<int[]> blocks = new ArrayList<>();
        for (int x = 14; x < 18; x++) {
            for (int y = 62; y < 66; y++) {
                for (int z = -2; z < 2; z++) {
                    blocks.add(new int[] {x, y, z});
                }
            }
        }
        PreviewModel model = PreviewModel.of(plan(blocks.toArray(new int[0][])), List.of());
        assertEquals(8, model.sections().size());
        assertEquals(6 * 16, model.faceCount());
        // The block grid on a 4-cube's surface: per face plane 2 × 5 lines of 4 blocks, but the 12 cube edges are
        // shared by two planes: 6 × 40 − 12 × 4 = 192 unit edges.
        assertEquals(192, model.edgeCount());
        Set<SectionMesh.Edge> all = new HashSet<>();
        model.sections().values().forEach(mesh -> mesh.edges().forEach(edge -> assertTrue(all.add(edge), "edge " + edge + " twice")));
    }

    /** Every face and edge of a road, counted the slow way, block by block. */
    @Test
    void aRoadsModelMatchesABruteForceCount() {
        for (Heading heading : List.of(Heading.E, Heading.SE, Heading.ESE, Heading.NNW)) {
            PreviewModel model = road(5, -7, heading, 60);
            End start = End.at(5, 64, -7, heading);
            BlockPlan plan = RoadPlanner.plan(RoadPlanParams.straight("city-17", start, RoadPlanner.endAlong(start, 60)), WorldLimits.OVERWORLD)
                    .plan().orElseThrow();
            // A level road is full blocks and air (its clearance); air is neither drawn nor hides anything.
            Set<List<Integer>> occupied = new HashSet<>();
            plan.blocks().stream().filter(b -> !b.state().equals("minecraft:air")).forEach(b -> occupied.add(List.of(b.x(), b.y(), b.z())));
            int faces = 0;
            Set<List<Integer>> edges = new HashSet<>();
            for (BlockPlan.Block b : plan.blocks()) {
                if (b.state().equals("minecraft:air")) {
                    continue;
                }
                for (Side side : Side.values()) {
                    if (occupied.contains(List.of(b.x() + side.dx(), b.y() + side.dy(), b.z() + side.dz()))) {
                        continue;
                    }
                    faces++;
                    // The face's 4 corners, as unit edges between neighbouring corners.
                    int[][] corners = faceCorners(b, side);
                    for (int i = 0; i < 4; i++) {
                        int[] p = corners[i];
                        int[] q = corners[(i + 1) % 4];
                        int[] lo = {StrictMath.min(p[0], q[0]), StrictMath.min(p[1], q[1]), StrictMath.min(p[2], q[2])};
                        int axis = p[0] != q[0] ? 0 : p[1] != q[1] ? 1 : 2;
                        edges.add(List.of(lo[0], lo[1], lo[2], axis));
                    }
                }
            }
            assertEquals(faces, model.faceCount(), heading.toString());
            assertEquals(edges.size(), model.edgeCount(), heading.toString());
            assertEquals(plan.blockCount(), model.blockCount());
            // Every face is in the section of its block.
            model.sections().forEach((pos, mesh) -> mesh.faces().forEach(face -> assertEquals(pos, SectionPos.of(face.x(), face.y(), face.z()))));
        }
    }

    private static int[][] faceCorners(BlockPlan.Block b, Side side) {
        int a = side.axis().ordinal();
        int u = (a + 1) % 3;
        int v = (a + 2) % 3;
        int[] base = {b.x(), b.y(), b.z()};
        if (side.positive()) {
            base[a]++;
        }
        int[][] corners = new int[4][];
        int[][] steps = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            int[] c = base.clone();
            c[u] += steps[i][0];
            c[v] += steps[i][1];
            corners[i] = c;
        }
        return corners;
    }

    @Test
    void theSameRoadElsewhereIsTheSameModelMoved() {
        PreviewModel here = road(0, 0, Heading.SE, 80);
        PreviewModel there = road(16 * 1000, -16 * 2000, Heading.SE, 80);
        assertEquals(here.sections().size(), there.sections().size());
        List<SectionMesh> a = new ArrayList<>(here.sections().values());
        List<SectionMesh> b = new ArrayList<>(there.sections().values());
        for (int i = 0; i < a.size(); i++) {
            SectionMesh m = a.get(i);
            SectionMesh n = b.get(i);
            assertEquals(m.states(), n.states());
            assertEquals(m.faces().size(), n.faces().size());
            for (int f = 0; f < m.faces().size(); f++) {
                SectionMesh.Face p = m.faces().get(f);
                SectionMesh.Face q = n.faces().get(f);
                assertEquals(new SectionMesh.Face(p.x() + 16000, p.y(), p.z() - 32000, p.side(), p.state()), q);
            }
            assertEquals(m.edges().size(), n.edges().size());
        }
    }

    @Test
    void diffRebuildsExactlyTheSectionsThatChanged() {
        PreviewModel short1 = road(0, 0, Heading.E, 40);
        PreviewModel long1 = road(0, 0, Heading.E, 100);
        PreviewModel.Diff fromNothing = short1.diff(PreviewModel.EMPTY);
        assertEquals(short1.sections().keySet(), fromNothing.rebuild());
        assertTrue(fromNothing.drop().isEmpty());
        assertTrue(fromNothing.handlesChanged());

        // Making the road longer leaves the sections near its start untouched.
        PreviewModel.Diff longer = long1.diff(short1);
        Set<SectionPos> unchanged = new HashSet<>(long1.sections().keySet());
        unchanged.removeAll(longer.rebuild());
        assertFalse(unchanged.isEmpty(), "some sections kept");
        for (SectionPos pos : unchanged) {
            assertEquals(short1.sections().get(pos), long1.sections().get(pos));
            assertTrue(pos.x() * 16 + 15 < 40, "a kept section is wholly before the old end: " + pos);
        }
        for (SectionPos pos : longer.rebuild()) {
            assertFalse(long1.sections().get(pos).equals(short1.sections().get(pos)));
        }
        assertTrue(longer.drop().isEmpty());
        assertTrue(longer.handlesChanged(), "the end moved");

        PreviewModel.Diff shorter = short1.diff(long1);
        assertFalse(shorter.drop().isEmpty(), "the far sections go");
        assertTrue(long1.diff(long1).isEmpty(), "the same model changes nothing");
    }

    @Test
    void changingTheStyleRebuildsEverySectionAndTheHandles() {
        RoadPlanParams params = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(60, 64, 0, Heading.E));
        BlockPlan plan = RoadPlanner.plan(params, WorldLimits.OVERWORLD).plan().orElseThrow();
        PreviewModel normal = PreviewModel.of(plan, RoadPlanner.handles(params));
        PreviewModel refused = PreviewModel.of(plan, RoadPlanner.handles(params), PreviewModel.Style.REFUSED);
        assertEquals(PreviewModel.Style.NORMAL, normal.style());
        assertEquals(PreviewModel.Style.REFUSED, refused.style());
        assertEquals(normal.sections(), refused.sections(), "the same meshes; only their colours differ");

        PreviewModel.Diff toRed = refused.diff(normal);
        assertEquals(refused.sections().keySet(), toRed.rebuild(), "every section is drawn again in red");
        assertTrue(toRed.handlesChanged());
        assertTrue(toRed.drop().isEmpty());
        PreviewModel.Diff back = normal.diff(refused);
        assertEquals(normal.sections().keySet(), back.rebuild());
        assertTrue(refused.diff(refused).isEmpty());
    }

    @Test
    void otherHandlesKeepTheSectionsAndRedrawOnlyTheHandles() {
        PreviewModel model = road(0, 0, Heading.E, 40);
        List<Handle> lit = List.of(model.handles().get(0), new Handle(40, 64, 0, Handle.Kind.END, true));
        PreviewModel relit = model.withHandles(lit);
        assertEquals(model.sections(), relit.sections());
        assertEquals(lit, relit.handles());
        PreviewModel.Diff diff = relit.diff(model);
        assertTrue(diff.rebuild().isEmpty());
        assertTrue(diff.drop().isEmpty());
        assertTrue(diff.handlesChanged());
    }

    @Test
    void blockHeightsAreReadFromTheState() {
        assertEquals(0, PreviewModel.height8("minecraft:air"));
        assertEquals(0, PreviewModel.height8("minecraft:cave_air"));
        assertEquals(8, PreviewModel.height8("minecraft:stone"));
        assertEquals(4, PreviewModel.height8("minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]"));
        assertEquals(4, PreviewModel.height8("minecraft:stone_slab"), "a slab is a bottom slab by default");
        assertEquals(8, PreviewModel.height8("minecraft:stone_slab[type=top,waterlogged=false]"));
        assertEquals(8, PreviewModel.height8("minecraft:stone_slab[type=double,waterlogged=false]"));
        assertEquals(3, PreviewModel.height8("minecraft:snow[layers=3]"));
        assertEquals(8, PreviewModel.height8("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"));
    }

    @Test
    void airIsntDrawnAndASlabIsHalfABlockHigh() {
        BlockPlan.Builder builder = BlockPlan.builder(3465);
        builder.set(0, 64, 0, "minecraft:gray_concrete");
        builder.set(1, 64, 0, "minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]");
        builder.set(0, 65, 0, "minecraft:air");
        builder.set(1, 65, 0, "minecraft:air");
        PreviewModel model = PreviewModel.of(builder.build(), List.of());
        List<SectionMesh.Face> faces = model.sections().values().stream().flatMap(mesh -> mesh.faces().stream()).toList();
        assertEquals(11, faces.size(), "the full block's 6 faces (its top is under air, its east side over a slab), and the slab's 5");
        assertTrue(faces.stream().noneMatch(f -> f.y() == 65), "air isn't drawn");
        assertTrue(faces.stream().noneMatch(f -> f.x() == 1 && f.side() == Side.WEST), "the full block hides the slab's side");
        assertTrue(faces.stream().anyMatch(f -> f.x() == 0 && f.side() == Side.EAST), "the slab doesn't hide the full block's side");
        assertTrue(faces.stream().filter(f -> f.x() == 1).allMatch(f -> f.height8() == 4));
        List<SectionMesh.Edge> edges = model.sections().values().stream().flatMap(mesh -> mesh.edges().stream()).toList();
        assertTrue(edges.contains(new SectionMesh.Edge(1, 64, 0, Axis.X, 4, 8)), "the slab's top edge, half a block up");
        assertTrue(edges.contains(new SectionMesh.Edge(2, 64, 0, Axis.Y, 0, 4)), "its corner, half a block high");
        assertEquals(edges.size(), Set.copyOf(edges).size(), "no edge twice");
        assertEquals(4, model.blockCount(), "the plan's blocks, air included");
    }

    @Test
    void handlesSitAtTheRoadsEnds() {
        PreviewModel model = road(10, 20, Heading.E, 30);
        assertEquals(List.of(new Handle(10, 64, 20, Handle.Kind.START), new Handle(40, 64, 20, Handle.Kind.END)), model.handles());
    }

    @Test
    void thePreviewTellsTheSinkOnlyWhatChanged() {
        List<String> calls = new ArrayList<>();
        Map<String, Integer> rebuilt = new HashMap<>();
        Preview preview = new Preview(new PreviewSink() {
            @Override
            public void update(PreviewModel model, PreviewModel.Diff diff) {
                calls.add("update " + diff.rebuild().size() + " " + diff.drop().size());
                rebuilt.merge("total", diff.rebuild().size(), Integer::sum);
            }

            @Override
            public void clear() {
                calls.add("clear");
            }
        });
        PreviewModel a = road(0, 0, Heading.E, 40);
        preview.show(a);
        preview.show(a);
        preview.clear();
        preview.clear();
        assertEquals(List.of("update " + a.sections().size() + " 0", "clear"), calls);
        assertEquals(PreviewModel.EMPTY, preview.current());
    }
}
