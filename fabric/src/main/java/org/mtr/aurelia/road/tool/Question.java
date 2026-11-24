/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.List;
import java.util.Objects;
import org.mtr.aurelia.road.tool.HudModel.Text;

/**
 * A question a tool puts to the player (task R-7; owner, 2026-10-09: "a small box"), such as the Edit roads tool's
 * "Rebuild, Delete or Cancel" and its hand-edit question. The platform shows it in a box over the game, which doesn't
 * pause, while the tool has one ({@link Tool#question}); the player's pick comes back as {@link ToolInput.Choose} with the
 * choice's id. Closing the box (Esc) picks the last choice, which is always the way out. Compared by value: the box opens
 * again only for a different question.
 *
 * @param title   the box's heading
 * @param body    what it asks
 * @param choices the buttons, in order; the last is the way out (Cancel)
 */
public record Question(Text title, Text body, List<Choice> choices) {
    /** One button: the id {@link ToolInput.Choose} carries back, and its label (R-9: with arguments, "Rebuild in Vanilla"). */
    public record Choice(String id, Text label) {
        public Choice {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
        }

        /** A button labelled by the lang key {@code labelKey}. */
        public Choice(String id, String labelKey) {
            this(id, Text.of(labelKey));
        }
    }

    public Question {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        choices = List.copyOf(choices);
        if (choices.size() < 2) {
            throw new IllegalArgumentException("A question has at least two choices, the last its way out");
        }
    }

    /** The choice closing the box picks: the last. */
    public Choice wayOut() {
        return this.choices.get(this.choices.size() - 1);
    }
}
