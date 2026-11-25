package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.mtr.mod.zip.ZipCurve;
import org.mtr.mod.zip.ZipLines;

/**
 * Cables as a strip that always faces the camera, shaded like a round steel rope lit from above, and seats as a small
 * model: a trolley on the cable, a hanger bar, two ropes and a wooden plank. Everything is flat coloured quads in one
 * buffer, lit once per cable and once per seat from the world light there. Far cables are skipped, mid ones use 6
 * segments, near ones 16. The strip never gets thinner than about a pixel and a half, so a far cable does not flicker.
 * Nothing is allocated per frame.
 */
public final class ZipCableRenderer {

	private static final double FAR = 96;
	private static final double MID = 48;
	private static final double CABLE_RADIUS = 0.035;
	/** The strip keeps at least this many blocks of half width per block of distance, about 1.5 pixels at 1080p. */
	private static final double MIN_ANGULAR_RADIUS = 0.0011;
	private static final int CABLE_COLOR = 0x7A7E86;
	private static final int METAL_COLOR = 0x4A4D54;
	private static final int ROPE_COLOR = 0xB08A58;
	private static final int WOOD_COLOR = 0x8E5E34;

	private final double[] point = new double[3];
	/** Left edge, centre and right edge of the previous and the current cross-section, xyz each, camera relative. */
	private final double[] previous = new double[9];
	private final double[] current = new double[9];
	private final BlockPos.Mutable lightPos = new BlockPos.Mutable();
	private float previousLeftShade;
	private float previousRightShade;
	private float currentLeftShade;
	private float currentRightShade;

	// the box being drawn: centre, half sizes, forward and side axes (both level), colour and light
	private double boxX;
	private double boxY;
	private double boxZ;
	private double halfForward;
	private double halfUp;
	private double halfSide;
	private double forwardX;
	private double forwardZ;
	private double sideX;
	private double sideZ;
	private int boxColor;
	private float boxLight;

	private ZipCableRenderer() {
	}

	public static void register() {
		final ZipCableRenderer renderer = new ZipCableRenderer();
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(renderer::render);
	}

	private void render(WorldRenderContext context) {
		if (!ZipClient.settings().enabled() || context.consumers() == null || context.camera() == null || context.matrixStack() == null || context.world() == null) {
			return;
		}
		final int count = ZipClient.count();
		if (count == 0) {
			return;
		}
		final ClientWorld world = context.world();
		final Vec3d camera = context.camera().getPos();
		final Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
		final VertexConsumer quads = context.consumers().getBuffer(RenderLayer.getDebugQuads());
		final double sag = ZipClient.settings().sag();
		final float skyBrightness = world.getSkyBrightness(1);
		final long now = System.nanoTime();
		final ZipClient.View[] views = ZipClient.views();
		for (int i = 0; i < count; i++) {
			final ZipClient.View view = views[i];
			final double x0 = view.ax() + 0.5;
			final double y0 = view.ay() + 1;
			final double z0 = view.az() + 0.5;
			final double x1 = view.bx() + 0.5;
			final double y1 = view.by() + 1;
			final double z1 = view.bz() + 0.5;
			final double midX = (x0 + x1) * 0.5;
			final double midY = (y0 + y1) * 0.5 - ZipCurve.midDrop(x0, z0, x1, z1, sag);
			final double midZ = (z0 + z1) * 0.5;
			final double distance = distance(camera, midX, midY, midZ);
			if (distance > FAR) {
				continue;
			}
			cable(quads, matrix, camera, x0, y0, z0, x1, y1, z1, sag, distance > MID ? 6 : 16, light(world, midX, midY, midZ, skyBrightness));
			if (view.seat()) {
				ZipCurve.point(view.seatT(now), x0, y0, z0, x1, y1, z1, sag, point);
				seat(quads, matrix, camera, point[0], point[1], point[2], x1 - x0, z1 - z0, light(world, point[0], point[1] - ZipLines.SEAT_HANG, point[2], skyBrightness));
			}
		}
	}

