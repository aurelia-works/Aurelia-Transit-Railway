/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.fabric.block.LayeredBlock;
import org.mtr.aurelia.road.fabric.block.StratosBlocks;
import org.mtr.aurelia.road.fabric.paint.PaintLines;
import org.mtr.aurelia.road.fabric.paint.PaintPacket;
import org.mtr.aurelia.road.road.line.LineKind;
import org.mtr.aurelia.road.road.line.LineTop;

/**
 * Draws road paint ({@link PaintLines}) as MTR draws its rails: from data, over the world's blocks, with no block states.
 * <ul>
 *   <li>The server sends the paint of the player's dimension ({@code PaintSync}); it is kept by chunk section.</li>
 *   <li>Each section's paint is one vertex buffer, built from core's {@link LineTop} paint pieces on top of the asphalt
 *       below, with the world's light. A section is rebuilt when its paint changes, and every few seconds while it is
 *       near, so a broken or raised block and a new torch show; a few sections a frame at most.</li>
 *   <li>Paint shows only on Stratos asphalt: where the block is something else now, nothing is drawn.</li>
 * </ul>
 */
public final class PaintRenderer {
    /** How far above the asphalt the paint lies: enough not to flicker, too little to see. */
    private static final float LIFT = 1 / 512F;
    /** Sections rebuilt a frame at most. */
    private static final int BUILDS_PER_FRAME = 4;
    /** A near section is rebuilt when its mesh is older than this (ticks). */
    private static final long REFRESH_TICKS = 60;
    /** Within this many blocks of the camera, a section counts as near. */
    private static final double NEAR = 48;

    private static final Long2ObjectOpenHashMap<Section> SECTIONS = new Long2ObjectOpenHashMap<>();
    private static final Map<LineKind, List<LineTop.Piece>[]> PIECES = new EnumMap<>(LineKind.class);
    /** One builder for every section, render thread only: a builder's memory is native and never given back. */
    private static final BufferBuilder BUILDER = new BufferBuilder(1 << 16);

    private PaintRenderer() {
    }

    /** Called once, from the client initialiser. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PaintPacket.TYPE, (packet, player, sender) -> receive(packet));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(PaintRenderer::clear));
        WorldRenderEvents.BEFORE_ENTITIES.register(PaintRenderer::render);
    }

    private static void receive(PaintPacket packet) {
        if (packet.reset()) {
            clear();
        }
        for (int i = 0; i < packet.positions().length; i++) {
            long position = packet.positions()[i];
            int value = packet.values()[i];
            long key = ChunkSectionPos.fromBlockPos(position);
            Section section = SECTIONS.get(key);
            if (value == 0 || !PaintLines.valid(value)) {
                if (section != null) {
                    section.cells.remove(position);
                    section.dirty = true;
                }
                continue;
            }
            if (section == null) {
                section = new Section(key);
                SECTIONS.put(key, section);
            }
            section.cells.put(position, value);
            section.dirty = true;
        }
    }

    private static void clear() {
        SECTIONS.values().forEach(Section::close);
        SECTIONS.clear();
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = context.world();
        Frustum frustum = context.frustum();
        if (SECTIONS.isEmpty() || world == null || frustum == null || context.camera() == null) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        double reach = client.options.getClampedViewDistance() * 16.0 + 16;
        long now = world.getTime();
        int builds = BUILDS_PER_FRAME;
        var iterator = SECTIONS.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Section section = iterator.next().getValue();
            if (section.cells.isEmpty()) {
                section.close();
                iterator.remove();
                continue;
            }
            double distance = section.distance(camera);
            if (builds > 0 && distance <= reach
                    && (section.dirty || distance <= NEAR && now - section.builtAt > REFRESH_TICKS) && frustum.isVisible(section.bounds)) {
                section.build(world, now);
                builds--;
            }
        }
        Matrix4f base = context.matrixStack().peek().getPositionMatrix();
        Matrix4f projection = context.projectionMatrix();
        RenderLayer layer = RenderLayer.getCutout();
        layer.startDrawing();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1, -4);
        try {
            ShaderProgram shader = RenderSystem.getShader();
            if (shader == null) {
                return;
            }
            if (shader.chunkOffset != null) {
                shader.chunkOffset.set(0F, 0F, 0F);
            }
            for (Section section : SECTIONS.values()) {
                if (section.buffer == null || section.distance(camera) > reach || !frustum.isVisible(section.bounds)) {
                    continue;
                }
                section.buffer.bind();
                section.buffer.draw(new Matrix4f(base).translate((float) (section.minX - camera.x), (float) (section.minY - camera.y),
                        (float) (section.minZ - camera.z)), projection, shader);
            }
            VertexBuffer.unbind();
        } finally {
            RenderSystem.polygonOffset(0, 0);
            RenderSystem.disablePolygonOffset();
            layer.endDrawing();
        }
    }

    /** The paint pieces of {@code kind} along {@code chord}, worked out once. */
    @SuppressWarnings("unchecked")
    private static List<LineTop.Piece> pieces(LineKind kind, int chord) {
        List<LineTop.Piece>[] byChord = PIECES.computeIfAbsent(kind, k -> new List[org.mtr.aurelia.road.road.line.LineChords.COUNT]);
        List<LineTop.Piece> pieces = byChord[chord];
        if (pieces == null) {
            pieces = LineTop.pieces(kind, chord).stream().filter(LineTop.Piece::paint).toList();
            byChord[chord] = pieces;
        }
        return pieces;
    }

