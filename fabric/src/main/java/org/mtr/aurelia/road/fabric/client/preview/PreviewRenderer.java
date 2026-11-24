/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.preview;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mtr.aurelia.road.ops.BlockPlan.SectionPos;
import org.mtr.aurelia.road.preview.Handle;
import org.mtr.aurelia.road.preview.PreviewChoices;
import org.mtr.aurelia.road.preview.PreviewModel;
import org.mtr.aurelia.road.preview.SectionMesh;
import org.mtr.aurelia.road.preview.Side;
import org.mtr.aurelia.road.spi.PreviewSink;
import org.mtr.aurelia.road.fabric.client.compat.DhPreviewCompat;
import org.mtr.aurelia.road.fabric.client.compat.IrisCompat;
import org.mtr.aurelia.road.fabric.client.ui.BlockColours;
import org.mtr.aurelia.road.fabric.client.ui.RoadColours;

/**
 * Draws the preview (spec mod-suite-v1 §6.3), as spike S7 settled it:
 * <ul>
 *   <li><b>Faces</b> in {@code AFTER_TRANSLUCENT}: the block's map colour × vanilla's face shading, 45% opaque, pushed
 *       0.002 outwards, depth test on and depth writes off. Under a water surface they don't show, only the outlines do
 *       (owner, 2026-10-05).</li>
 *   <li><b>With a shader pack</b>, the faces are drawn both ways: as position-colour (packs such as Complementary show
 *       it), then with vanilla's translucent-entity shader after a depth-only pass (deferred packs such as IndusShaders
 *       show only that).</li>
 *   <li><b>Outlines</b> (the block grid, owner) and <b>handles</b> in {@code BEFORE_DEBUG_RENDER}, 2 px, writing depth,
 *       so water drawn later and shader packs' water effects treat them as in front.</li>
 *   <li>With Distant Horizons, the <b>far part</b> (sections beyond vanilla's render distance) is drawn in DH's own pass
 *       instead, against DH's depth and in its projection: DH's terrain hides it, and it shows past vanilla's far plane
 *       (F-7b; {@link #renderFar}).</li>
 *   <li>Nothing during Iris's shadow pass. Only vanilla's shaders, so Iris swaps in the pack's programs.</li>
 *   <li>A refused plan (R-2) is drawn the same way, with its faces, outlines and handles in {@code status-error}.</li>
 *   <li>Air isn't drawn, and a slab is drawn half a block high (G-7: core's faces and edges carry their heights).</li>
 *   <li>The handle under the crosshair, or held, is drawn in {@code accent} instead of {@code accent-soft} (R-3).</li>
 * </ul>
 * One set of static buffers per section, rebuilt only when core says that section changed. Vertices are stored relative
 * to their region's anchor (every 512 blocks), so they stay exact as floats, and the shader is set up once per region
 * in each pass: each visible section then costs one bind and one draw. Render thread only.
 */
public final class PreviewRenderer implements PreviewSink {
    private static final float INFLATE = 0.002F;
    private static final int FACE_ALPHA = 115;
    private static final int OUTLINE_ALPHA = 230;
    private static final int OUTLINE_RGB = RoadColours.ACCENT & 0xFFFFFF;
    private static final int HANDLE_RGB = RoadColours.ACCENT_SOFT & 0xFFFFFF;
    /** The handle under the crosshair, or held (R-3). */
    private static final int ACTIVE_HANDLE_RGB = RoadColours.ACCENT & 0xFFFFFF;
    /** A refused plan (roads-v1 §12; R-2): its faces, outlines and handles are all in {@code status-error}. */
    private static final int REFUSED_RGB = RoadColours.STATUS_ERROR & 0xFFFFFF;
    /** Regions of 32 sections (512 blocks) along each axis. */
    private static final int REGION_SHIFT = 5;
    /** For states that have no map colour (such as air). */
    private static final int NO_COLOUR = 0xA0A0A0;
    private static final Identifier WHITE = new Identifier("stratos", "preview_white");
    /** The far part's pull towards the camera, in normalised depth (F-7b). */
    private static final float FAR_DEPTH_BIAS = 2e-5F;
    private static final boolean DEBUG_DH = Boolean.getBoolean("stratos.debug.dhPreview");
    private static final Logger LOGGER = LoggerFactory.getLogger("stratos");

