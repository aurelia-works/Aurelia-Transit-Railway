package org.mtr.aurelia.road.road.junction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.LineMarking;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.Section;
import org.mtr.aurelia.road.road.line.LineChords;
import org.mtr.aurelia.road.road.raster.Column;

/**
 * The surface, kerbs and markings a junction piece owns (roads-v1 §11.1: "the template owns its surface, kerbs and
 * markings"), column by column in its own frame (block {@code (0, 0)} its centre, x east, z south), built from upstream's
 * spec; upstream has no junction raster yet.
 *
 * <p><b>The carriageway</b> is every column whose centre is in:
 * <ul>
 *   <li>an arm's carriageway (its lanes and centre, the road's own width), from its port in to where it meets the other
 *       arms (T-junctions and crossroads) or the ring (roundabouts);</li>
 *   <li>a corner between two arms at right angles, inside the kerb line rounded off at the corner radius (CD 123: the
 *       template's size, 6, 10 or 15): the arc of that radius tangent to both kerb lines;</li>
 *   <li>a roundabout's circulating carriageway, one lane wide either side of the ring's radius, and the corners where
 *       each arm meets it, rounded off at {@link #ROUNDABOUT_CORNER} (CD 116's smallest entry radius).</li>
 * </ul>
 * Outside it each column takes the band its distance from the carriageway gives across the arm's own layout (kerb,
 * then footway), so an arm ends at its port exactly as the road joined there does; a roundabout's central island is kerb
 * round a paved footway. The rasteriser turns kerbs to face the road where the palette's kerb does
 * ({@code Rasteriser.junction}).
 *
 * <p><b>Markings</b> (roads-v1 §7.2: a one-block approximation in v1): a give-way line across the entry lane of each
 * minor arm where it meets the major road (the T-junction's stem; a crossroads' north and south arms) and of every
 * roundabout arm where it meets the ring, one block on, one off, as white line blocks across the lane.
 */
public final class JunctionFootprint {
    /** The corner radius where a roundabout's arms meet its ring, in blocks (CD 116: entry radius 10 to 100 m). */
    public static final int ROUNDABOUT_CORNER = 10;

    /** The columns of each template worked out so far: the tool asks on every aim. */
    private static final Map<JunctionTemplate, Map<BlockStep, Column>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private JunctionFootprint() {
    }

    /** One arm, in the template's frame: its way out, its port's distance from the centre, its carriageway and its outer sections. */
    private record Arm(double ux, double uz, double length, double half, List<Section> outer, boolean minor, int lane) {
        double along(double x, double z) {
            return x * this.ux + z * this.uz;
        }

        /** Positive on the arm's right, seen leaving the junction. */
        double side(double x, double z) {
            return x * -this.uz + z * this.ux;
        }
    }

    /** A rounded corner: the arc's centre and radius, and the two tangent points that bound it. */
    private record Corner(double cx, double cz, double r, double ax, double az, double bx, double bz) {
        /** Whether the direction from the centre to {@code (x, z)} lies between those to the tangent points. */
        boolean within(double x, double z) {
            double px = x - this.cx;
            double pz = z - this.cz;
            double ax = this.ax - this.cx;
            double az = this.az - this.cz;
            double bx = this.bx - this.cx;
            double bz = this.bz - this.cz;
            double ab = ax * bz - az * bx;
            return ab >= 0 ? ax * pz - az * px >= 0 && px * bz - pz * bx >= 0 : ax * pz - az * px <= 0 && px * bz - pz * bx <= 0;
        }
    }

    /**
     * The columns of {@code template}, by block in its frame: each with its band, and paint where a give-way line is. A
     * column outside every arm's port ({@code |x|} or {@code |z|} past it) isn't the junction's.
     */
    public static Map<BlockStep, Column> columns(JunctionTemplate template) {
        Map<BlockStep, Column> known = CACHE.get(template);
        if (known != null) {
            return known;
        }
        Map<BlockStep, Column> columns = java.util.Collections.unmodifiableMap(work(template));
        CACHE.putIfAbsent(template, columns);
        return columns;
    }

