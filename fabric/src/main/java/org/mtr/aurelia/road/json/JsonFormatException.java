/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.json;

/** A Stratos JSON file that doesn't have the form it should, with a message naming the field (task G-9). */
public final class JsonFormatException extends Exception {
    public JsonFormatException(String message) {
        super(message);
    }
}
