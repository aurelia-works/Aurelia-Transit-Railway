/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.Palettes;
import org.mtr.aurelia.road.tool.HudModel.Text;

/**
 * The road tools' shared settings (task R-4; owner, 2026-10-06): the vertical shape sloped roads get, and Two radii's
 * vertical radius. Kept when a tool is put away, as tool settings are (R-1). Client thread only.
 * <ul>
 *   <li><b>The layout</b> (G-4+; owner, 2026-10-07): City street to start; the hub's Layout list chooses another built-in
 *       profile.</li>
 *   <li><b>Quadratic</b> (MTR's default) is the starting shape; Home switches to <b>Two radii</b> (MTR's constant
 *       grade with curved ends) and back.</li>
 *   <li>The radius starts at {@link #DEFAULT_RADIUS}. Insert and Delete change it by {@link #STEP}, never below
 *       {@link #STEP}; a typed radius can be anything from {@link #MIN_RADIUS} to {@link #MAX_RADIUS}, to a tenth (MTR's
 *       rail screen takes tenths). MTR shortens it on a piece too short or steep for it, and so does the planner.</li>
 *   <li><b>The node aid</b> (R-6, T-2; owner, 2026-10-08): <b>Track</b> to start with, where it can be built (the
 *       server has Stratos Transit and the game has MTR's node block); otherwise blue-wool markers. {@code '} goes
 *       Track → blue wool → MTR nodes placed already facing the right way → none → Track, leaving out Track where it
 *       can't be built and MTR nodes where the game has no node block.</li>
 *   <li><b>The palette</b> (R-9; owner, 2026-10-10): Stratos UK to start (B-1, ADR-0044); the hub's palette button steps
 *       through the server's palettes. A palette chosen on another server and missing here stands for Stratos UK.</li>
 * </ul>
 */
public final class RoadSettings {
    public static final double DEFAULT_RADIUS = 100;
    public static final double STEP = 10;
    public static final double MIN_RADIUS = 0.1;
    public static final double MAX_RADIUS = 100_000;

    private final BooleanSupplier mtrNodes;
    private final BooleanSupplier track;
    private final Supplier<Palettes> palettes;
    private String paletteId = BuiltInPalettes.DEFAULT_ID;
    private RoadProfile profile = RoadProfiles.CITY_17;
    private RoadPlanParams.Vertical.Shape shape = RoadPlanParams.Vertical.Shape.QUADRATIC;
    private double radius = DEFAULT_RADIUS;
    /** The aid chosen; Track stands for blue wool while track can't be built. */
    private RoadPlanParams.NodeAids nodeAids = RoadPlanParams.NodeAids.TRACK;

    /** Settings for a game without MTR (tests). */
    public RoadSettings() {
        this(() -> false);
    }

    /** Settings where {@code mtrNodes} says whether the game has MTR's node block, and track can never be built. */
    public RoadSettings(BooleanSupplier mtrNodes) {
        this(mtrNodes, () -> false);
    }

    /**
     * Settings where {@code mtrNodes} says whether the game has MTR's node block, and {@code track} whether the server can
     * build track (its {@code track} capability, T-2). Both are asked each time the aid is read or changed.
     */
    public RoadSettings(BooleanSupplier mtrNodes, BooleanSupplier track) {
        this(mtrNodes, track, () -> Palettes.BUILT_IN);
    }

    /** As {@link #RoadSettings(BooleanSupplier, BooleanSupplier)}, where {@code palettes} gives the palettes roads may use (R-9: the server's). */
    public RoadSettings(BooleanSupplier mtrNodes, BooleanSupplier track, Supplier<Palettes> palettes) {
        this.mtrNodes = Objects.requireNonNull(mtrNodes, "mtrNodes");
        this.track = Objects.requireNonNull(track, "track");
        this.palettes = Objects.requireNonNull(palettes, "palettes");
    }

