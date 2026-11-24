/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.palette;

/** Why a palette file can't be used (task R-9): its message names the file's problem, for the log. */
public final class PaletteException extends Exception {
    private static final long serialVersionUID = 1L;

    public PaletteException(String message) {
        super(message);
    }
}