    private record Region(int x, int y, int z) {
        static Region of(SectionPos pos) {
            return new Region(pos.x() >> REGION_SHIFT, pos.y() >> REGION_SHIFT, pos.z() >> REGION_SHIFT);
        }

        static Region ofBlock(int x, int y, int z) {
            return new Region(x >> (REGION_SHIFT + 4), y >> (REGION_SHIFT + 4), z >> (REGION_SHIFT + 4));
        }

        int anchorX() {
            return this.x << (REGION_SHIFT + 4);
        }

        int anchorY() {
            return this.y << (REGION_SHIFT + 4);
        }

        int anchorZ() {
            return this.z << (REGION_SHIFT + 4);
        }
    }

    /** One section's buffers: faces as position-colour, the same faces as entity vertices (shader mode), outlines. */
    private record GpuSection(Region region, Box bounds, VertexBuffer faces, VertexBuffer entityFaces, VertexBuffer outlines) {
        void close() {
            for (VertexBuffer buffer : new VertexBuffer[] {this.faces, this.entityFaces, this.outlines}) {
                if (buffer != null) {
                    buffer.close();
                }
            }
        }
    }

    /** Counts for {@code stats} and the harness. */
    record Counts(int sections, int blocks, int faces, int edges, long buildMillis, long shadowPassSkips, long faceFrames, long outlineFrames,
            long farFrames) {
    }

    final PreviewTimers timers = new PreviewTimers();
    private final Map<SectionPos, GpuSection> sections = new HashMap<>();
    private final Map<Region, List<GpuSection>> byRegion = new LinkedHashMap<>();
    private GpuSection handles;
    private BufferBuilder faceBuilder;
    private BufferBuilder entityBuilder;
    private BufferBuilder lineBuilder;
    private boolean whiteReady;
    private boolean visible = true;
    private boolean outlinesOnly;
    /** How faces are drawn with a shader pack (UI-1c). */
    private PreviewChoices.ShaderFaces shaderFaces = PreviewChoices.DEFAULT.shaderFaces();
    /** Whether the last frame with a shader pack drew the faces once (UI-1c). */
    private boolean drawnOnce;
    /** Whether the model being drawn is refused, so its buffers are built in red. */
    private boolean refused;
    private int blocks;
    private int faces;
    private int edges;
    private long buildMillis;
    private long shadowPassSkips;
    private long faceFrames;
    private long outlineFrames;
    /** Whether DH's pass drew the far part this frame (F-7b), and in how many frames it has. */
    private boolean farDrawn;
    private long farFrames;

    @Override
    public void update(PreviewModel model, PreviewModel.Diff diff) {
        long start = System.nanoTime();
        ensureBuilders();
        this.refused = model.style() == PreviewModel.Style.REFUSED; // a change of style rebuilds every section (core's diff)
        for (SectionPos pos : diff.drop()) {
            GpuSection old = this.sections.remove(pos);
            if (old != null) {
                old.close();
            }
        }
        for (SectionPos pos : diff.rebuild()) {
            GpuSection old = this.sections.remove(pos);
            if (old != null) {
                old.close();
            }
            this.sections.put(pos, build(model.sections().get(pos)));
        }
        this.byRegion.clear();
        this.sections.values().forEach(section -> this.byRegion.computeIfAbsent(section.region(), r -> new ArrayList<>()).add(section));
        if (diff.handlesChanged()) {
            if (this.handles != null) {
                this.handles.close();
            }
            this.handles = buildHandles(model.handles());
        }
        this.blocks = model.blockCount();
        this.faces = model.faceCount();
        this.edges = model.edgeCount();
        this.buildMillis = (System.nanoTime() - start) / 1_000_000;
        this.timers.reset();
    }

    @Override
    public void clear() {
        this.sections.values().forEach(GpuSection::close);
        this.sections.clear();
        this.byRegion.clear();
        if (this.handles != null) {
            this.handles.close();
            this.handles = null;
        }
        this.blocks = 0;
        this.faces = 0;
        this.edges = 0;
    }

