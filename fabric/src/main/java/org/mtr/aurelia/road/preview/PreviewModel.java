/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.preview;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.BlockPlan.SectionPos;

/**
 * The preview of one block plan (spec mod-suite-v1 §6.3, spike S7): per 16³ section, the faces of the plan's blocks
 * that face away from the plan and the block-grid edges around them (owner, 2026-10-05: outlines are the block grid),
 * plus the gizmos (handles). Faces between two of the plan's blocks are never drawn, and each edge belongs to exactly
 * one section: the section of the first face that has it, in the plan's canonical order.
 *
 * <p><b>From G-7</b> the preview shows what will be built: air (a road's clearance) isn't drawn, a block can be partial
 * ({@link #height8}: a bottom slab is half a block high), and only a full block hides its neighbours' faces, so the
 * riser of each half step shows. Faces and edges carry their heights in eighths of a block.
 *
 * <p>A section's mesh depends only on the plan, so comparing two models ({@link #diff}) tells the renderer exactly which
 * sections to rebuild. A model also has a {@link Style}: a refused plan is drawn in red (roads-v1 §12; R-2). Pure and
 * deterministic, and safe to build off the render thread (the road tool does, R-2).
 */
public final class PreviewModel {
    private static final Comparator<SectionPos> SECTION_ORDER =
            Comparator.comparingInt(SectionPos::x).thenComparingInt(SectionPos::z).thenComparingInt(SectionPos::y);

    public static final PreviewModel EMPTY = new PreviewModel(new TreeMap<>(SECTION_ORDER), List.of(), 0, Style.NORMAL);

    /** How the whole preview is drawn. */
    public enum Style {
        /** In the blocks' own colours, with the brand's outlines and handles. */
        NORMAL,
        /** A plan the planner refused: faces, outlines and handles in the error colour (roads-v1 §12). */
        REFUSED
    }

    /** What changed between two previews: the sections to (re)build, the sections to drop, and whether the handles moved. */
    public record Diff(Set<SectionPos> rebuild, Set<SectionPos> drop, boolean handlesChanged) {
        public Diff {
            rebuild = Collections.unmodifiableSet(new LinkedHashSet<>(rebuild));
            drop = Collections.unmodifiableSet(new LinkedHashSet<>(drop));
        }

        public boolean isEmpty() {
            return this.rebuild.isEmpty() && this.drop.isEmpty() && !this.handlesChanged;
        }
    }

    private final SortedMap<SectionPos, SectionMesh> sections;
    private final List<Handle> handles;
    private final int blockCount;
    private final Style style;

    private PreviewModel(SortedMap<SectionPos, SectionMesh> sections, List<Handle> handles, int blockCount, Style style) {
        this.sections = Collections.unmodifiableSortedMap(sections);
        this.handles = List.copyOf(handles);
        this.blockCount = blockCount;
        this.style = Objects.requireNonNull(style, "style");
    }

    /** The preview of {@code plan}, with {@code handles}. */
    public static PreviewModel of(BlockPlan plan, List<Handle> handles) {
        return of(plan, handles, Style.NORMAL);
    }

