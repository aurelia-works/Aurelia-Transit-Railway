package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** SCRATCH, never commit: dumps a plan for the quality loop. */
class ZzPlanDump {
	@Test
	void dump() throws Exception {
		final String description = System.getProperty("zz.desc", "two track dutch station, island platform, 64 long, glass canopy, gravel bed, named Leidsche Rijn");
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(description).spec());
		final Map<String, Integer> counts = new TreeMap<>();
		final Map<String, PlannedBlock> at = new java.util.HashMap<>();
		for (final PlannedBlock b : plan.blocks()) {
			at.put(b.u() + "," + b.y() + "," + b.v(), b);
		}
		int uMin = 999, uMax = -999, vMin = 999, vMax = -999, yMin = 999, yMax = -999;
		for (final PlannedBlock b : plan.blocks()) {
			counts.merge(b.blockId() + " " + b.role(), 1, Integer::sum);
			uMin = Math.min(uMin, b.u()); uMax = Math.max(uMax, b.u()); vMin = Math.min(vMin, b.v()); vMax = Math.max(vMax, b.v()); yMin = Math.min(yMin, b.y()); yMax = Math.max(yMax, b.y());
		}
		try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(System.getProperty("zz.out", "/tmp/zzplan.txt"))))) {
			out.println("bounds u " + uMin + ".." + uMax + " v " + vMin + ".." + vMax + " y " + yMin + ".." + yMax);
			counts.forEach((k, n) -> out.println(n + " " + k));
			for (final PlannedBlock b : plan.blocks()) {
				if (b.blockId().contains("catenary") || b.blockId().contains("contact_wire") || b.blockId().contains("station_sign")) {
					out.println("CAT " + b.blockId().substring(b.blockId().indexOf(':') + 1) + " u" + b.u() + " y" + b.y() + " v" + b.v() + " " + b.front() + " " + b.role());
				}
			}
			for (int y = yMin; y <= yMax; y++) {
				out.println("== y " + y + " (rows v, cols u; char = first letter of role, lower = part of name)");
				for (int v = vMin; v <= vMax; v++) {
					final StringBuilder row = new StringBuilder(String.format("%4d ", v));
					for (int u = uMin; u <= uMax; u++) {
						final PlannedBlock hit = at.get(u + "," + y + "," + v);
						row.append(hit == null ? '.' : glyph(hit));
					}
					out.println(row);
				}
			}
		}
	}

	private static char glyph(PlannedBlock b) {
		final String id = b.blockId();
		final String name = id.substring(id.indexOf(':') + 1);
		return switch (b.role()) {
			case FLOOR -> name.contains("gravel") || name.contains("ballast") ? 'g' : 'F';
			default -> b.role().name().charAt(0);
		};
	}
}
