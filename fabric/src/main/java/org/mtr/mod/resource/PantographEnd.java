package org.mtr.mod.resource;

/**
 * Which end of the car a pantograph part rises at, relative to the direction of travel.
 * {@code FRONT} rises when the car's first end leads, {@code REAR} when the second end leads (the vehicle is reversed),
 * and {@code BOTH} either way. {@code DEFAULT} uses the end chosen in the Aurelia panel.
 */
public enum PantographEnd {
	DEFAULT, FRONT, REAR, BOTH
}