    /**
     * The preview of {@code plan}, with {@code handles}, drawn in {@code style}. Stops with a
     * {@link CancellationException} if its thread is interrupted (a newer preview replaced it).
     */
    public static PreviewModel of(BlockPlan plan, List<Handle> handles, Style style) {
        Objects.requireNonNull(plan, "plan");
        List<BlockPlan.Block> blocks = plan.blocks();
        Long2ByteOpenHashMap heights = new Long2ByteOpenHashMap(blocks.size() * 2);
        Map<String, Integer> heightOfState = new HashMap<>();
        for (BlockPlan.Block block : blocks) {
            int h = heightOfState.computeIfAbsent(block.state(), PreviewModel::height8);
            if (h > 0) {
                heights.put(pack(block.x(), block.y(), block.z()), (byte) h);
            }
        }
        LongOpenHashSet[] seenEdges = {new LongOpenHashSet(), new LongOpenHashSet(), new LongOpenHashSet()};
        Set<SectionMesh.Edge> seenPartEdges = new java.util.HashSet<>();
        SortedMap<SectionPos, SectionMesh> sections = new TreeMap<>(SECTION_ORDER);
        for (Map.Entry<SectionPos, List<BlockPlan.Block>> section : plan.sections().entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("A newer preview replaced this one");
            }
            List<String> states = new ArrayList<>();
            Map<String, Integer> stateIndex = new HashMap<>();
            List<SectionMesh.Face> faces = new ArrayList<>();
            List<SectionMesh.Edge> edges = new ArrayList<>();
            for (BlockPlan.Block block : section.getValue()) {
                int h = heights.get(pack(block.x(), block.y(), block.z()));
                if (h == 0) {
                    continue; // air: nothing to draw
                }
                for (Side side : Side.values()) {
                    int next = heights.get(pack(block.x() + side.dx(), block.y() + side.dy(), block.z() + side.dz()));
                    if (hidden(side, h, next)) {
                        continue;
                    }
                    int state = stateIndex.computeIfAbsent(block.state(), s -> {
                        states.add(s);
                        return states.size() - 1;
                    });
                    faces.add(new SectionMesh.Face(block.x(), block.y(), block.z(), side, state, h));
                    addEdges(edges, seenEdges, seenPartEdges, block, side, h);
                }
            }
            if (!faces.isEmpty()) {
                sections.put(section.getKey(), new SectionMesh(section.getKey(), states, faces, edges));
            }
        }
        return new PreviewModel(sections, handles, blocks.size(), style);
    }

    /**
     * How high a block of {@code state} is drawn, in eighths: 0 for air (not drawn), 4 for a bottom slab, {@code n} for
     * {@code n} layers (snow, or TrafficCraft's slopes), 8 for anything else. Read from the state string, so core needs no
     * game code; these are the shapes road palettes use (spec roads-v1 §6.2).
     */
    public static int height8(String state) {
        int properties = state.indexOf('[');
        String id = properties < 0 ? state : state.substring(0, properties);
        if (id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air")) {
            return 0;
        }
        String props = properties < 0 ? "" : "," + state.substring(properties + 1, state.length() - 1) + ",";
        if (id.endsWith("_slab")) {
            return props.contains(",type=top,") || props.contains(",type=double,") ? 8 : 4;
        }
        int layers = props.indexOf(",layers=");
        if (layers >= 0) {
            int from = layers + ",layers=".length();
            int n = Integer.parseInt(props.substring(from, props.indexOf(',', from)));
            return StrictMath.max(1, StrictMath.min(8, n));
        }
        return 8;
    }

    /**
     * Whether a face of a block {@code h} eighths high is hidden by its neighbour on {@code side} ({@code next} eighths
     * high, 0 for none or air): the top by anything resting on a full block; the bottom by a full block; a side by a
     * neighbour at least as high.
     */
    static boolean hidden(Side side, int h, int next) {
        return switch (side) {
            case UP -> h == 8 && next > 0;
            case DOWN -> next == 8;
            default -> next >= h;
        };
    }

    /** The four edges of a face of a block {@code h} eighths high, each added only the first time any face has it. */
    private static void addEdges(List<SectionMesh.Edge> edges, LongOpenHashSet[] seen, Set<SectionMesh.Edge> seenPart, BlockPlan.Block block,
            Side side, int h) {
        if (h < 8) {
            addPartEdges(edges, seen, seenPart, block, side, h);
            return;
        }
        int[] corner = {block.x(), block.y(), block.z()};
        int a = side.axis().ordinal();
        if (side.positive()) {
            corner[a]++; // the face lies in the plane at the block's far side
        }
        int u = (a + 1) % 3;
        int v = (a + 2) % 3;
        addEdge(edges, seen, corner, u, v, 0);
        addEdge(edges, seen, corner, u, v, 1);
        addEdge(edges, seen, corner, v, u, 0);
        addEdge(edges, seen, corner, v, u, 1);
    }

    /**
     * The edges of a face of a partial block, in eighths of a block: its rectangle from the block's bottom ({@code 8y}) to
     * its top ({@code 8y + h}). Edges on the block grid share the grid's lookup; the rest are kept apart.
     */
    private static void addPartEdges(List<SectionMesh.Edge> edges, LongOpenHashSet[] grid, Set<SectionMesh.Edge> seen, BlockPlan.Block block, Side side,
            int h) {
        int x = block.x();
        int y = block.y();
        int z = block.z();
        switch (side) {
            case UP, DOWN -> {
                int lift = side == Side.UP ? h : 0;
                addPart(edges, grid, seen, x, y, z, Axis.X, lift, 8);
                addPart(edges, grid, seen, x, y, z + 1, Axis.X, lift, 8);
                addPart(edges, grid, seen, x, y, z, Axis.Z, lift, 8);
                addPart(edges, grid, seen, x + 1, y, z, Axis.Z, lift, 8);
            }
            case NORTH, SOUTH -> {
                int fz = side == Side.SOUTH ? z + 1 : z;
                addPart(edges, grid, seen, x, y, fz, Axis.X, 0, 8);
                addPart(edges, grid, seen, x, y, fz, Axis.X, h, 8);
                addPart(edges, grid, seen, x, y, fz, Axis.Y, 0, h);
                addPart(edges, grid, seen, x + 1, y, fz, Axis.Y, 0, h);
            }
            case WEST, EAST -> {
                int fx = side == Side.EAST ? x + 1 : x;
                addPart(edges, grid, seen, fx, y, z, Axis.Z, 0, 8);
                addPart(edges, grid, seen, fx, y, z, Axis.Z, h, 8);
                addPart(edges, grid, seen, fx, y, z, Axis.Y, 0, h);
                addPart(edges, grid, seen, fx, y, z + 1, Axis.Y, 0, h);
            }
        }
    }

    private static void addPart(List<SectionMesh.Edge> edges, LongOpenHashSet[] grid, Set<SectionMesh.Edge> seen, int x, int y, int z, Axis axis,
            int lift, int length) {
        int ey = lift == 8 ? y + 1 : y;
        int elift = lift == 8 ? 0 : lift;
        if (elift == 0 && length == 8) {
            if (grid[axis.ordinal()].add(pack(x, ey, z))) {
                edges.add(new SectionMesh.Edge(x, ey, z, axis));
            }
            return;
        }
        SectionMesh.Edge edge = new SectionMesh.Edge(x, ey, z, axis, elift, length);
        if (seen.add(edge)) {
            edges.add(edge);
        }
    }

    /** The edge along {@code axis} from {@code corner}, moved {@code shift} along {@code across}. */
    private static void addEdge(List<SectionMesh.Edge> edges, LongOpenHashSet[] seen, int[] corner, int axis, int across, int shift) {
        int[] start = corner.clone();
        start[across] += shift;
        if (seen[axis].add(pack(start[0], start[1], start[2]))) {
            edges.add(new SectionMesh.Edge(start[0], start[1], start[2], Axis.values()[axis]));
        }
    }

    /**
     * A position packed for the lookups: x and z in 26 bits (±33,554,432), y in 12 (±2,048). Any plan inside a Minecraft
     * world fits, with room for its neighbours and edge corners.
     */
    static long pack(int x, int y, int z) {
        if (x < -(1 << 25) || x >= 1 << 25 || z < -(1 << 25) || z >= 1 << 25 || y < -(1 << 11) || y >= 1 << 11) {
            throw new IllegalArgumentException("(" + x + ", " + y + ", " + z + ") is too far out for a preview");
        }
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** This preview with other handles (R-3: a handle aimed at or picked up); the sections stay as they are. */
    public PreviewModel withHandles(List<Handle> newHandles) {
        return new PreviewModel(this.sections, newHandles, this.blockCount, this.style);
    }

    /** What changed from {@code previous} to this preview. A change of style rebuilds every section and the handles. */
    public Diff diff(PreviewModel previous) {
        boolean restyled = this.style != previous.style;
        Set<SectionPos> rebuild = new LinkedHashSet<>();
        this.sections.forEach((pos, mesh) -> {
            if (restyled || !mesh.equals(previous.sections.get(pos))) {
                rebuild.add(pos);
            }
        });
        Set<SectionPos> drop = new LinkedHashSet<>();
        for (SectionPos pos : previous.sections.keySet()) {
            if (!this.sections.containsKey(pos)) {
                drop.add(pos);
            }
        }
        return new Diff(rebuild, drop, restyled || !this.handles.equals(previous.handles));
    }

    public Style style() {
        return this.style;
    }

    /** The sections with something to draw, in canonical order (x, z, y). */
    public SortedMap<SectionPos, SectionMesh> sections() {
        return this.sections;
    }

    public List<Handle> handles() {
        return this.handles;
    }

    /** How many blocks the plan has. */
    public int blockCount() {
        return this.blockCount;
    }

    public int faceCount() {
        return this.sections.values().stream().mapToInt(mesh -> mesh.faces().size()).sum();
    }

    public int edgeCount() {
        return this.sections.values().stream().mapToInt(mesh -> mesh.edges().size()).sum();
    }

    public boolean isEmpty() {
        return this.sections.isEmpty() && this.handles.isEmpty();
    }
}
