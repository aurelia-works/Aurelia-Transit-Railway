/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.nodes;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.road.nodes.NodeAids;
import org.mtr.aurelia.road.road.nodes.NodeCheck;
import org.mtr.aurelia.road.road.nodes.PlacedRoads;
import org.mtr.aurelia.road.road.solve.LaneNode;
import org.mtr.aurelia.road.fabric.client.compat.IrisCompat;
import org.mtr.aurelia.road.fabric.client.ui.RoadColours;

/**
 * The heading overlay and the checker's highlights (task R-6; owner, 2026-10-06; spec roads-v1 §9), drawn while the node
 * aids show ({@link StratosNodes#visible()}), for every lane node within {@link NodeAids#RANGE} blocks of the player:
 * <ul>
 *   <li><b>An arrow</b> along the lane's heading, both ways (a node has no front), 2.4 blocks long, just above the node
 *       block's floor (the top of the marker or the road).</li>
 *   <li><b>Its colour:</b> a node placed right in {@code status-success}; a wrong one in {@code status-error}; one still
 *       to place in {@code accent-soft}, or {@code status-success} while the player's facing would place it right (MTR's
 *       yaw rule).</li>
 *   <li><b>Boxes:</b> green round a node placed right, red round a wrong one, and red round a node found in the wrong
 *       block.</li>
 * </ul>
 * Drawn like the preview's outlines ({@code BEFORE_DEBUG_RENDER}, depth-tested, 2 px lines), relative to the camera; not
 * in Iris's shadow pass. Render thread only.
 */
final class NodeOverlay {
    private static final float HALF_ARROW = 1.2F;
    private static final float HEAD = 0.4F;
    private static final float WING = 0.25F;
    private static final float LIFT = 0.03F;
    private static final float INFLATE = 0.03F;
    private static final int ALPHA = 255;

    private static long framesDrawn;

    private NodeOverlay() {
    }

    /** How many frames have drawn the overlay (the harness checks it shows). */
    static long framesDrawn() {
        return framesDrawn;
    }

    static void render(WorldRenderContext context) {
        if (!StratosNodes.visible() || IrisCompat.renderingShadowPass()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        NodeAids aids = StratosNodes.aids();
        List<PlacedRoads.Road> roads = aids.roads().roads();
        if (roads.isEmpty()) {
            return;
        }
        int facing = MtrNodes.facingFor(StratosNodes.yaw(client));
        Vec3d camera = context.camera().getPos();
        double px = client.player.getX();
        double pz = client.player.getZ();
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        BufferBuilder lines = Tessellator.getInstance().getBuffer();
        lines.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int drawn = 0;
        for (PlacedRoads.Road road : roads) {
            Optional<NodeCheck.Report> report = aids.report(road);
            for (int i = 0; i < road.nodes().size(); i++) {
                LaneNode node = road.nodes().get(i);
                double dx = node.x() + 0.5 - px;
                double dz = node.z() + 0.5 - pz;
                if (dx * dx + dz * dz > NodeAids.RANGE * NodeAids.RANGE) {
                    continue;
                }
                int index = i;
                Optional<NodeCheck.Result> result = report.filter(r -> r.results().size() == road.nodes().size()).map(r -> r.results().get(index));
                NodeCheck.Status status = result.map(NodeCheck.Result::status).orElse(NodeCheck.Status.NOT_PLACED);
                if (status == NodeCheck.Status.NOT_LOADED) {
                    continue;
                }
                int arrow;
                if (status == NodeCheck.Status.RIGHT) {
                    arrow = RoadColours.STATUS_SUCCESS;
                } else if (facing == node.mtrFacing() && (status == NodeCheck.Status.NOT_PLACED || status == NodeCheck.Status.MISSING)) {
                    arrow = RoadColours.STATUS_SUCCESS; // the player's facing would place this one right
                } else if (status.wrong()) {
                    arrow = RoadColours.STATUS_ERROR;
                } else {
                    arrow = RoadColours.ACCENT_SOFT;
                }
                arrow(lines, matrix, camera, node, arrow);
                if (status == NodeCheck.Status.RIGHT) {
                    box(lines, matrix, camera, node.x(), node.nodeY(), node.z(), RoadColours.STATUS_SUCCESS);
                } else if (status.wrong()) {
                    box(lines, matrix, camera, node.x(), node.nodeY(), node.z(), RoadColours.STATUS_ERROR);
                    result.flatMap(NodeCheck.Result::foundAt).ifPresent(spot -> box(lines, matrix, camera, spot.x(), spot.y(), spot.z(),
                            RoadColours.STATUS_ERROR));
                }
                drawn++;
            }
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.lineWidth(3.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        Tessellator.getInstance().draw();
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
        if (drawn > 0) {
            framesDrawn++;
        }
    }

    /** The two-headed arrow along {@code node}'s heading, just above its block's floor. */
    private static void arrow(BufferBuilder lines, Matrix4f matrix, Vec3d camera, LaneNode node, int rgb) {
        Heading heading = Heading.of(node.mtrFacing());
        Vec2 t = heading.tangent();
        Vec2 n = heading.normal();
        double cx = node.x() + 0.5;
        double cy = node.nodeY() + LIFT;
        double cz = node.z() + 0.5;
        line(lines, matrix, camera, cx - HALF_ARROW * t.x(), cy, cz - HALF_ARROW * t.z(), cx + HALF_ARROW * t.x(), cy, cz + HALF_ARROW * t.z(), rgb);
        for (int sign = -1; sign <= 1; sign += 2) {
            double tipX = cx + sign * HALF_ARROW * t.x();
            double tipZ = cz + sign * HALF_ARROW * t.z();
            double backX = tipX - sign * HEAD * t.x();
            double backZ = tipZ - sign * HEAD * t.z();
            line(lines, matrix, camera, tipX, cy, tipZ, backX + WING * n.x(), cy, backZ + WING * n.z(), rgb);
            line(lines, matrix, camera, tipX, cy, tipZ, backX - WING * n.x(), cy, backZ - WING * n.z(), rgb);
        }
    }

    /** A box round the block {@code (x, y, z)}, a little larger than it. */
    private static void box(BufferBuilder lines, Matrix4f matrix, Vec3d camera, int x, int y, int z, int rgb) {
        double x0 = x - INFLATE;
        double y0 = y - INFLATE;
        double z0 = z - INFLATE;
        double x1 = x + 1 + INFLATE;
        double y1 = y + 1 + INFLATE;
        double z1 = z + 1 + INFLATE;
        double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        for (int i = 0; i < 4; i++) {
            double[] a = corners[i];
            double[] b = corners[(i + 1) % 4];
            line(lines, matrix, camera, a[0], y0, a[1], b[0], y0, b[1], rgb);
            line(lines, matrix, camera, a[0], y1, a[1], b[0], y1, b[1], rgb);
            line(lines, matrix, camera, a[0], y0, a[1], a[0], y1, a[1], rgb);
        }
    }

    private static void line(BufferBuilder lines, Matrix4f matrix, Vec3d camera, double x0, double y0, double z0, double x1, double y1, double z1,
            int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        lines.vertex(matrix, (float) (x0 - camera.x), (float) (y0 - camera.y), (float) (z0 - camera.z)).color(r, g, b, ALPHA).next();
        lines.vertex(matrix, (float) (x1 - camera.x), (float) (y1 - camera.y), (float) (z1 - camera.z)).color(r, g, b, ALPHA).next();
    }
}