    boolean showing() {
        return !this.sections.isEmpty() || this.handles != null;
    }

    void setVisible(boolean visible) {
        this.visible = visible;
    }

    boolean outlinesOnly() {
        return this.outlinesOnly;
    }

    void setOutlinesOnly(boolean outlinesOnly) {
        this.outlinesOnly = outlinesOnly;
    }

    PreviewChoices.ShaderFaces shaderFaces() {
        return this.shaderFaces;
    }

    void setShaderFaces(PreviewChoices.ShaderFaces shaderFaces) {
        this.shaderFaces = shaderFaces;
    }

    boolean drawnOnce() {
        return this.drawnOnce;
    }

    Counts counts() {
        return new Counts(this.sections.size(), this.blocks, this.faces, this.edges, this.buildMillis, this.shadowPassSkips, this.faceFrames,
                this.outlineFrames, this.farFrames);
    }

    // ---- Drawing ----

    /** Called at the start of every frame: DH hasn't drawn the far part yet. */
    void frameStart() {
        this.farDrawn = false;
    }

    /**
     * The far part (F-7b): sections beyond vanilla's render distance, drawn inside DH's own pass, into DH's framebuffer
     * and in DH's projection, so DH's terrain hides them by depth like real terrain and they show past vanilla's far plane
     * (S7, findings 2 and 3). Nearer terrain drawn later covers them as usual. Called by {@link DhPreviewCompat} just
     * before DH copies its picture onto Minecraft's; GL state is set raw and put back, as DH sets its own state raw.
     * <ul>
     *   <li><b>Outlines only</b> (owner, 2026-10-12): that far out a block is a few pixels wide and the grid covers it
     *       anyway, and faces would double the far part's draws (the 2 ms budget).</li>
     *   <li>With a shader pack, Iris draws DH's terrain in its own projection (other near and far planes), so that one is
     *       used; and Iris's programs bind their own framebuffer when applied, so DH's is bound again. Position-colour
     *       lines show in DH's buffers in both Complementary and IndusShaders.</li>
     * </ul>
     */
    void renderFar(DhPreviewCompat.Frame frame) {
        // DH's first frames can carry an unset projection (NaN); vanilla's pass then draws everything.
        if (!this.visible || !showing() || IrisCompat.renderingShadowPass() || !frame.projection().isFinite()) {
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        Matrix4f irisProjection = IrisCompat.dhProjection();
        Matrix4f dhProjection = irisProjection != null ? irisProjection : frame.projection();
        Vec3d camera = minecraft.gameRenderer.getCamera().getPos();
        int rd = minecraft.options.getClampedViewDistance();
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (DEBUG_DH && (this.farFrames % 600) == 0) {
            LOGGER.info("[stratos] DH far pass: framebuffer {}, DH depth texture {}, shader pack {}, Iris's projection {}, far sections {}", framebuffer,
                    DhPreviewCompat.depthTexture(), IrisCompat.shaderPackInUse(), irisProjection != null, countFar(camera, rd));
        }
        this.farDrawn = true;
        this.farFrames++;
        ShaderProgram shader = GameRenderer.getPositionColorProgram();
        if (shader == null) {
            return;
        }
        this.timers.begin(PreviewTimers.FAR);
        Frustum frustum = new Frustum(frame.modelView(), dhProjection);
        frustum.setPosition(camera.x, camera.y, camera.z);
        // A small constant pull towards the camera in clip space: the road's top edges lie on DH's ground.
        Matrix4f projection = new Matrix4f().translation(0F, 0F, -FAR_DEPTH_BIAS).mul(dhProjection);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GL11.glLineWidth(2.0F);
        int cameraChunkX = MathHelper.floor(camera.x) >> 4;
        int cameraChunkZ = MathHelper.floor(camera.z) >> 4;
        boolean applied = false;
        for (Map.Entry<Region, List<GpuSection>> region : this.byRegion.entrySet()) {
            boolean regionSet = false;
            for (GpuSection section : region.getValue()) {
                if (section.outlines() == null || !isFar(section, cameraChunkX, cameraChunkZ, rd) || !frustum.isVisible(section.bounds())) {
                    continue;
                }
                if (!regionSet) {
                    Region r = region.getKey();
                    Matrix4f pose = new Matrix4f(frame.modelView()).translate((float) (r.anchorX() - camera.x), (float) (r.anchorY() - camera.y),
                            (float) (r.anchorZ() - camera.z));
                    RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                    // The program cache can be stale here (DH binds its own programs raw), so clear first: apply then binds it.
                    shader.unbind();
                    setUpShader(shader, pose, projection);
                    shader.bind();
                    // With a shader pack, Iris's program binds its own framebuffer on apply: draw into DH's, against its depth.
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
                    // State after apply, raw: apply's blend goes through RenderSystem's cache, which DH bypasses. Outlines
                    // write depth, as in vanilla's pass, so DH's water drawn later counts them as in front.
                    GL11.glEnable(GL11.GL_BLEND);
                    GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GL11.glDepthFunc(GL11.GL_LEQUAL);
                    GL11.glDepthMask(true);
                    regionSet = true;
                    applied = true;
                }
                section.outlines().bind();
                section.outlines().draw();
            }
        }
        if (applied) {
            shader.unbind();
            VertexBuffer.unbind();
        }
        GL11.glLineWidth(1.0F);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        setEnabled(GL11.GL_BLEND, blend);
        setEnabled(GL11.GL_DEPTH_TEST, depthTest);
        GL11.glDepthFunc(depthFunc);
        GL11.glDepthMask(depthMask);
        this.timers.end(PreviewTimers.FAR);
    }

    /**
     * Whether a section's chunk is beyond vanilla's render distance (in chunks, either way: the square vanilla draws), where
     * only DH draws terrain. Nearer sections stay in vanilla's pass.
     */
    private static boolean isFar(GpuSection section, int cameraChunkX, int cameraChunkZ, int rd) {
        int chunkX = MathHelper.floor(section.bounds().minX) >> 4;
        int chunkZ = MathHelper.floor(section.bounds().minZ) >> 4;
        return Math.max(Math.abs(chunkX - cameraChunkX), Math.abs(chunkZ - cameraChunkZ)) > rd;
    }

    private int countFar(Vec3d camera, int rd) {
        int cx = MathHelper.floor(camera.x) >> 4;
        int cz = MathHelper.floor(camera.z) >> 4;
        return (int) this.sections.values().stream().filter(s -> isFar(s, cx, cz, rd)).count();
    }

    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    void renderFaces(WorldRenderContext context) {
        if (!this.visible || this.outlinesOnly || this.sections.isEmpty()) {
            return;
        }
        if (IrisCompat.renderingShadowPass()) {
            this.shadowPassSkips++;
            return;
        }
        this.timers.begin(PreviewTimers.FACES);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.depthMask(false);
        boolean shaderPack = IrisCompat.shaderPackInUse();
        // UI-1c: BSL and Bliss show both draws, so their faces look too solid; Settings' "Faces with a shader pack" says
        // whether to leave out the first (Auto: for those two, by the pack's file name).
        this.drawnOnce = shaderPack && this.shaderFaces.once(IrisCompat.shaderPackName());
        if (!this.drawnOnce) {
            draw(context, GpuSection::faces, GameRenderer.getPositionColorProgram());
        }
        if (shaderPack) {
            // Shader mode (S7, finding 5). Iris sends the position-colour draw above to the pack's gbuffers_basic: forward
            // packs (Complementary) show it, but deferred packs (IndusShaders) have already lit their G-buffer by now.
            // Drawn with the translucent-entity shader, the faces take a deferred pack's forward path and show. A
            // depth-only pass first tells the pack how far away the nearest ghost surface is (in front of water or not);
            // then only that surface is coloured.
            ensureWhiteTexture();
            ShaderProgram shader = GameRenderer.getRenderTypeEntityTranslucentProgram();
            MinecraftClient minecraft = MinecraftClient.getInstance();
            RenderSystem.setShaderTexture(0, WHITE);
            minecraft.gameRenderer.getOverlayTexture().setupOverlayColor();
            minecraft.gameRenderer.getLightmapTextureManager().enable();
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(true);
            draw(context, GpuSection::entityFaces, shader);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);
            draw(context, GpuSection::entityFaces, shader);
            minecraft.gameRenderer.getLightmapTextureManager().disable();
            minecraft.gameRenderer.getOverlayTexture().teardownOverlayColor();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        this.timers.end(PreviewTimers.FACES);
        this.faceFrames++;
    }

    void renderOutlines(WorldRenderContext context) {
        if (!this.visible || !showing()) {
            return;
        }
        if (IrisCompat.renderingShadowPass()) {
            this.shadowPassSkips++;
            return;
        }
        this.timers.begin(PreviewTimers.OUTLINES);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        // Outlines write depth. Without it, water drawn later counts as in front of them: plain water tints them, and
        // IndusShaders' refraction bends them even high above the water (S7).
        RenderSystem.depthMask(true);
        RenderSystem.lineWidth(2.0F);
        ShaderProgram shader = GameRenderer.getPositionColorProgram();
        draw(context, GpuSection::outlines, shader);
        if (this.handles != null) {
            drawRegion(context, this.handles.region(), List.of(this.handles), GpuSection::outlines, shader, true, s -> false);
            shader.unbind();
            VertexBuffer.unbind();
        }
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
        this.timers.end(PreviewTimers.OUTLINES);
        this.outlineFrames++;
    }

    /** Draws one buffer of every visible section, setting the shader up once per region. The shader comes from the caller each frame: with a shader pack, Iris hands out its own. */
    private void draw(WorldRenderContext context, Function<GpuSection, VertexBuffer> which, ShaderProgram shader) {
        if (shader == null) {
            return;
        }
        boolean applied = false;
        // Sections DH's pass drew this frame are left to it (F-7b).
        Predicate<GpuSection> skip = s -> false;
        if (this.farDrawn) {
            Vec3d camera = context.camera().getPos();
            int rd = MinecraftClient.getInstance().options.getClampedViewDistance();
            int cx = MathHelper.floor(camera.x) >> 4;
            int cz = MathHelper.floor(camera.z) >> 4;
            skip = s -> isFar(s, cx, cz, rd);
        }
        for (Map.Entry<Region, List<GpuSection>> region : this.byRegion.entrySet()) {
            applied |= drawRegion(context, region.getKey(), region.getValue(), which, shader, false, skip);
        }
        if (applied) {
            shader.unbind();
            VertexBuffer.unbind();
        }
    }

    /** Returns whether anything was drawn (and so the shader applied). */
    private static boolean drawRegion(WorldRenderContext context, Region region, List<GpuSection> sections, Function<GpuSection, VertexBuffer> which,
            ShaderProgram shader, boolean always, Predicate<GpuSection> skip) {
        Frustum frustum = context.frustum();
        boolean applied = false;
        for (GpuSection section : sections) {
            VertexBuffer buffer = which.apply(section);
            if (buffer == null || (!always && (skip.test(section) || frustum != null && !frustum.isVisible(section.bounds())))) {
                continue;
            }
            if (!applied) {
                RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                Vec3d camera = context.camera().getPos();
                MatrixStack pose = context.matrixStack();
                pose.push();
                pose.translate(region.anchorX() - camera.x, region.anchorY() - camera.y, region.anchorZ() - camera.z);
                setUpShader(shader, pose.peek().getPositionMatrix(), context.projectionMatrix());
                pose.pop();
                shader.bind();
                applied = true;
            }
            buffer.bind();
            buffer.draw();
        }
        return applied;
    }

    /** The uniforms {@code VertexBuffer.drawWithShader} sets before every draw; here once per region and pass. */
    private static void setUpShader(ShaderProgram shader, Matrix4f modelView, Matrix4f projection) {
        for (int i = 0; i < 12; i++) {
            shader.addSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }
        if (shader.modelViewMat != null) {
            shader.modelViewMat.set(modelView);
        }
        if (shader.projectionMat != null) {
            shader.projectionMat.set(projection);
        }
        if (shader.viewRotationMat != null) {
            shader.viewRotationMat.set(RenderSystem.getInverseViewRotationMatrix());
        }
        if (shader.colorModulator != null) {
            shader.colorModulator.set(RenderSystem.getShaderColor());
        }
        if (shader.fogStart != null) {
            shader.fogStart.set(RenderSystem.getShaderFogStart());
        }
        if (shader.fogEnd != null) {
            shader.fogEnd.set(RenderSystem.getShaderFogEnd());
        }
        if (shader.fogColor != null) {
            shader.fogColor.set(RenderSystem.getShaderFogColor());
        }
        if (shader.fogShape != null) {
            shader.fogShape.set(RenderSystem.getShaderFogShape().getId());
        }
        if (shader.textureMat != null) {
            shader.textureMat.set(RenderSystem.getTextureMatrix());
        }
        if (shader.gameTime != null) {
            shader.gameTime.set(RenderSystem.getShaderGameTime());
        }
        if (shader.screenSize != null) {
            Window window = MinecraftClient.getInstance().getWindow();
            shader.screenSize.set((float) window.getWidth(), (float) window.getHeight());
        }
        RenderSystem.setupShaderLights(shader);
    }

    /** A 16×16 white texture, so the entity shader's texture lookup leaves the vertex colour as it is. */
    private void ensureWhiteTexture() {
        if (!this.whiteReady) {
            NativeImage image = new NativeImage(16, 16, false);
            image.fillRect(0, 0, 16, 16, 0xFFFFFFFF);
            MinecraftClient.getInstance().getTextureManager().registerTexture(WHITE, new NativeImageBackedTexture(image));
            this.whiteReady = true;
        }
    }

    // ---- Building the buffers ----

    private void ensureBuilders() {
        if (this.faceBuilder == null) {
            // Reused for every section and every update: BufferBuilder memory isn't freed on its own.
            this.faceBuilder = new BufferBuilder(1 << 16);
            this.entityBuilder = new BufferBuilder(1 << 16);
            this.lineBuilder = new BufferBuilder(1 << 16);
        }
    }

    private GpuSection build(SectionMesh mesh) {
        Region region = Region.of(mesh.pos());
        int ax = region.anchorX();
        int ay = region.anchorY();
        int az = region.anchorZ();
        int[] rgb = new int[mesh.states().size()];
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = this.refused ? REFUSED_RGB : colour(mesh.states().get(i));
        }
        this.faceBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        this.entityBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        for (SectionMesh.Face face : mesh.faces()) {
            addFace(face.x() - ax, face.y() - ay, face.z() - az, face.side(), rgb[face.state()], face.height8());
        }
        VertexBuffer faceBuffer = upload(this.faceBuilder, mesh.faces().size());
        VertexBuffer entityBuffer = upload(this.entityBuilder, mesh.faces().size());
        this.lineBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int outline = this.refused ? REFUSED_RGB : OUTLINE_RGB;
        int r = (outline >> 16) & 0xFF;
        int g = (outline >> 8) & 0xFF;
        int b = outline & 0xFF;
        for (SectionMesh.Edge edge : mesh.edges()) {
            float x = edge.x() - ax;
            float y = edge.y() - ay + edge.lift8() / 8F; // partial blocks' edges sit at their heights (G-7)
            float z = edge.z() - az;
            this.lineBuilder.vertex(x, y, z).color(r, g, b, OUTLINE_ALPHA).next();
            switch (edge.axis()) {
                case X -> this.lineBuilder.vertex(x + 1, y, z).color(r, g, b, OUTLINE_ALPHA).next();
                case Y -> this.lineBuilder.vertex(x, y + edge.length8() / 8F, z).color(r, g, b, OUTLINE_ALPHA).next();
                case Z -> this.lineBuilder.vertex(x, y, z + 1).color(r, g, b, OUTLINE_ALPHA).next();
            }
        }
        VertexBuffer lineBuffer = upload(this.lineBuilder, mesh.edges().size());
        int ox = mesh.pos().x() << 4;
        int oy = mesh.pos().y() << 4;
        int oz = mesh.pos().z() << 4;
        return new GpuSection(region, new Box(ox, oy, oz, ox + 16, oy + 16, oz + 16), faceBuffer, entityBuffer, lineBuffer);
    }

