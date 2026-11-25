/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.palette;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mtr.aurelia.road.road.line.LineChords;
import org.mtr.aurelia.road.road.raster.KerbEdge;

/**
 * One candidate for a palette role (spec roads-v1 §8; task R-9): a full block and at most one way to stand part-way up a
 * block, as the same material (ADR-0044: matched families).
 * <ul>
 *   <li>{@code half}: a bottom slab, so the surface climbs in half blocks;</li>
 *   <li>{@code layers}: a state with {@value #LAYERS} where a number 1 to 7 goes (ADR-0044's layered blocks, Slice C2),
 *       so the surface climbs in eighths. At a half it stands at 4.</li>
 * </ul>
 * States are strings in Minecraft's form ({@code minecraft:smooth_stone_slab[type=bottom,waterlogged=false]}).
 *
 * <p>From B-1 (ADR-0044's real kerbs) a family may also have:
 * <ul>
 *   <li>{@code raise}: eighths of a block it stands above the road's surface, so the Stratos UK footway and kerb stand one
 *       layer (125 mm) up. A palette whose steps can't show it leaves it level ({@link Palette#raise}).</li>
 *   <li>{@code edge}: its states have {@code facing} and {@code shape}, which Stratos sets for each column to face the road
 *       ({@link KerbEdge}). The file's values stand where a column has no edge. From B-4 and the crossings (ATR P11) the
 *       gully and dropped kerb roles may have it too, with {@code facing} only: their blocks have no {@code shape}.</li>
 * </ul>
 * From B-2 (ADR-0046's line blocks), a marking role's family may have {@code line}: its states have {@code chord}, which
 * Stratos sets for each block the line's paint touches ({@code road.line.LineTracer}). The file's value stands where a
 * column has no chord.
 */
public record Family(String full, Optional<String> half, Optional<String> layers, int raise, boolean edge, boolean line) {
    /** Where a layer count goes in {@link #layers}. */
    public static final String LAYERS = "{layers}";
    /** The highest {@link #raise}: one block. */
    public static final int MAX_RAISE = 8;
    /** The properties an {@link #edge} family's states have, which Stratos sets for each column. */
    public static final List<String> EDGE_PROPERTIES = List.of("facing", "shape");
    /** The property a {@link #line} family's states have, which Stratos sets for each block. */
    public static final String CHORD = "chord";