    private static Sprite sprite(LineKind kind) {
        return MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                .apply(new Identifier(StratosMod.MOD_ID, "block/paint_" + kind.paint()));
    }

    /** One chunk section's paint and its mesh. */
    private static final class Section {
        private final Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
        private final int minX;
        private final int minY;
        private final int minZ;
        private final Box bounds;
        private VertexBuffer buffer;
        private boolean dirty = true;
        private long builtAt;

        private Section(long key) {
            this.minX = ChunkSectionPos.unpackX(key) << 4;
            this.minY = ChunkSectionPos.unpackY(key) << 4;
            this.minZ = ChunkSectionPos.unpackZ(key) << 4;
            this.bounds = new Box(this.minX, this.minY, this.minZ, this.minX + 16, this.minY + 17, this.minZ + 16);
        }

        private double distance(Vec3d camera) {
            double dx = this.minX + 8 - camera.x;
            double dy = this.minY + 8 - camera.y;
            double dz = this.minZ + 8 - camera.z;
            return StrictMath.sqrt(dx * dx + dy * dy + dz * dz);
        }

        private void build(ClientWorld world, long now) {
            this.dirty = false;
            this.builtAt = now;
            BufferBuilder builder = BUILDER;
            boolean begun = false;
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (Long2IntMap.Entry entry : this.cells.long2IntEntrySet()) {
                pos.set(entry.getLongKey());
                if (!world.isChunkLoaded(pos)) {
                    this.builtAt = 0; // try again soon
                    continue;
                }
                BlockState state = world.getBlockState(pos);
                if (!state.isOf(StratosBlocks.ASPHALT)) {
                    continue;
                }
                int value = entry.getIntValue();
                LineKind kind = PaintLines.kind(value);
                int layers = state.get(LayeredBlock.LAYERS);
                int light = WorldRenderer.getLightmapCoordinates(world, layers == 8 ? pos.up() : pos);
                Sprite sprite = sprite(kind);
                float ox = pos.getX() - this.minX;
                float oz = pos.getZ() - this.minZ;
                float y = pos.getY() - this.minY + layers / 8F + LIFT;
                for (LineTop.Piece piece : pieces(kind, PaintLines.chord(value))) {
                    // A fan of quads; a piece with an odd number of corners ends in a quad with its last corner repeated.
                    int n = piece.x().length;
                    for (int start = 1; start < n - 1; start += 2) {
                        if (!begun) {
                            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
                            begun = true;
                        }
                        int[] corners = {0, start, start + 1, StrictMath.min(start + 2, n - 1)};
                        for (int corner : corners) {
                            float x = (float) piece.x()[corner];
                            float z = (float) piece.z()[corner];
                            builder.vertex(ox + x, y, oz + z, 1, 1, 1, 1, sprite.getFrameU(x * 16), sprite.getFrameV(z * 16),
                                    OverlayTexture.DEFAULT_UV, light, 0, 1, 0);
                        }
                    }
                }
            }
            if (!begun) {
                close();
                return;
            }
            BufferBuilder.BuiltBuffer built = builder.end();
            if (this.buffer == null) {
                this.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
            this.buffer.bind();
            this.buffer.upload(built);
            VertexBuffer.unbind();
        }

        private void close() {
            if (this.buffer != null) {
                this.buffer.close();
                this.buffer = null;
            }
        }
    }
}
