/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a tool's HUD says (spec mod-suite-v1 §6.2): its title, which step the player is on and what to do, a fixed set of
 * labelled values, an optional notice and what each binding does now. Every text is a lang key with its arguments, or a
 * refusal or warning code, so core holds no Minecraft text; the platform translates them and names the player's actual
 * key bindings.
 *
 * <p>The HUD keeps a fixed size (owner, 2026-10-05), so a tool gives the same lines in every state, with
 * {@link #NONE} where a value isn't known yet.
 */
public record HudModel(String titleKey, int step, int steps, Text state, List<Line> lines, Optional<Notice> notice, List<Hint> hints) {
    /** A lang key and its arguments, in order. */
    public record Text(String key, List<String> args) {
        public Text {
            Objects.requireNonNull(key, "key");
            args = List.copyOf(args);
        }

        public static Text of(String key, String... args) {
            return new Text(key, List.of(args));
        }
    }

    /** "No value yet" (a dash). */
    public static final Text NONE = Text.of("stratos.tool.value.none");

    /** How a value is coloured: normally, in the warning colour, or (R-6) in the success colour. */
    public enum Tone {
        NORMAL,
        WARNING,
        GOOD
    }

    /**
     * A labelled value, such as "Length: 20.0 blocks". A warning value is shown in the warning colour, a good one (R-6:
     * a node placed right) in the success colour. From R-6 a label can have arguments ("Lane 2, node 1").
     */
    public record Line(Text label, Text value, Tone tone) {
        public Line {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(tone, "tone");
        }

        /** A line whose label is the lang key {@code labelKey}, in the warning colour or not. */
        public Line(String labelKey, Text value, boolean warning) {
            this(Text.of(labelKey), value, warning ? Tone.WARNING : Tone.NORMAL);
        }

        /** The label's lang key. */
        public String labelKey() {
            return this.label.key();
        }

        public boolean warning() {
            return this.tone == Tone.WARNING;
        }
    }

    /** How a notice is shown: errors in {@code status-error}, warnings in {@code status-warning}, news in the accent. */
    public enum Level {
        INFO,
        WARNING,
        ERROR
    }

    /**
     * A refusal or warning code and its named arguments, in order (roads-v1 §12). The platform words it from the lang
     * file, as it does the server's refusals ({@code stratos.road.reject.<code>}, {@code stratos.reject.<code>},
     * {@code stratos.road.warn.<code>}).
     */
    public record Reason(String code, Map<String, String> args) {
        public Reason {
            Objects.requireNonNull(code, "code");
            args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
        }
    }

    /** A line under the panel (R-1's strip): a text or a reason, never both. */
    public record Notice(Level level, Optional<Text> text, Optional<Reason> reason) {
        public Notice {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(reason, "reason");
            if (text.isPresent() == reason.isPresent()) {
                throw new IllegalArgumentException("A notice has either a text or a reason");
            }
        }

        public static Notice text(Level level, Text text) {
            return new Notice(level, Optional.of(text), Optional.empty());
        }

        public static Notice reason(Level level, String code, Map<String, String> args) {
            return new Notice(level, Optional.empty(), Optional.of(new Reason(code, args)));
        }
    }

    /**
     * Which binding a hint names; the HUD lists them in this order (undo and redo share a row, with the node-aid key from
     * R-6; from R-4, raise, lower and the shape share one, and the radius keys another).
     */
    public enum Action {
        USE,
        ATTACK,
        TOOL_KEY,
        UNDO,
        REDO,
        NODES,
        RAISE,
        LOWER,
        SHAPE,
        RADIUS_UP,
        RADIUS_DOWN,
        RADIUS_TYPE,
        CROSSINGS
    }

    /** What a binding does now. A binding without a hint does nothing in this state. */
    public record Hint(Action action, String textKey) {
        public Hint {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(textKey, "textKey");
        }
    }

    public HudModel {
        Objects.requireNonNull(titleKey, "titleKey");
        if (steps < 0 || step < 0 || step > steps) {
            throw new IllegalArgumentException("Step " + step + " of " + steps + " isn't possible");
        }
        Objects.requireNonNull(state, "state");
        lines = List.copyOf(lines);
        Objects.requireNonNull(notice, "notice");
        hints = List.copyOf(hints);
    }

    /** The hint for {@code action}, if the binding does anything now. */
    public Optional<Hint> hint(Action action) {
        return this.hints.stream().filter(hint -> hint.action() == action).findFirst();
    }

    /** The line whose label is the lang key {@code labelKey}, if this HUD has one (R-9: callers find rows by label, not position). */
    public Optional<Line> line(String labelKey) {
        return this.lines.stream().filter(line -> line.labelKey().equals(labelKey)).findFirst();
    }
}