    /** A state: {@code namespace:path}, then optionally {@code [property=value,...]}. */
    private static final Pattern STATE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+(\\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*])?");

    public Family {
        Objects.requireNonNull(full, "full");
        Objects.requireNonNull(half, "half");
        Objects.requireNonNull(layers, "layers");
        checkState(full, "full");
        half.ifPresent(state -> checkState(state, "half"));
        if (half.isPresent() && layers.isPresent()) {
            throw new IllegalArgumentException("a family has half or layers, not both");
        }
        if (layers.isPresent()) {
            String template = layers.get();
            int at = template.indexOf(LAYERS);
            if (at < 0 || template.indexOf(LAYERS, at + 1) >= 0) {
                throw new IllegalArgumentException("layers must contain " + LAYERS + " exactly once: " + template);
            }
            checkState(template.replace(LAYERS, "1"), "layers");
        }
        if (raise < 0 || raise > MAX_RAISE) {
            throw new IllegalArgumentException("raise is in eighths of a block, 0 to " + MAX_RAISE + ": " + raise);
        }
        if (edge) {
            List<String> states = new ArrayList<>(List.of(full));
            half.ifPresent(states::add);
            layers.ifPresent(template -> states.add(template.replace(LAYERS, "1")));
            for (String state : states) {
                // facing always; shape is checked for the kerb role by PaletteFile, as a gully or dropped kerb has none.
                if (!property(EDGE_PROPERTIES.get(0)).matcher(state).find()) {
                    throw new IllegalArgumentException("an edge family's states have " + EDGE_PROPERTIES.get(0) + ": " + state);
                }
            }
        }
        if (line) {
            List<String> states = new ArrayList<>(List.of(full));
            half.ifPresent(states::add);
            layers.ifPresent(template -> states.add(template.replace(LAYERS, "1")));
            for (String state : states) {
                if (!property(CHORD).matcher(state).find()) {
                    throw new IllegalArgumentException("a line family's states have " + CHORD + ": " + state);
                }
            }
        }
    }

    /** A family with no raise, edge or line (R-9's families). */
    public Family(String full, Optional<String> half, Optional<String> layers) {
        this(full, half, layers, 0, false, false);
    }

    /** A family with {@code raise} and {@code edge} and no line (B-1's families). */
    public Family(String full, Optional<String> half, Optional<String> layers, int raise, boolean edge) {
        this(full, half, layers, raise, edge, false);
    }

    /** This family standing {@code eighths} of a block above the road. */
    public Family raised(int eighths) {
        return new Family(this.full, this.half, this.layers, eighths, this.edge, this.line);
    }

    /** This family turning to face the road ({@link #edge}). */
    public Family edged() {
        return new Family(this.full, this.half, this.layers, this.raise, true, this.line);
    }

    /** This family's states drawing a line along a chord ({@link #line}). */
    public Family lined() {
        return new Family(this.full, this.half, this.layers, this.raise, this.edge, true);
    }

    /**
     * {@code state} (one of this family's) with the line's {@code chord}, if this is a {@link #line} family and there is
     * one; else {@code state} as it is.
     */
    public String painted(String state, OptionalInt chord) {
        if (!this.line || chord.isEmpty()) {
            return state;
        }
        return property(CHORD).matcher(state).replaceFirst(CHORD + "=" + chord.getAsInt());
    }

    /**
     * {@code state} (one of this family's) turned to meet the road at {@code kerb}: its {@code facing} and {@code shape}
     * set, if this is an {@link #edge} family and there is a kerb edge; else {@code state} as it is.
     */
    public String turned(String state, Optional<KerbEdge> kerb) {
        if (!this.edge || kerb.isEmpty()) {
            return state;
        }
        String facing = property("facing").matcher(state).replaceFirst("facing=" + kerb.get().facing().id());
        return property("shape").matcher(facing).replaceFirst("shape=" + kerb.get().shape().id()); // no shape: as it is
    }

    /** A family of one full block, with no partial blocks. */
    public static Family full(String state) {
        return new Family(state, Optional.empty(), Optional.empty());
    }

    /** A full block and its bottom slab. */
    public static Family withHalf(String full, String half) {
        return new Family(full, Optional.of(half), Optional.empty());
    }

    /** A full block and its layered states ({@value #LAYERS} for 1 to 7). */
    public static Family withLayers(String full, String layers) {
        return new Family(full, Optional.empty(), Optional.of(layers));
    }

    /** Steps per block it can stand at: 8 with layers, 2 with a slab, else 1. */
    public int steps() {
        return this.layers.isPresent() ? 8 : this.half.isPresent() ? 2 : 1;
    }

    /** The partial block {@code part}/{@code steps} of a block high, {@code 0 < part < steps}, where {@code steps} divides {@link #steps()}. */
    public String partial(int part, int steps) {
        if (steps < 2 || steps > steps() || steps() % steps != 0 || part <= 0 || part >= steps) {
            throw new IllegalArgumentException("This family has no part " + part + " of " + steps);
        }
        if (steps == 2 && this.half.isPresent()) {
            return this.half.get();
        }
        return layer(part * (8 / steps));
    }

    /** The layered state with {@code count} layers, 1 to 7. */
    public String layer(int count) {
        if (count < 1 || count > 7) {
            throw new IllegalArgumentException("Layers go from 1 to 7, not " + count);
        }
        return this.layers.orElseThrow(() -> new IllegalStateException("This family has no layers")).replace(LAYERS, Integer.toString(count));
    }

    /** Every state it can give: the full block, then its slab or its seven layers. */
    public List<String> states() {
        List<String> states = new ArrayList<>();
        states.add(this.full);
        this.half.ifPresent(states::add);
        if (this.layers.isPresent()) {
            for (int count = 1; count <= 7; count++) {
                states.add(layer(count));
            }
        }
        return states;
    }

    /** Whether every one of its states has the property {@code name}, such as {@code shape}. */
    public boolean hasProperty(String name) {
        return states().stream().allMatch(state -> property(name).matcher(state).find());
    }

    /** The blocks its states belong to, such as {@code minecraft:smooth_stone_slab}. */
    public Set<String> blocks() {
        Set<String> blocks = new LinkedHashSet<>();
        states().forEach(state -> blocks.add(block(state)));
        return blocks;
    }

    /**
     * The family with every state as {@code canonical} writes it (the game's form: every property, in its order). A
     * layered state is written for each count and must come back the same each time but for the count.
     */
    public Family canonical(UnaryOperator<String> canonical) {
        String full = canonical.apply(this.full);
        Optional<String> half = this.half.map(canonical);
        Optional<String> layers = Optional.empty();
        if (this.layers.isPresent()) {
            Matcher matcher = Pattern.compile("([a-z0-9_]+)=" + Pattern.quote(LAYERS)).matcher(this.layers.get());
            if (!matcher.find()) {
                throw new IllegalArgumentException(LAYERS + " must be a property's value: " + this.layers.get());
            }
            String property = matcher.group(1);
            String first = canonical.apply(layer(1));
            String template = first.replaceFirst("(?<=[\\[,])" + Pattern.quote(property) + "=1(?=[,\\]])", Matcher.quoteReplacement(property + "=" + LAYERS));
            if (template.equals(first)) {
                throw new IllegalArgumentException("The game doesn't keep " + property + " in " + first);
            }
            for (int count = 2; count <= 7; count++) {
                String written = canonical.apply(layer(count));
                if (!written.equals(template.replace(LAYERS, Integer.toString(count)))) {
                    throw new IllegalArgumentException("The game writes " + layer(count) + " as " + written);
                }
            }
            layers = Optional.of(template);
        }
        Family written = new Family(full, half, layers, this.raise, this.edge, this.line);
        if (this.edge) {
            // The game must take every facing and shape Stratos may set, and write each as the family's own form.
            for (KerbEdge.Facing facing : KerbEdge.Facing.values()) {
                for (KerbEdge.Shape shape : KerbEdge.Shape.values()) {
                    Optional<KerbEdge> kerb = Optional.of(new KerbEdge(facing, shape));
                    List<String> mine = new ArrayList<>(List.of(this.full));
                    List<String> theirs = new ArrayList<>(List.of(written.full));
                    this.half.ifPresent(mine::add);
                    written.half.ifPresent(theirs::add);
                    if (this.layers.isPresent()) {
                        mine.add(layer(1));
                        theirs.add(written.layer(1));
                    }
                    for (int i = 0; i < mine.size(); i++) {
                        String wanted = written.turned(theirs.get(i), kerb);
                        String got = canonical.apply(turned(mine.get(i), kerb));
                        if (!got.equals(wanted)) {
                            throw new IllegalArgumentException("The game writes " + turned(mine.get(i), kerb) + " as " + got);
                        }
                    }
                }
            }
        }
        if (this.line) {
            // The game must take every chord Stratos may set (the first and the last), and write each as the family's form.
            for (int chord : new int[] {0, LineChords.COUNT - 1}) {
                List<String> mine = new ArrayList<>(List.of(this.full));
                List<String> theirs = new ArrayList<>(List.of(written.full));
                if (this.layers.isPresent()) {
                    mine.add(layer(1));
                    theirs.add(written.layer(1));
                }
                for (int i = 0; i < mine.size(); i++) {
                    String wanted = written.painted(theirs.get(i), OptionalInt.of(chord));
                    String got = canonical.apply(painted(mine.get(i), OptionalInt.of(chord)));
                    if (!got.equals(wanted)) {
                        throw new IllegalArgumentException("The game writes " + painted(mine.get(i), OptionalInt.of(chord)) + " as " + got);
                    }
                }
            }
        }
        return written;
    }

    /** Whether every block of it is in Minecraft's own namespace. */
    public boolean vanilla() {
        return blocks().stream().allMatch(block -> block.startsWith("minecraft:"));
    }

    /** The block a state belongs to: the state without its properties. */
    public static String block(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    /** {@code name=value} as a property of a state (after {@code [} or {@code ,}, before {@code ,} or {@code ]}). */
    private static Pattern property(String name) {
        return Pattern.compile("(?<=[\\[,])" + Pattern.quote(name) + "=[a-z0-9_]+(?=[,\\]])");
    }

    private static void checkState(String state, String field) {
        if (!STATE.matcher(state).matches()) {
            throw new IllegalArgumentException(field + " isn't a block state (namespace:block[property=value,...]): " + state);
        }
    }
}
