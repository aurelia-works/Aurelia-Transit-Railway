package com.aureliatransit.architecture.station;

import java.util.Locale;

/**
 * The measured numbers of the real stations in {@code docs/research-2026-11-07} (ATA-research), used by
 * {@link StationAudit} to report how far a generated station is from the real one. Only values the sources state are set;
 * an unknown dimension is 0 and is left out of the report rather than guessed.
 *
 * @param label        the name on the research sheet ("Amsterdam Centraal")
 * @param tracks       platform tracks the real station has, or 0 when the sources disagree or say nothing
 * @param lengthMetres the longest platform in metres, or 0 when unknown
 * @param widthMetres  a typical platform width in metres, or 0 when unknown
 */
public enum MeasuredStation {
	AMSTERDAM("Amsterdam Centraal", 15, 695, 13.5),
	ANTWERP("Antwerpen-Centraal", 14, 400, 9),
	BANGKOK("Krung Thep Aphiwat", 14, 0, 0),
	BEIJING("Beijing South", 24, 0, 0),
	BERLIN("Berlin Hauptbahnhof", 18, 430, 15),
	COVENTRY("Coventry", 4, 0, 0),
	FRANKFORD("Frankford Transportation Center", 2, 0, 0),
	FRANKFURT("Frankfurt (Main) Hbf", 25, 240, 0),
	GRAND_CENTRAL("Grand Central Terminal", 67, 0, 0),
	HAMBURG("Hamburg Hauptbahnhof", 14, 0, 0),
	HONG_KONG("West Kowloon", 15, 0, 0),
	KL_SENTRAL("KL Sentral", 10, 0, 0),
	KUALA_LUMPUR("Kuala Lumpur", 4, 0, 0),
	LEIDSCHE_RIJN("Utrecht Leidsche Rijn", 4, 0, 0),
	MILAN("Milano Centrale", 24, 0, 0),
	MUMBAI("Mumbai CSMT", 40, 0, 0),
	MUNICH("Munchen Ost", 14, 0, 0),
	PARIS("Paris Nord", 0, 0, 0),
	ROSENTHALER_PLATZ("Rosenthaler Platz", 2, 130, 8),
	SIXTY_NINTH_STREET("69th Street", 8, 0, 0),
	ST_PANCRAS("St Pancras", 15, 0, 0),
	SURBITON("Surbiton", 5, 0, 0),
	TEDDINGTON("Teddington", 2, 0, 0),
	TOKYO("Tokyo", 20, 0, 0),
	UTRECHT("Utrecht Centraal", 16, 0, 0),
	ZURICH("Zurich HB", 26, 420, 13.5),
	WESTMINSTER("Westminster", 4, 0, 0),
	ANGEL("Angel", 2, 0, 0),
	CANARY_WHARF("Canary Wharf", 2, 0, 0),
	VIJZELGRACHT("Vijzelgracht", 2, 125, 0);

	private final String label;
	private final int tracks;
	private final int lengthMetres;
	private final double widthMetres;

	MeasuredStation(String label, int tracks, int lengthMetres, double widthMetres) {
		this.label = label;
		this.tracks = tracks;
		this.lengthMetres = lengthMetres;
		this.widthMetres = widthMetres;
	}

	public String label() {
		return label;
	}

	public int tracks() {
		return tracks;
	}

	public int lengthMetres() {
		return lengthMetres;
	}

	public double widthMetres() {
		return widthMetres;
	}

	/** The measured station a generated station's name matches, or null for a station the research has no sheet for. */
	public static MeasuredStation of(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		final String wanted = normalise(name);
		for (final MeasuredStation station : values()) {
			if (normalise(station.label).equals(wanted)) {
				return station;
			}
		}
		// the presets use shorter names than the research sheets ("Frankfurt Hbf", "Berlin Hbf")
		if (wanted.startsWith("frankfurt")) {
			return FRANKFURT;
		}
		if (wanted.startsWith("berlin")) {
			return BERLIN;
		}
		// the presets use shorter names than the research sheets ("Amsterdam Centraal", "West Kowloon", "Munchen Ost")
		for (final MeasuredStation station : values()) {
			final String sheet = normalise(station.label);
			if (sheet.contains(wanted) || wanted.contains(sheet)) {
				return station;
			}
		}
		return null;
	}

	private static String normalise(String text) {
		return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
	}
}