    /** Each handle: a box a little larger than its block, and a post up from the box's top. */
    private GpuSection buildHandles(List<Handle> handles) {
        if (handles.isEmpty()) {
            return null;
        }
        Handle first = handles.get(0);
        Region region = Region.ofBlock(first.x(), first.y(), first.z());
        this.lineBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int lines = 0;
        for (Handle handle : handles) {
            // The handle under the crosshair, or held, is in the accent (R-3); a refused plan's are all in the error colour.
            int colour = this.refused ? REFUSED_RGB : handle.active() ? ACTIVE_HANDLE_RGB : HANDLE_RGB;
            int r = (colour >> 16) & 0xFF;
            int g = (colour >> 8) & 0xFF;
            int b = colour & 0xFF;
            float x0 = handle.x() - region.anchorX() - 0.15F;
            float y0 = handle.y() - region.anchorY() - 0.15F;
            float z0 = handle.z() - region.anchorZ() - 0.15F;
            float x1 = x0 + 1.3F;
            float y1 = y0 + 1.3F;
            float z1 = z0 + 1.3F;
            float[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            int[][] boxEdges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] edge : boxEdges) {
                float[] p = corners[edge[0]];
                float[] q = corners[edge[1]];
                this.lineBuilder.vertex(p[0], p[1], p[2]).color(r, g, b, 255).next();
                this.lineBuilder.vertex(q[0], q[1], q[2]).color(r, g, b, 255).next();
                lines++;
            }
            float cx = (x0 + x1) / 2;
            float cz = (z0 + z1) / 2;
            this.lineBuilder.vertex(cx, y1, cz).color(r, g, b, 255).next();
            this.lineBuilder.vertex(cx, y1 + 2.5F, cz).color(r, g, b, 255).next();
            lines++;
        }
        VertexBuffer buffer = upload(this.lineBuilder, lines);
        return new GpuSection(region, null, null, null, buffer);
    }

    private static VertexBuffer upload(BufferBuilder builder, int count) {
        BufferBuilder.BuiltBuffer rendered = builder.end();
        if (count == 0) {
            rendered.release();
            return null;
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(rendered);
        VertexBuffer.unbind();
        return buffer;
    }

    /** The map colour of a state string (a road line's virtual state shows its paint). */
    private static int colour(String state) {
        int rgb = BlockColours.map(state);
        return rgb == BlockColours.NO_COLOUR ? NO_COLOUR : rgb;
    }

    /**
     * One face, wound counter-clockwise seen from outside (as vanilla's FaceInfo) and shaded like vanilla: once as
     * position-colour vertices and once as entity vertices (white-texture UV, no overlay, full-bright, the face's
     * normal). Coordinates are relative to the region's anchor. A partial block ({@code height8} eighths high, G-7) has
     * its top lowered and its sides shortened.
     */
    private void addFace(int x, int y, int z, Side side, int rgb, int height8) {
        float shade = switch (side) {
            case UP -> 1.0F;
            case DOWN -> 0.5F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
        int r = (int) (((rgb >> 16) & 0xFF) * shade);
        int g = (int) (((rgb >> 8) & 0xFF) * shade);
        int b = (int) ((rgb & 0xFF) * shade);
        float x0 = x - INFLATE;
        float y0 = y - INFLATE;
        float z0 = z - INFLATE;
        float x1 = x + 1 + INFLATE;
        float y1 = y + height8 / 8F + INFLATE;
        float z1 = z + 1 + INFLATE;
        float[] xyz = switch (side) {
            case UP -> new float[] {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0};
            case DOWN -> new float[] {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1};
            case NORTH -> new float[] {x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0};
            case SOUTH -> new float[] {x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1};
            case WEST -> new float[] {x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1};
            case EAST -> new float[] {x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0};
        };
        for (int i = 0; i < 12; i += 3) {
            this.faceBuilder.vertex(xyz[i], xyz[i + 1], xyz[i + 2]).color(r, g, b, FACE_ALPHA).next();
            this.entityBuilder.vertex(xyz[i], xyz[i + 1], xyz[i + 2]).color(r, g, b, FACE_ALPHA).texture(0.5F, 0.5F)
                    .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal(side.dx(), side.dy(), side.dz()).next();
        }
    }
}
