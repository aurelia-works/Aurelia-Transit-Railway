package com.aureliatransit.architecture.timetable;

import com.aureliatransit.architecture.terminal.MapLine;
import com.aureliatransit.architecture.terminal.MapStop;
import com.aureliatransit.architecture.terminal.StationInfo;
import com.aureliatransit.architecture.terminal.SystemMap;
import com.aureliatransit.architecture.text.TextSanitizer;
import org.mtr.aurelia.timetable.Timetable;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines printed on a pocket timetable. Built from {@link StationInfo}, {@link SystemMap} and depot departure times
 * ({@link Timetable}). Every line is sanitised the same way sign text is, so a printed sheet cannot carry formatting
 * codes. Empty input still yields a titled sheet, never a null.
 */
public final class PocketSheet {

	public static final int MAX_LINES = 24;
	public static final int MAX_CHARS = 40;

	public enum Kind {TIMETABLE, MAP}

	public record Sheet(String title, Kind kind, List<String> lines) {
		public Sheet {
			title = TextSanitizer.sanitize(title, MAX_CHARS);
			kind = kind == null ? Kind.TIMETABLE : kind;
			final List<String> clean = new ArrayList<>();
			if (lines != null) {
				for (final String line : lines) {
					if (clean.size() >= MAX_LINES) {
						break;
					}
					final String sanitized = TextSanitizer.sanitize(line, MAX_CHARS);
					if (!sanitized.isEmpty()) {
						clean.add(sanitized);
					}
				}
			}
			lines = List.copyOf(clean);
		}
	}

	private PocketSheet() {
	}

	public static Sheet timetable(StationInfo info, List<Long> departuresMillisOfDay) {
		final List<String> lines = new ArrayList<>();
		if (info != null && !info.stationCode().isBlank()) {
			lines.add(info.stationCode());
		}
		if (info != null) {
			info.platforms().forEach(platform -> lines.add(platform.name()));
		}
		if (departuresMillisOfDay != null) {
			for (final long time : Timetable.normalise(departuresMillisOfDay)) {
				lines.add(Timetable.format(time));
			}
		}
		if (lines.isEmpty()) {
			lines.add("No departures");
		}
		return new Sheet(info == null ? "" : info.stationName(), Kind.TIMETABLE, lines);
	}

	public static Sheet lineMap(String stationName, SystemMap map) {
		final List<String> lines = new ArrayList<>();
		if (map != null) {
			for (final MapLine line : map.lines()) {
				final StringBuilder row = new StringBuilder();
				if (line.badge() != null && !line.badge().label().isBlank()) {
					row.append(line.badge().label()).append(' ');
				}
				for (final MapStop stop : line.stops()) {
					if (row.length() > 0 && row.charAt(row.length() - 1) != ' ') {
						row.append(stop.current() ? " *" : " - ");
					}
					row.append(stop.name() == null ? "" : stop.name());
					if (row.length() >= MAX_CHARS) {
						break;
					}
				}
				lines.add(row.toString());
			}
		}
		if (lines.isEmpty()) {
			lines.add("No lines");
		}
		return new Sheet(stationName, Kind.MAP, lines);
	}
}