    private static Map<BlockStep, Column> work(JunctionTemplate template) {
        boolean roundabout = template.templateId().equals(JunctionTemplates.ROUNDABOUT);
        List<Arm> arms = arms(template, roundabout);
        double ring = template.size();
        double laneHalf = arms.isEmpty() ? 2.5 : laneWidth(template.arms().get(0).profileId()) / 2.0;
        double inner = roundabout ? ring - laneHalf : 0;
        double outerRing = roundabout ? ring + laneHalf : 0;
        List<Corner> corners = corners(arms, roundabout, outerRing, roundabout ? ROUNDABOUT_CORNER : template.size());
        int reach = 0;
        for (Arm arm : arms) {
            reach = (int) StrictMath.max(reach, StrictMath.ceil(arm.length()));
        }
        Map<BlockStep, Column> columns = new LinkedHashMap<>();
        for (int z = -reach; z <= reach; z++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Stopped by the plan time limit");
            }
            for (int x = -reach; x <= reach; x++) {
                double d = Double.POSITIVE_INFINITY;
                Arm nearest = arms.get(0);
                double nearestAlong = Double.NEGATIVE_INFINITY;
                for (Arm arm : arms) {
                    double along = arm.along(x, z);
                    double start = roundabout ? 0 : innerReach(arm, arms);
                    double dx = StrictMath.max(0, StrictMath.max(start - along, along - arm.length()));
                    double dz = StrictMath.max(0, StrictMath.abs(arm.side(x, z)) - arm.half());
                    d = StrictMath.min(d, StrictMath.hypot(dx, dz));
                    if (along > nearestAlong) {
                        nearestAlong = along;
                        nearest = arm;
                    }
                }
                double radius = StrictMath.hypot(x, z);
                if (roundabout) {
                    d = StrictMath.min(d, StrictMath.max(0, radius - outerRing));
                }
                for (Corner corner : corners) {
                    if (corner.within(x, z)) {
                        d = StrictMath.min(d, StrictMath.max(0, corner.r() - StrictMath.hypot(x - corner.cx(), z - corner.cz())));
                    }
                }
                boolean island = roundabout && radius < inner;
                Optional<Band> band;
                if (island) {
                    band = Optional.of(inner - radius <= 1 ? Band.KERB : Band.FOOTWAY); // the central island: kerb round a paved middle
                } else if (d == 0) {
                    band = Optional.of(Band.LANE);
                } else {
                    band = outside(nearest.outer(), d);
                }
                if (band.isEmpty()) {
                    continue;
                }
                columns.put(new BlockStep(x, z), new Column(band.get(), false, false));
            }
        }
        giveWay(arms, roundabout, outerRing, columns);
        return columns;
    }

    private static List<Arm> arms(JunctionTemplate template, boolean roundabout) {
        List<Arm> arms = new ArrayList<>();
        for (int i = 0; i < template.arms().size(); i++) {
            JunctionTemplate.Arm arm = template.arms().get(i);
            RoadProfile profile = RoadProfiles.byId(arm.profileId()).orElseThrow();
            double length = StrictMath.hypot(arm.x(), arm.z());
            double ux = arm.x() / length;
            double uz = arm.z() / length;
            boolean minor = roundabout || minor(template.templateId(), i);
            JunctionTemplate.Node entry = template.nodes().get(arm.entry());
            int lane = (int) StrictMath.round(entry.x() * -uz + entry.z() * ux); // the entry lane's side: + right, − left
            arms.add(new Arm(ux, uz, length, carriageway(profile) / 2.0, outer(profile), minor, lane));
        }
        return arms;
    }

    /** The arms that give way: a T-junction's stem (its third arm), and a crossroads' north and south arms. */
    static boolean minor(String templateId, int arm) {
        return switch (templateId) {
            case JunctionTemplates.T_JUNCTION -> arm == 2;
            case JunctionTemplates.CROSSROADS -> arm == 1 || arm == 3;
            default -> true;
        };
    }

    /** How far towards the centre an arm's carriageway runs past it: to the far edge of the widest arm across it. */
    private static double innerReach(Arm arm, List<Arm> arms) {
        double reach = 0;
        for (Arm other : arms) {
            if (StrictMath.abs(arm.ux() * other.ux() + arm.uz() * other.uz()) < 0.5) { // at right angles
                reach = StrictMath.max(reach, other.half());
            }
        }
        return -reach;
    }

    /**
     * The rounded corners: between two arms at right angles (T-junctions and crossroads), radius {@code radius}, tangent
     * to both kerb lines; on a roundabout between each arm and the ring's outer edge, tangent to both.
     */
    private static List<Corner> corners(List<Arm> arms, boolean roundabout, double outerRing, double radius) {
        List<Corner> corners = new ArrayList<>();
        for (Arm a : arms) {
            if (roundabout) {
                for (int sign : new int[] {-1, 1}) {
                    double offset = a.half() + radius; // the arc's centre, off the arm's axis
                    double along = StrictMath.sqrt(StrictMath.max(0, (outerRing + radius) * (outerRing + radius) - offset * offset));
                    double cx = a.ux() * along - a.uz() * offset * sign;
                    double cz = a.uz() * along + a.ux() * offset * sign;
                    double tx = a.ux() * along - a.uz() * a.half() * sign; // on the kerb line
                    double tz = a.uz() * along + a.ux() * a.half() * sign;
                    double scale = outerRing / (outerRing + radius); // on the ring's edge, towards the centre
                    corners.add(new Corner(cx, cz, radius, tx, tz, cx * scale, cz * scale));
                }
                continue;
            }
            for (Arm b : arms) {
                double cross = a.ux() * b.uz() - a.uz() * b.ux();
                if (cross <= 0.5) {
                    continue; // b isn't a quarter turn clockwise of a; each corner once
                }
                // In the corner between a and b: b's kerb line is a.half along... a, offset across b; and the other way.
                double sa = b.half() + radius; // along a
                double sb = a.half() + radius; // along b
                double cx = a.ux() * sa + b.ux() * sb;
                double cz = a.uz() * sa + b.uz() * sb;
                double ax = a.ux() * sa + b.ux() * a.half(); // on a's kerb line
                double az = a.uz() * sa + b.uz() * a.half();
                double bx = a.ux() * b.half() + b.ux() * sb; // on b's kerb line
                double bz = a.uz() * b.half() + b.uz() * sb;
                corners.add(new Corner(cx, cz, radius, ax, az, bx, bz));
            }
        }
        return corners;
    }

    /** Paints the give-way lines: across each minor arm's entry lane, on the first row of it outside the road it joins. */
    private static void giveWay(List<Arm> arms, boolean roundabout, double outerRing, Map<BlockStep, Column> columns) {
        for (Arm arm : arms) {
            if (!arm.minor()) {
                continue;
            }
            int lane = arm.lane();
            double mouth = roundabout ? StrictMath.sqrt(StrictMath.max(0, outerRing * outerRing - lane * lane)) : -innerReach(arm, arms);
            int row = (int) StrictMath.floor(mouth) + 1;
            int ix = (int) StrictMath.round(arm.ux());
            int iz = (int) StrictMath.round(arm.uz());
            boolean acrossX = iz != 0; // the line runs across the arm: east–west on a north–south arm
            int chord = acrossX ? LineChords.index(LineChords.WEST, LineChords.snap(0.5), LineChords.EAST, LineChords.snap(0.5))
                    : LineChords.index(LineChords.NORTH, LineChords.snap(0.5), LineChords.SOUTH, LineChords.snap(0.5));
            int sign = lane >= 0 ? 1 : -1;
            for (int across = 0; across < arm.half(); across++) {
                if (across % 2 != 0) {
                    continue; // one block on, one off
                }
                int s = sign * across;
                BlockStep at = new BlockStep(ix * row - iz * s, iz * row + ix * s);
                Column column = columns.get(at);
                if (column != null && column.band() == Band.LANE) {
                    columns.put(at, column.painted(Optional.of(LineMarking.Colour.WHITE), OptionalInt.of(chord)));
                }
            }
        }
    }

    /** The band at {@code d} blocks out from the carriageway's edge across {@code outer} (kerb, footway…), or none past them. */
    private static Optional<Band> outside(List<Section> outer, double d) {
        double edge = 0;
        for (Section section : outer) {
            edge += section.width();
            if (d <= edge) {
                return Optional.of(section.band());
            }
        }
        return Optional.empty();
    }

    /** The carriageway's width: from the outer edge of the first lane to the outer edge of the last. */
    private static int carriageway(RoadProfile profile) {
        int start = 0;
        int first = -1;
        int last = 0;
        for (Section section : profile.sections()) {
            if (section.band() == Band.LANE) {
                first = first < 0 ? start : first;
                last = start + section.width();
            }
            start += section.width();
        }
        return last - first;
    }

    /** The sections outside the last lane, outward (kerb, footway…). */
    private static List<Section> outer(RoadProfile profile) {
        List<Section> sections = profile.sections();
        int last = 0;
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).band() == Band.LANE) {
                last = i;
            }
        }
        return List.copyOf(sections.subList(last + 1, sections.size()));
    }

    private static int laneWidth(String profileId) {
        for (Section section : RoadProfiles.byId(profileId).orElseThrow().sections()) {
            if (section.band() == Band.LANE) {
                return section.width();
            }
        }
        return 5;
    }
}
