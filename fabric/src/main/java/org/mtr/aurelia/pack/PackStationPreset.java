package org.mtr.aurelia.pack;

/**
 * A station the generator can build. {@code description} uses the same words as the wrench and Station Studio, so a
 * pack preset goes through the same parser, design rules and checks as a typed description. {@code style} is an
 * optional hint for menus.
 */
public record PackStationPreset(String id, String name, String description, String style) {
}