	private void cable(VertexConsumer quads, Matrix4f matrix, Vec3d camera, double x0, double y0, double z0, double x1, double y1, double z1, double sag, int segments, float light) {
		final double span = ZipCurve.horizontalSpan(x0, z0, x1, z1);
		for (int segment = 0; segment <= segments; segment++) {
			final double t = segment / (double) segments;
			ZipCurve.point(t, x0, y0, z0, x1, y1, z1, sag, point);
			crossSection(point[0] - camera.x, point[1] - camera.y, point[2] - camera.z, x1 - x0, ZipCurve.dyDt(t, y0, y1, span, sag), z1 - z0);
			if (segment > 0) {
				// two quads, edge to centre and centre to edge, so the shading rounds over the middle
				cableQuad(quads, matrix, 0, previousLeftShade, currentLeftShade, light);
				cableQuad(quads, matrix, 6, previousRightShade, currentRightShade, light);
			}
			System.arraycopy(current, 0, previous, 0, 9);
			previousLeftShade = currentLeftShade;
			previousRightShade = currentRightShade;
		}
	}

	/** Fills {@link #current}: the cable's two edges across the line of sight, and the edge shades (the upper edge lighter). */
	private void crossSection(double x, double y, double z, double tangentX, double tangentY, double tangentZ) {
		// side = tangent x (point - camera), which lies across the cable and across the line of sight
		double sx = tangentY * z - tangentZ * y;
		double sy = tangentZ * x - tangentX * z;
		double sz = tangentX * y - tangentY * x;
		double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (length < 1.0e-6) {
			// looking straight along the cable: any level side will do
			sx = -tangentZ;
			sy = 0;
			sz = tangentX;
			length = Math.max(1.0e-6, Math.sqrt(sx * sx + sz * sz));
		}
		final double radius = Math.max(CABLE_RADIUS, Math.sqrt(x * x + y * y + z * z) * MIN_ANGULAR_RADIUS);
		final double scale = radius / length;
		sx *= scale;
		sy *= scale;
		sz *= scale;
		current[0] = x - sx;
		current[1] = y - sy;
		current[2] = z - sz;
		current[3] = x;
		current[4] = y;
		current[5] = z;
		current[6] = x + sx;
		current[7] = y + sy;
		current[8] = z + sz;
		// how much the right edge points up, -1 to 1
		final float up = (float) (sy / radius);
		currentLeftShade = 0.62F - 0.3F * up;
		currentRightShade = 0.62F + 0.3F * up;
	}

	/** One quad of the strip between the previous and current cross-sections; {@code edge} is 0 for the left edge, 6 for the right. */
	private void cableQuad(VertexConsumer quads, Matrix4f matrix, int edge, float previousShade, float currentShade, float light) {
		final float centreShade = 0.9F;
		vertex(quads, matrix, previous[edge], previous[edge + 1], previous[edge + 2], CABLE_COLOR, previousShade * light);
		vertex(quads, matrix, previous[3], previous[4], previous[5], CABLE_COLOR, centreShade * light);
		vertex(quads, matrix, current[3], current[4], current[5], CABLE_COLOR, centreShade * light);
		vertex(quads, matrix, current[edge], current[edge + 1], current[edge + 2], CABLE_COLOR, currentShade * light);
	}

