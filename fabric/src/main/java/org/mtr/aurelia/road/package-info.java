/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * The Stratos core: all logic, in pure Java 17 (ADR-0009, spec mod-suite-v1 §4.1).
 *
 * <ul>
 *   <li>No {@code net.minecraft} or {@code net.fabricmc} imports (ArchUnit enforces this from task C-2).</li>
 *   <li>Maths uses {@code StrictMath}, explicit tie-breaks and canonical ordering wherever a result reaches
 *       a plan, so the client and the server compute identical plans.</li>
 *   <li>Block states are strings, such as {@code minecraft:stone_slab[type=bottom,waterlogged=false]},
 *       plus a DataVersion (3465 for Minecraft 1.20.1).</li>
 *   <li>The game is reached only through the interfaces in {@link org.mtr.aurelia.road.spi}.</li>
 * </ul>
 */
package org.mtr.aurelia.road;
