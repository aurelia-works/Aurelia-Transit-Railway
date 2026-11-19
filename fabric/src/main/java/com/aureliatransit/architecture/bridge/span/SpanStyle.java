package com.aureliatransit.architecture.bridge.span;

import java.util.Locale;

/**
 * How a stretch of rail over a gap is carried. {@link #AUTO} lets the planner pick from the gap: water, length and depth.
 */
public enum SpanStyle {
	AUTO("Auto"),
	GIRDER("Concrete box girder"),
	TRUSS("Steel truss"),
	ARCH("Stone arch viaduct"),
	CABLE_STAYED("Cable-stayed"),
	SUSPENSION("Suspension");

	private final String label;

	SpanStyle(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public SpanStyle next() {
		return values()[(ordinal() + 1) % values().length];
	}

	/** The style with this {@link #id}, or {@link #AUTO} for anything else (an old or hand-edited stack). */
	public static SpanStyle of(String id) {
		for (final SpanStyle style : values()) {
			if (style.id().equals(id)) {
				return style;
			}
		}
		return AUTO;
	}
}