	/**
	 * The seat hanging at cable point (x, y, z): the rider stands on the plank {@link ZipLines#SEAT_HANG} below the
	 * cable. The hanger bar sits just over the rider's eyes and the ropes come down outside their arms, so in first
	 * person neither crosses the view ahead.
	 */
	private void seat(VertexConsumer quads, Matrix4f matrix, Vec3d camera, double x, double y, double z, double alongX, double alongZ, float light) {
		final double length = Math.sqrt(alongX * alongX + alongZ * alongZ);
		forwardX = length < 1.0e-4 ? 1 : alongX / length;
		forwardZ = length < 1.0e-4 ? 0 : alongZ / length;
		sideX = -forwardZ;
		sideZ = forwardX;
		boxLight = light;
		final double cx = x - camera.x;
		final double cy = y - camera.y;
		final double cz = z - camera.z;
		final double feet = cy - ZipLines.SEAT_HANG;
		final double barY = cy - 0.08;
		final double ropeSide = 0.52;
		// trolley wrapped round the cable
		box(quads, matrix, cx, cy, cz, 0.18, 0.06, 0.06, METAL_COLOR);
		// hanger bar across the rider, under the trolley
		box(quads, matrix, cx, barY, cz, 0.03, 0.025, ropeSide + 0.03, METAL_COLOR);
		// ropes from the bar ends down to the plank ends
		final double ropeBottom = feet - 0.02;
		final double ropeMid = (barY + ropeBottom) * 0.5;
		final double ropeHalf = (barY - ropeBottom) * 0.5;
		box(quads, matrix, cx + sideX * ropeSide, ropeMid, cz + sideZ * ropeSide, 0.02, ropeHalf, 0.02, ROPE_COLOR);
		box(quads, matrix, cx - sideX * ropeSide, ropeMid, cz - sideZ * ropeSide, 0.02, ropeHalf, 0.02, ROPE_COLOR);
		// plank under the rider's feet
		box(quads, matrix, cx, feet - 0.04, cz, 0.2, 0.04, ropeSide + 0.06, WOOD_COLOR);
	}

	/** A box on the seat's axes (level forward and side, straight up), lit from above: top brightest, bottom darkest. */
	private void box(VertexConsumer quads, Matrix4f matrix, double x, double y, double z, double forward, double up, double side, int color) {
		boxX = x;
		boxY = y;
		boxZ = z;
		halfForward = forward;
		halfUp = up;
		halfSide = side;
		boxColor = color;
		// top and bottom
		for (int u = 1; u >= -1; u -= 2) {
			final float shade = u > 0 ? 1 : 0.5F;
			corner(quads, matrix, -1, u, -1, shade);
			corner(quads, matrix, 1, u, -1, shade);
			corner(quads, matrix, 1, u, 1, shade);
			corner(quads, matrix, -1, u, 1, shade);
		}
		// front and back
		for (int f = -1; f <= 1; f += 2) {
			corner(quads, matrix, f, -1, -1, 0.8F);
			corner(quads, matrix, f, 1, -1, 0.8F);
			corner(quads, matrix, f, 1, 1, 0.8F);
			corner(quads, matrix, f, -1, 1, 0.8F);
		}
		// both sides
		for (int s = -1; s <= 1; s += 2) {
			corner(quads, matrix, -1, -1, s, 0.68F);
			corner(quads, matrix, 1, -1, s, 0.68F);
			corner(quads, matrix, 1, 1, s, 0.68F);
			corner(quads, matrix, -1, 1, s, 0.68F);
		}
	}

	private void corner(VertexConsumer quads, Matrix4f matrix, int f, int u, int s, float shade) {
		final double alongForward = halfForward * f;
		final double alongSide = halfSide * s;
		vertex(quads, matrix,
				boxX + forwardX * alongForward + sideX * alongSide,
				boxY + halfUp * u,
				boxZ + forwardZ * alongForward + sideZ * alongSide,
				boxColor, shade * boxLight);
	}

	private static void vertex(VertexConsumer quads, Matrix4f matrix, double x, double y, double z, int color, float shade) {
		quads.vertex(matrix, (float) x, (float) y, (float) z)
				.color(channel(color >> 16, shade), channel(color >> 8, shade), channel(color, shade), 255)
				.next();
	}

	private static int channel(int color, float shade) {
		return Math.min(255, (int) ((color & 0xFF) * shade));
	}

	/** How lit the world is at a point, 0.35 (pitch dark) to 1 (full daylight or a torch beside it). */
	private float light(ClientWorld world, double x, double y, double z, float skyBrightness) {
		final int packed = WorldRenderer.getLightmapCoordinates(world, lightPos.set(x, y, z));
		final float block = ((packed >> 4) & 0xF) / 15F;
		final float sky = ((packed >> 20) & 0xF) / 15F * skyBrightness;
		return 0.35F + 0.65F * Math.max(block, sky);
	}

	private static double distance(Vec3d camera, double x, double y, double z) {
		final double dx = x - camera.x;
		final double dy = y - camera.y;
		final double dz = z - camera.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
