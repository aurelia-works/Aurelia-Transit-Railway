/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.proto.MalformedMessage;
import org.mtr.aurelia.road.proto.ProtoReader;
import org.mtr.aurelia.road.proto.ProtoWriter;

/**
 * The roads module's request parameters (editor-protocol §4, {@code module = roads}): everything the server needs to
 * recompute a road's plan. The record is complete; which tools and options the planner supports grows task by task
 * ({@link RoadPlanner}).
 *
 * <p>Encoding, in {@link ProtoWriter}'s field encoding and this order: tool, profileId, preset, paletteId,
 * drivingSide, ends (each x, y, z, heading index, snapRoadId?, snapPortIndex?), vertical (shape, radius?), nodeAids,
 * overlay, junction? (templateId, size, armProfiles), targetRoadId?, manualEdits?, dashes? (phase, anchor; R-5, present
 * only when it isn't {@link Dashes#FROM_START}).
 */
public record RoadPlanParams(Tool tool, String profileId, Presets.Preset preset, String paletteId, DrivingSide drivingSide, List<End> ends,
        Vertical vertical, NodeAids nodeAids, boolean overlay, Optional<Junction> junction, Optional<UUID> targetRoadId,
        Optional<ManualEdits> manualEdits, Dashes dashes) {
    /** Profile, palette and template ids: at most 64 bytes of UTF-8. */
    public static final int MAX_ID_BYTES = 64;
    /** At most 16 ends. */
    public static final int MAX_ENDS = 16;
    /** At most 8 junction arms. */
    public static final int MAX_ARMS = 8;
    /** The furthest x or z any Minecraft world allows (its largest world border is 29,999,984). */
    public static final int MAX_XZ = 30_000_000;
    /** The lowest y any Minecraft 1.20.1 dimension allows. */
    public static final int MIN_Y = -2032;
    /** The highest y any Minecraft 1.20.1 dimension allows. */
    public static final int MAX_Y = 2031;

    public enum Tool {
        STRAIGHT,
        CURVE,
        TAPER,
        JUNCTION,
        REBUILD,
        DELETE,
        CLEAR_MARKERS
    }

    /**
     * Which side of the road traffic keeps to (spec roads-v1 §11.4). Profiles are authored for left-hand traffic (UK);
     * right-hand traffic swaps the way each carriageway runs. New roads take the world's setting (P9: the
     * {@code stratosDrivingSide} game rule, left-hand by default); a placed road keeps the side it was placed with.
     */
    public enum DrivingSide {
        LHT,
        RHT
    }

    /**
     * MTR node aids (ADR-0033), and from T-2 the hidden track itself (ADR-0040): {@code TRACK} places MTR nodes already
     * joined and the lanes' hidden track. New values go last: the encoding numbers them in this order.
     */
    public enum NodeAids {
        WOOL,
        PREORIENTED,
        NONE,
        TRACK
    }

    /** The answer to a {@code MANUAL_EDITS} question (R-7). */
    public enum ManualEdits {
        OVERWRITE,
        KEEP
    }

    /** One end of the road: a block position, its heading, and optionally the road and port it snaps to (G-10). */
    public record End(int x, int y, int z, Heading heading, Optional<UUID> snapRoadId, OptionalInt snapPortIndex) {
        public End {
            if (x < -MAX_XZ || x > MAX_XZ || z < -MAX_XZ || z > MAX_XZ) {
                throw new IllegalArgumentException("(" + x + ", " + z + ") is beyond any world's border");
            }
            if (y < MIN_Y || y > MAX_Y) {
                throw new IllegalArgumentException("y " + y + " is outside any world's height (" + MIN_Y + " to " + MAX_Y + ")");
            }
            Objects.requireNonNull(heading, "heading");
            Objects.requireNonNull(snapRoadId, "snapRoadId");
            Objects.requireNonNull(snapPortIndex, "snapPortIndex");
            if (snapPortIndex.isPresent() && snapPortIndex.getAsInt() < 0) {
                throw new IllegalArgumentException("snapPortIndex can't be negative");
            }
        }

        /** An end that snaps to nothing. */
        public static End at(int x, int y, int z, Heading heading) {
            return new End(x, y, z, heading, Optional.empty(), OptionalInt.empty());
        }
    }

    /** The vertical profile (roads-v1 §6.1). */
    public record Vertical(Shape shape, OptionalDouble radius) {
        public static final Vertical QUADRATIC = new Vertical(Shape.QUADRATIC, OptionalDouble.empty());

        public enum Shape {
            QUADRATIC,
            TWO_RADII
        }

        public Vertical {
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(radius, "radius");
            if (radius.isPresent() && !(Double.isFinite(radius.getAsDouble()) && radius.getAsDouble() > 0)) {
                throw new IllegalArgumentException("The vertical radius must be a positive number");
            }
        }
    }

    /**
     * Where the road's line markings stand in their dash pattern (task R-5; roads-v1 §7.2): {@code phase} blocks along
     * it, at its start or its end ({@code at}). A road that joins another carries on that road's pattern, so the dashes
     * keep their rhythm across the joint (owner, 2026-10-09); every other road starts its pattern at its start.
     * <ul>
     *   <li>A road whose start joins another road's end starts where that road's pattern stops: {@code at = START}.</li>
     *   <li>A road whose end joins another road's start is timed to run into it: {@code at = END}, its pattern reaching
     *       {@code phase} at its end; the planner works out its start from the road's own length.</li>
     * </ul>
     * The planner checks it against the joined road ({@code PORT_MISMATCH}). It is stored so a later road can join this
     * one without looking further back.
     */
    public record Dashes(double phase, Anchor at) {
        /** The pattern starting at the road's start: every road that doesn't join another. */
        public static final Dashes FROM_START = new Dashes(0, Anchor.START);

        /** Which end of the road {@code phase} belongs to. */
        public enum Anchor {
            START,
            END
        }

        public Dashes {
            if (!Double.isFinite(phase)) {
                throw new IllegalArgumentException("The dash phase must be finite");
            }
            Objects.requireNonNull(at, "at");
            phase = phase + 0.0; // -0.0 becomes 0.0, so equal phases are equal records
        }
    }

    /** A junction piece's template, size and arm profiles (roads-v1 §11). */
    public record Junction(String templateId, int size, List<String> armProfiles) {
        public Junction {
            checkId(templateId, "templateId");
            if (size < 0) {
                throw new IllegalArgumentException("A junction's size can't be negative");
            }
            if (armProfiles.size() > MAX_ARMS) {
                throw new IllegalArgumentException("A junction has at most " + MAX_ARMS + " arms");
            }
            armProfiles.forEach(id -> checkId(id, "armProfile"));
            armProfiles = List.copyOf(armProfiles);
        }
    }

    public RoadPlanParams {
        Objects.requireNonNull(tool, "tool");
        checkId(profileId, "profileId");
        Objects.requireNonNull(preset, "preset");
        checkId(paletteId, "paletteId");
        Objects.requireNonNull(drivingSide, "drivingSide");
        if (ends.size() > MAX_ENDS) {
            throw new IllegalArgumentException("A road has at most " + MAX_ENDS + " ends");
        }
        ends = List.copyOf(ends);
        if (tool == Tool.STRAIGHT && ends.size() != 2) {
            throw new IllegalArgumentException("A straight road has exactly 2 ends, not " + ends.size());
        }
        Objects.requireNonNull(vertical, "vertical");
        Objects.requireNonNull(nodeAids, "nodeAids");
        Objects.requireNonNull(junction, "junction");
        Objects.requireNonNull(targetRoadId, "targetRoadId");
        Objects.requireNonNull(manualEdits, "manualEdits");
        Objects.requireNonNull(dashes, "dashes");
    }

    /** As the canonical constructor, with the dash pattern starting at the road's start ({@link Dashes#FROM_START}). */
    public RoadPlanParams(Tool tool, String profileId, Presets.Preset preset, String paletteId, DrivingSide drivingSide, List<End> ends,
            Vertical vertical, NodeAids nodeAids, boolean overlay, Optional<Junction> junction, Optional<UUID> targetRoadId,
            Optional<ManualEdits> manualEdits) {
        this(tool, profileId, preset, paletteId, drivingSide, ends, vertical, nodeAids, overlay, junction, targetRoadId, manualEdits, Dashes.FROM_START);
    }

    /**
     * A straight road from {@code start} to {@code end} with the defaults: game-scale preset, the {@code vanilla}
     * palette, left-hand traffic, a quadratic vertical profile, no node aids and no overlay.
     */
    public static RoadPlanParams straight(String profileId, End start, End end) {
        return straight(profileId, start, end, Vertical.QUADRATIC);
    }

    /** As {@link #straight(String, End, End)}, with the vertical profile {@code vertical} (R-4). */
    public static RoadPlanParams straight(String profileId, End start, End end, Vertical vertical) {
        return straight(profileId, start, end, vertical, NodeAids.NONE);
    }

    /** As {@link #straight(String, End, End, Vertical)}, with the node aid {@code nodeAids} (R-6). */
    public static RoadPlanParams straight(String profileId, End start, End end, Vertical vertical, NodeAids nodeAids) {
        return new RoadPlanParams(Tool.STRAIGHT, profileId, Presets.Preset.GAME_SCALE, "vanilla", DrivingSide.LHT, List.of(start, end),
                vertical, nodeAids, false, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * A curved road (one piece, R-3) from {@code start} to {@code end}, each with the heading the road runs at it, with
     * the same defaults as {@link #straight}.
     */
    public static RoadPlanParams curve(String profileId, End start, End end) {
        return curve(profileId, start, end, Vertical.QUADRATIC);
    }

    /** As {@link #curve(String, End, End)}, with the vertical profile {@code vertical} (R-4). */
    public static RoadPlanParams curve(String profileId, End start, End end, Vertical vertical) {
        return curve(profileId, start, end, vertical, NodeAids.NONE);
    }

    /** As {@link #curve(String, End, End, Vertical)}, with the node aid {@code nodeAids} (R-6). */
    public static RoadPlanParams curve(String profileId, End start, End end, Vertical vertical, NodeAids nodeAids) {
        return new RoadPlanParams(Tool.CURVE, profileId, Presets.Preset.GAME_SCALE, "vanilla", DrivingSide.LHT, List.of(start, end),
                vertical, nodeAids, false, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** The same parameters with the node aid {@code nodeAids} (R-6). */
    public RoadPlanParams withNodeAids(NodeAids nodeAids) {
        return new RoadPlanParams(this.tool, this.profileId, this.preset, this.paletteId, this.drivingSide, this.ends, this.vertical, nodeAids,
                this.overlay, this.junction, this.targetRoadId, this.manualEdits, this.dashes);
    }

    /** The same parameters with the ends {@code ends} (R-5: a tool joining a road). */
    public RoadPlanParams withEnds(List<End> ends) {
        return new RoadPlanParams(this.tool, this.profileId, this.preset, this.paletteId, this.drivingSide, ends, this.vertical, this.nodeAids,
                this.overlay, this.junction, this.targetRoadId, this.manualEdits, this.dashes);
    }

    /** The same parameters with the palette {@code paletteId} (R-9: the tools' palette; a rebuild in another palette). */
    public RoadPlanParams withPalette(String paletteId) {
        return new RoadPlanParams(this.tool, this.profileId, this.preset, paletteId, this.drivingSide, this.ends, this.vertical, this.nodeAids,
                this.overlay, this.junction, this.targetRoadId, this.manualEdits, this.dashes);
    }

    /** The same parameters with the driving side {@code drivingSide} (P9: the world's, for a new road). */
    public RoadPlanParams withDrivingSide(DrivingSide drivingSide) {
        return new RoadPlanParams(this.tool, this.profileId, this.preset, this.paletteId, drivingSide, this.ends, this.vertical, this.nodeAids,
                this.overlay, this.junction, this.targetRoadId, this.manualEdits, this.dashes);
    }

    /** The same parameters with the dash pattern {@code dashes} (R-5). */
    public RoadPlanParams withDashes(Dashes dashes) {
        return new RoadPlanParams(this.tool, this.profileId, this.preset, this.paletteId, this.drivingSide, this.ends, this.vertical, this.nodeAids,
                this.overlay, this.junction, this.targetRoadId, this.manualEdits, dashes);
    }

    /**
     * Clearing the markers of {@code road}, a road as it was placed (R-6): the same ends, profile, preset, palette,
     * vertical profile and (R-5) dash pattern, so the planner finds the same lane nodes and surface.
     */
    public static RoadPlanParams clearMarkers(RoadPlanParams road) {
        return new RoadPlanParams(Tool.CLEAR_MARKERS, road.profileId(), road.preset(), road.paletteId(), road.drivingSide(), road.ends(),
                road.vertical(), road.nodeAids(), false, Optional.empty(), Optional.empty(), Optional.empty(), road.dashes());
    }

    /**
     * The road these parameters describe (task R-7), as a request to place it would: the same ends, layout, preset,
     * palette, driving side, vertical profile, node aid and dashes, as a curved road, with no target and no answer. A
     * straight road plans exactly the blocks of the curved road from the same ends (the same solver, R-3), so two
     * parameters are the same road when their {@code road()}s are equal: a road and the rebuilds and deletes of it.
     */
    public RoadPlanParams road() {
        return new RoadPlanParams(Tool.CURVE, this.profileId, this.preset, this.paletteId, this.drivingSide, this.ends, this.vertical, this.nodeAids,
                this.overlay, this.junction, Optional.empty(), Optional.empty(), this.dashes);
    }

    /**
     * Deleting or rebuilding a placed road (task R-7): {@code tool} ({@link Tool#DELETE} or {@link Tool#REBUILD}) for the
     * road whose id is {@code roadId} (the operation that first placed it), with its parameters as they are now
     * ({@code road}) and the player's answer to a {@code MANUAL_EDITS} question, if they have given one.
     */
    public static RoadPlanParams edit(Tool tool, UUID roadId, RoadPlanParams road, Optional<ManualEdits> answer) {
        if (tool != Tool.DELETE && tool != Tool.REBUILD) {
            throw new IllegalArgumentException("Only a delete or a rebuild edits a placed road, not " + tool);
        }
        Objects.requireNonNull(roadId, "roadId");
        return new RoadPlanParams(tool, road.profileId(), road.preset(), road.paletteId(), road.drivingSide(), road.ends(), road.vertical(),
                road.nodeAids(), road.overlay(), road.junction(), Optional.of(roadId), answer, road.dashes());
    }

    /** Whether these are a placed road's parameters: a straight or curved road's, or (R-7) a rebuild's. */
    public boolean isRoad() {
        return this.tool == Tool.STRAIGHT || this.tool == Tool.CURVE || this.tool == Tool.REBUILD;
    }

    private static void checkId(String id, String field) {
        Objects.requireNonNull(id, field);
        if (id.getBytes(StandardCharsets.UTF_8).length > MAX_ID_BYTES) {
            throw new IllegalArgumentException(field + " is longer than " + MAX_ID_BYTES + " bytes");
        }
    }

    public byte[] encode() {
        ProtoWriter out = new ProtoWriter();
        out.enumValue(this.tool).string(this.profileId, MAX_ID_BYTES).enumValue(this.preset).string(this.paletteId, MAX_ID_BYTES)
                .enumValue(this.drivingSide).count(this.ends, MAX_ENDS);
        for (End end : this.ends) {
            out.varInt(end.x()).varInt(end.y()).varInt(end.z()).varInt(end.heading().index()).optionalUuid(end.snapRoadId())
                    .bool(end.snapPortIndex().isPresent());
            end.snapPortIndex().ifPresent(out::nonNegative);
        }
        out.enumValue(this.vertical.shape()).bool(this.vertical.radius().isPresent());
        this.vertical.radius().ifPresent(out::doubleValue);
        out.enumValue(this.nodeAids).bool(this.overlay).present(this.junction);
        this.junction.ifPresent(j -> {
            out.string(j.templateId(), MAX_ID_BYTES).nonNegative(j.size()).count(j.armProfiles(), MAX_ARMS);
            j.armProfiles().forEach(id -> out.string(id, MAX_ID_BYTES));
        });
        out.optionalUuid(this.targetRoadId).present(this.manualEdits);
        this.manualEdits.ifPresent(out::enumValue);
        boolean dashed = !this.dashes.equals(Dashes.FROM_START);
        out.bool(dashed);
        if (dashed) {
            out.doubleValue(this.dashes.phase()).enumValue(this.dashes.at());
        }
        return out.toByteArray();
    }

    /** The parameters in {@code bytes}, every field bounds-checked (editor-protocol §2). */
    public static RoadPlanParams decode(byte[] bytes) throws MalformedMessage {
        ProtoReader in = new ProtoReader(bytes);
        try {
            Tool tool = in.enumValue(Tool.values(), "tool");
            String profileId = in.string(MAX_ID_BYTES, "profileId");
            Presets.Preset preset = in.enumValue(Presets.Preset.values(), "preset");
            String paletteId = in.string(MAX_ID_BYTES, "paletteId");
            DrivingSide drivingSide = in.enumValue(DrivingSide.values(), "drivingSide");
            int endCount = in.count(MAX_ENDS, "ends");
            List<End> ends = new ArrayList<>(endCount);
            for (int i = 0; i < endCount; i++) {
                int x = in.varInt(-MAX_XZ, MAX_XZ, "x");
                int y = in.varInt(MIN_Y, MAX_Y, "y");
                int z = in.varInt(-MAX_XZ, MAX_XZ, "z");
                Heading heading = Heading.of(in.varInt(0, Heading.COUNT - 1, "heading"));
                Optional<UUID> snapRoadId = in.optionalUuid();
                OptionalInt snapPortIndex = in.present() ? OptionalInt.of(in.nonNegative("snapPortIndex")) : OptionalInt.empty();
                ends.add(new End(x, y, z, heading, snapRoadId, snapPortIndex));
            }
            Vertical.Shape shape = in.enumValue(Vertical.Shape.values(), "vertical shape");
            OptionalDouble radius = in.present() ? OptionalDouble.of(in.finiteDouble("vertical radius")) : OptionalDouble.empty();
            NodeAids nodeAids = in.enumValue(NodeAids.values(), "nodeAids");
            boolean overlay = in.bool();
            Optional<Junction> junction = Optional.empty();
            if (in.present()) {
                String templateId = in.string(MAX_ID_BYTES, "templateId");
                int size = in.nonNegative("junction size");
                int armCount = in.count(MAX_ARMS, "armProfiles");
                List<String> arms = new ArrayList<>(armCount);
                for (int i = 0; i < armCount; i++) {
                    arms.add(in.string(MAX_ID_BYTES, "armProfile"));
                }
                junction = Optional.of(new Junction(templateId, size, arms));
            }
            Optional<UUID> targetRoadId = in.optionalUuid();
            Optional<ManualEdits> manualEdits = in.present() ? Optional.of(in.enumValue(ManualEdits.values(), "manualEdits")) : Optional.empty();
            Dashes dashes = Dashes.FROM_START;
            if (in.present()) {
                dashes = new Dashes(in.finiteDouble("dash phase"), in.enumValue(Dashes.Anchor.values(), "dash anchor"));
            }
            in.end();
            return new RoadPlanParams(tool, profileId, preset, paletteId, drivingSide, ends, new Vertical(shape, radius), nodeAids, overlay,
                    junction, targetRoadId, manualEdits, dashes);
        } catch (IllegalArgumentException e) {
            // A rule across fields, such as a straight road without exactly 2 ends, or a non-positive radius.
            throw new MalformedMessage("road parameters: " + e.getMessage());
        }
    }
}
