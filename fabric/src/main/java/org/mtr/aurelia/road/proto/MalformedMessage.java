/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

/**
 * An incoming record that breaks the encoding rules (editor-protocol §2): cut short, too long, with trailing bytes, an
 * unknown message id or enum value, a string or list over its maximum, a number out of range, or invalid UTF-8. Every
 * kind of bad input ends up as this one exception, so the platform layer can drop, log and count it in one place.
 */
public final class MalformedMessage extends Exception {
    private static final long serialVersionUID = 1L;

    public MalformedMessage(String message) {
        super(message);
    }
}
