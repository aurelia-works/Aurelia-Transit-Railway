/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * The preview (spec mod-suite-v1 §6.3), from task F-4: what the client draws for a block plan, worked out in core so it
 * is unit-tested and identical everywhere.
 * <ul>
 *   <li>{@link org.mtr.aurelia.road.preview.PreviewModel}: per 16³ section, the plan's outside faces with their
 *       states and the block-grid edges around them (each edge once), plus the handles. {@code diff} says which sections
 *       changed. From R-2 a model has a style: a refused plan is drawn in the error colour.</li>
 *   <li>{@link org.mtr.aurelia.road.preview.Preview}: the preview on screen, which tells the platform's
 *       {@code PreviewSink} only what changed.</li>
 * </ul>
 */
package org.mtr.aurelia.road.preview;
