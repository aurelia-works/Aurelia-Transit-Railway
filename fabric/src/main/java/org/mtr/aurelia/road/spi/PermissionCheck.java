/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

import java.util.UUID;

/**
 * Whether a player holds a Stratos permission node, such as {@code stratos.roads.apply} (editor-protocol §7). Asked on
 * the server for every request. From F-6 the platform asks the server's permissions mod through fabric-permissions-api;
 * without one, each node falls back to an operator level (2, or 4 for {@code stratos.limits.bypass} and
 * {@code stratos.admin}; owner, 2026-10-12). The singleplayer world's owner holds every node.
 */
@FunctionalInterface
public interface PermissionCheck {
    boolean has(UUID player, String node);
}
