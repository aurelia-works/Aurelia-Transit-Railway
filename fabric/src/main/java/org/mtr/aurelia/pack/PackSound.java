package org.mtr.aurelia.pack;

/** A vehicle's default sound profile, named by the id of an SN profile file ({@code namespace:name}). */
public record PackSound(String vehicle, String profile) {
}
