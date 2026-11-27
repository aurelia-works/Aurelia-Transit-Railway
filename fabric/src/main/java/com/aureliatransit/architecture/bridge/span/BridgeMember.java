package com.aureliatransit.architecture.bridge.span;

import java.util.ArrayList;
import java.util.Arrays;
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

	/** Two paint ends closer than this are one joint (packed ends are rounded to thousandths). */
	private static final float JOINT = 0.01f;
	/** A joint sharper than this (the cosine of half its turn) keeps square ends: its mitre would reach far past the line. */
	private static final float SHARPEST = 0.5f;

	/**
	 * The ground corners of each paint strip, drawn as one joined line: where exactly two strips meet end to end they share
	 * mitred corners, so a curve made of short pieces has no notch on its outside and no corner sticking out on its inside
	 * (square pieces turned a little each read as a chain of arrows). A free end, a fork of three or more, and a sharp turn
	 * keep a square end pushed {@code overlap} past the point. Per strip, as x and z, in the order of a quad facing up: start
	 * on the -n side, start on the +n side, end on +n, end on -n, where n = (-dz, dx) is square to the strip.
	 *
	 * @return eight values per strip of {@code paint}; a strip too short to draw gets NaN
	 */
	public static float[] paintCorners(List<BridgeMember> paint, float halfWidth, float overlap) {
		final int n = paint.size();
		final float[] out = new float[n * 8];
		for (int i = 0; i < n; i++) {
			final BridgeMember m = paint.get(i);
			final float dx = m.x1 - m.x0;
			final float dz = m.z1 - m.z0;
			final float length = (float) Math.sqrt(dx * dx + dz * dz);
			if (length < 1e-4f) {
				Arrays.fill(out, i * 8, i * 8 + 8, Float.NaN);
				continue;
			}
			final float ux = dx / length;
			final float uz = dz / length;
			end(paint, i, m.x0, m.z0, -ux, -uz, halfWidth, overlap, out, i * 8);
			end(paint, i, m.x1, m.z1, ux, uz, halfWidth, overlap, out, i * 8 + 4);
		}
		return out;
	}

	/**
	 * One end's two corners at (px, pz), where strip {@code self} runs out along (tx, tz): first the corner on the side of
	 * (-tz, tx), then the other. With t pointing back at the start, that gives the order {@link #paintCorners} promises.
	 */
	private static void end(List<BridgeMember> paint, int self, float px, float pz, float tx, float tz, float h, float overlap, float[] out, int at) {
		float ox = Float.NaN;
		float oz = Float.NaN;
		for (int j = 0; j < paint.size(); j++) {
			if (j == self) {
				continue;
			}
			final BridgeMember o = paint.get(j);
			final boolean atStart = Math.abs(o.x0 - px) < JOINT && Math.abs(o.z0 - pz) < JOINT;
			final boolean atEnd = Math.abs(o.x1 - px) < JOINT && Math.abs(o.z1 - pz) < JOINT;
			if (!atStart && !atEnd) {
				continue;
			}
			if (!Float.isNaN(ox)) {
				// a fork: no single line to join
				ox = Float.NaN;
				break;
			}
			// the other strip's way on, away from the joint
			final float ax = atStart ? o.x1 - o.x0 : o.x0 - o.x1;
			final float az = atStart ? o.z1 - o.z0 : o.z0 - o.z1;
			final float al = (float) Math.sqrt(ax * ax + az * az);
			if (al < 1e-4f) {
				break;
			}
			ox = ax / al;
			oz = az / al;
		}
		// normals of the way in (t) and the way on (o): the mitre halves the angle between them
		final float nax = -tz;
		final float naz = tx;
		if (!Float.isNaN(ox)) {
			final float mx = nax - oz;
			final float mz = naz + ox;
			final float ml = (float) Math.sqrt(mx * mx + mz * mz);
			if (ml > 1e-4f) {
				final float cos = (mx * nax + mz * naz) / ml;
				if (cos >= SHARPEST) {
					final float reach = h / cos / ml;
					out[at] = px + mx * reach;
					out[at + 1] = pz + mz * reach;
					out[at + 2] = px - mx * reach;
					out[at + 3] = pz - mz * reach;
					return;
				}
			}
		}
		final float ex = px + tx * overlap;
		final float ez = pz + tz * overlap;
		out[at] = ex + nax * h;
		out[at + 1] = ez + naz * h;
		out[at + 2] = ex - nax * h;
		out[at + 3] = ez - naz * h;
	}

	public double length() {
		final double dx = x1 - x0;
		final double dy = y1 - y0;
		final double dz = z1 - z0;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
