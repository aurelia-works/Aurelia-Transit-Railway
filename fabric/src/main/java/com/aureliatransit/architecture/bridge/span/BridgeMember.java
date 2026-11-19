package com.aureliatransit.architecture.bridge.span;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A straight piece drawn by a renderer rather than built of blocks: a stay, hanger, main-cable segment, truss diagonal, mast or
 * wire. Ends are in blocks relative to the anchor block's lower north-west corner, so a member keeps its exact line on any
 * curve. {@code thickness} is the square section's side.
 */
public record BridgeMember(Material material, float x0, float y0, float z0, float x1, float y1, float z1, float thickness) {

	/** What a member is drawn with. Ordinal is saved, so add new ones at the end. */
	public enum Material {
		CONCRETE,
		WHITE,
		STEEL,
		CABLE,
		RED,
		/** An aviation warning light: drawn full bright. */
		LIGHT,
		STONE,
		/** Yellow ground paint: a flat strip lying on the block below, {@code thickness} wide. */
		PAINT
	}

	/** Most members a planned block's text may carry; the block entity keeps at most 1024. */
	public static final int MAX_PACKED = 1024;

	/**
	 * Members as a planned block's text: the skin, then one member per {@code ;}. Ends are local: u, y, v in blocks from the
	 * anchor cell's centre (y from its bottom), so the plan stays free of the world's facing until {@link #placed} turns them.
	 */
	public static String pack(String skin, List<BridgeMember> members) {
		final StringBuilder out = new StringBuilder(skin);
		for (final BridgeMember m : members) {
			out.append(';').append(m.material.ordinal());
			for (final float value : new float[]{m.x0, m.y0, m.z0, m.x1, m.y1, m.z1, m.thickness}) {
				out.append(',').append(String.format(Locale.ROOT, "%.3f", value));
			}
		}
		return out.toString();
	}

	/** The skin of a {@link #pack}ed text. */
	public static String skinOf(String text) {
		final int end = text.indexOf(';');
		return end < 0 ? text : text.substring(0, end);
	}

	/** The members of a {@link #pack}ed text, still local; a malformed member is dropped. */
	public static List<BridgeMember> unpack(String text) {
		final List<BridgeMember> out = new ArrayList<>();
		final String[] parts = text.split(";");
		final Material[] materials = Material.values();
		for (int i = 1; i < parts.length && out.size() < MAX_PACKED; i++) {
			final String[] f = parts[i].split(",");
			if (f.length != 8) {
				continue;
			}
			try {
				final int material = Integer.parseInt(f[0]);
				if (material < 0 || material >= materials.length) {
					continue;
				}
				out.add(new BridgeMember(materials[material], Float.parseFloat(f[1]), Float.parseFloat(f[2]), Float.parseFloat(f[3]),
						Float.parseFloat(f[4]), Float.parseFloat(f[5]), Float.parseFloat(f[6]), Float.parseFloat(f[7])));
			} catch (NumberFormatException e) {
				// skip it
			}
		}
		return out;
	}

	/**
	 * A local member (see {@link #pack}) in blocks from the anchor's lower north-west corner: u runs along world (rx, rz) and v
	 * along (fx, fz).
	 */
	public BridgeMember placed(int rx, int rz, int fx, int fz) {
		return new BridgeMember(material, 0.5f + rx * x0 + fx * z0, y0, 0.5f + rz * x0 + fz * z0,
				0.5f + rx * x1 + fx * z1, y1, 0.5f + rz * x1 + fz * z1, thickness);
	}

	public double length() {
		final double dx = x1 - x0;
		final double dy = y1 - y0;
		final double dz = z1 - z0;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