    /** The palette roads get: the one chosen, while this game has it; else the default (Stratos UK), else Vanilla. */
    public Palette palette() {
        Palettes available = this.palettes.get();
        return available.byId(this.paletteId)
                .or(() -> available.byId(BuiltInPalettes.DEFAULT_ID))
                .or(() -> available.byId(BuiltInPalettes.VANILLA_ID))
                .orElse(BuiltInPalettes.vanilla());
    }

    /** The palettes the player can choose from, in the hub's order. */
    public Palettes palettes() {
        return this.palettes.get();
    }

    /** Chooses the palette {@code id} (the hub's palette button); false, changing nothing, if this game hasn't got it. */
    public boolean setPalette(String id) {
        if (this.palettes.get().byId(id).isEmpty()) {
            return false;
        }
        this.paletteId = id;
        return true;
    }

    /** The next palette in the hub's order, wrapping round. */
    public void cyclePalette() {
        this.paletteId = this.palettes.get().next(palette().id());
    }

    /**
     * How the HUD names {@code palette}: a built-in one by its lang key ({@code stratos.palette.<id>}), any other by the
     * name its file gives, else its id.
     */
    public static Text name(Palette palette) {
        return BuiltInPalettes.isBuiltIn(palette.id()) ? Text.of("stratos.palette." + palette.id())
                : Text.of("stratos.palette.named", palette.name().orElse(palette.id()));
    }

    /** How the HUD names the palette with id {@code id} among {@code palettes}: as {@link #name(Palette)}, else its id. */
    public static Text name(String id, Palettes palettes) {
        return palettes.byId(id).map(RoadSettings::name).orElse(Text.of("stratos.palette.named", id));
    }

    /** The layout roads get: a built-in profile, City street 17 to start. */
    public RoadProfile profile() {
        return this.profile;
    }

