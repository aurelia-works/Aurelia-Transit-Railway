/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.Objects;
import java.util.Optional;

/**
 * What the player does with a tool (owner, 2026-10-05: crosshair tool mode). The platform maps the game's own use and
 * attack bindings (right and left click by default) to {@link Use} and {@link Attack}, and sends {@link Aim} every tick.
 * Each carries the block aimed at, if any is within reach, and the way the player faces: Minecraft's yaw in degrees
 * (0 faces south, 90 west; R-3, for ends set the way MTR nodes are). The one-argument constructors face south, for tools
 * that don't use the facing.
 */
public sealed interface ToolInput {
    Optional<Target> target();

    /** Minecraft's yaw, in degrees, as the game holds it (not wrapped into a range). */
    float yaw();

    /** The use binding: place a point, or confirm. */
    record Use(Optional<Target> target, float yaw) implements ToolInput {
        public Use {
            Objects.requireNonNull(target, "target");
        }

        public Use(Optional<Target> target) {
            this(target, 0F);
        }
    }

    /** The attack binding: take the last point back. */
    record Attack(Optional<Target> target, float yaw) implements ToolInput {
        public Attack {
            Objects.requireNonNull(target, "target");
        }

        public Attack(Optional<Target> target) {
            this(target, 0F);
        }
    }

    /**
     * What the elevation and shape keys do (task R-4): raise or lower the end, switch the shape, step the radius; and
     * (R-6) switch the node aid.
     */
    enum Adjustment {
        RAISE,
        LOWER,
        SHAPE,
        RADIUS_UP,
        RADIUS_DOWN,
        NODES
    }

    /** One of the elevation and shape keys (R-4), with the aim and facing at the time. */
    record Adjust(Adjustment adjustment, Optional<Target> target, float yaw) implements ToolInput {
        public Adjust {
            Objects.requireNonNull(adjustment, "adjustment");
            Objects.requireNonNull(target, "target");
        }

        public Adjust(Adjustment adjustment) {
            this(adjustment, Optional.empty(), 0F);
        }
    }

    /**
     * The player's pick in the box of the tool's {@link Question} (R-7): the choice's id. It carries no aim: the box is
     * over the game while it is open.
     */
    record Choose(String choice) implements ToolInput {
        public Choose {
            Objects.requireNonNull(choice, "choice");
        }

        @Override
        public Optional<Target> target() {
            return Optional.empty();
        }

        @Override
        public float yaw() {
            return 0F;
        }
    }

    /** Where the player aims now (every tick). */
    record Aim(Optional<Target> target, float yaw) implements ToolInput {
        public Aim {
            Objects.requireNonNull(target, "target");
        }

        public Aim(Optional<Target> target) {
            this(target, 0F);
        }
    }
}