    /** Chooses the layout (the hub's Layout list). */
    public void setProfile(RoadProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    /**
     * The node aid roads get: Track, blue wool, MTR nodes or none. Track only where it can be built, and MTR nodes only
     * where the game has them (UI-1a: a choice remembered from another game), else blue wool.
     */
    public RoadPlanParams.NodeAids nodeAids() {
        return switch (this.nodeAids) {
            case TRACK -> trackAvailable() ? RoadPlanParams.NodeAids.TRACK : RoadPlanParams.NodeAids.WOOL;
            case PREORIENTED -> mtrNodesAvailable() ? RoadPlanParams.NodeAids.PREORIENTED : RoadPlanParams.NodeAids.WOOL;
            case WOOL, NONE -> this.nodeAids;
        };
    }

    /** Whether Track can be chosen: the server can build track and the game has MTR's node block. */
    public boolean trackAvailable() {
        return this.track.getAsBoolean() && this.mtrNodes.getAsBoolean();
    }

    /** Whether MTR nodes can be chosen: the game has MTR's node block. */
    public boolean mtrNodesAvailable() {
        return this.mtrNodes.getAsBoolean();
    }

    /**
     * Chooses the node aid (UI-1a: the toolbar's Nodes pop-up). False, changing nothing, for Track where it can't be built
     * or MTR nodes where the game hasn't got them.
     */
    public boolean setNodeAids(RoadPlanParams.NodeAids aids) {
        boolean usable = switch (Objects.requireNonNull(aids, "aids")) {
            case TRACK -> trackAvailable();
            case PREORIENTED -> mtrNodesAvailable();
            case WOOL, NONE -> true;
        };
        if (usable) {
            this.nodeAids = aids;
        }
        return usable;
    }

    /** The layout, palette and node aid chosen, to keep across restarts (UI-1a). The aid is the one chosen, not the one in use. */
    public RoadChoices choices() {
        return new RoadChoices(this.profile.id(), this.paletteId, this.nodeAids);
    }

    /**
     * Takes back choices kept across restarts (UI-1a). A layout this game hasn't got leaves the layout as it was; a
     * palette or node aid this server can't give is kept, and stands for the default until one that can is joined, as
     * {@link #palette()} and {@link #nodeAids()} say.
     */
    public void restore(RoadChoices choices) {
        RoadProfiles.byId(choices.layoutId()).ifPresent(this::setProfile);
        this.paletteId = choices.paletteId();
        this.nodeAids = choices.nodeAids();
    }

    /** Track (where it can be built) → blue wool → MTR nodes (only when the game has them) → none → Track. */
    public void cycleNodeAids() {
        this.nodeAids = switch (nodeAids()) {
            case TRACK -> RoadPlanParams.NodeAids.WOOL;
            case WOOL -> this.mtrNodes.getAsBoolean() ? RoadPlanParams.NodeAids.PREORIENTED : RoadPlanParams.NodeAids.NONE;
            case PREORIENTED -> RoadPlanParams.NodeAids.NONE;
            case NONE -> trackAvailable() ? RoadPlanParams.NodeAids.TRACK : RoadPlanParams.NodeAids.WOOL;
        };
    }

    public RoadPlanParams.Vertical.Shape shape() {
        return this.shape;
    }

    public double radius() {
        return this.radius;
    }

    /** The vertical profile a road gets: Quadratic, or Two radii with the radius. */
    public RoadPlanParams.Vertical vertical() {
        return this.shape == RoadPlanParams.Vertical.Shape.QUADRATIC ? RoadPlanParams.Vertical.QUADRATIC
                : new RoadPlanParams.Vertical(RoadPlanParams.Vertical.Shape.TWO_RADII, OptionalDouble.of(this.radius));
    }

    /** Quadratic to Two radii, or back. */
    public void toggleShape() {
        this.shape = this.shape == RoadPlanParams.Vertical.Shape.QUADRATIC ? RoadPlanParams.Vertical.Shape.TWO_RADII
                : RoadPlanParams.Vertical.Shape.QUADRATIC;
    }

    /** {@link #STEP} up ({@code up}) or down; down stops at {@link #STEP}, and a radius already below it stays. */
    public void stepRadius(boolean up) {
        double next = up ? StrictMath.min(MAX_RADIUS, this.radius + STEP) : this.radius - STEP;
        if (!up && next < STEP) {
            next = this.radius > STEP ? STEP : this.radius;
        }
        this.radius = tenths(next);
    }

    /**
     * Sets a typed radius and switches to Two radii. Returns false (and changes nothing) unless {@code value} is a number
     * from {@link #MIN_RADIUS} to {@link #MAX_RADIUS}; it is rounded to a tenth.
     */
    public boolean setRadius(double value) {
        if (!(value >= MIN_RADIUS && value <= MAX_RADIUS)) {
            return false;
        }
        this.radius = StrictMath.max(MIN_RADIUS, tenths(value));
        this.shape = RoadPlanParams.Vertical.Shape.TWO_RADII;
        return true;
    }

    /**
     * Takes {@code road}'s layout, palette, node aid, shape and radius (R-10; owner, 2026-10-13: the hub follows the road
     * being edited, and keeps them for the next road). A palette or layout this game hasn't got stays as it was.
     */
    public void follow(RoadPlanParams road) {
        RoadProfiles.byId(road.profileId()).ifPresent(this::setProfile);
        setPalette(road.paletteId());
        this.nodeAids = road.nodeAids();
        this.shape = road.vertical().shape();
        road.vertical().radius().ifPresent(r -> this.radius = r);
    }

    /** The radius as the HUD and MTR's screen show it: whole numbers without decimals, else one decimal. */
    public static String format(double radius) {
        return radius == StrictMath.rint(radius) ? String.format(Locale.ROOT, "%.0f", radius) : String.format(Locale.ROOT, "%.1f", radius);
    }

    private static double tenths(double value) {
        return StrictMath.rint(value * 10) / 10;
    }
}
