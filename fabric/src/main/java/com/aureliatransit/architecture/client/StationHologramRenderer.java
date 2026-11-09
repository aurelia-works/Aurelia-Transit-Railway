package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LocalDir;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.studio.Connections;
import com.aureliatransit.architecture.station.studio.StudioApi;
import com.aureliatransit.architecture.station.world.BlockStateResolver;
import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.math.Vec3i;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Draws the planning player's pending station as a Litematica-style hologram: see-through copies of the real planned blocks
 * with their real models, built once per plan change into static vertex buffers and only drawn during the frame. Nothing is
 * sent to any other player and nothing is placed in the world.
 *
 * <p>The plan arrives as one compressed packet, is meshed on a worker, and is split into 32-block columns so a far part of
 * the station can be skipped or drawn as a simple translucent box while the part around the camera keeps full detail. The
 * red tint on cells that replace existing blocks and the single-height layer are baked into the mesh; changing either
 * rebuilds it once, on the dev panel's action, never per frame.</p>
 */
public final class StationHologramRenderer {

	private static final int PROTOCOL = 1;
	private static final int TYPE_CLEAR = 0;
	private static final int TYPE_PLAN = 1;
	private static final int MAX_COMPRESSED = 16 << 20;
	private static final int MAX_RAW = 64 << 20;
	private static final int MAX_BLOCKS = 400_000;
	private static final int MAX_PALETTE = 4096;
	private static final int MAX_ID = 256;
	private static final int MAX_STATE = 512;
	private static final int SECTION_SHIFT = 5;
	private static final double FULL_DISTANCE = 48;
	private static final double BOX_DISTANCE = 160;
	private static final float ALPHA = 0.55F;
	private static final float BOX_ALPHA = 0.3F;
	private static final float TINT_R = 1.0F;
	private static final float TINT_G = 0.28F;
	private static final float TINT_B = 0.28F;
	private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
	private static final Direction[] DIRECTIONS = Direction.values();

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "ATR station hologram");
		thread.setDaemon(true);
		return thread;
	});
	private static final AtomicInteger GENERATION = new AtomicInteger();

	private static Hologram hologram;
	private static List<Section> sections = List.of();
	private static boolean building;
	private static boolean rebuildQueued;
	private static int minLayer;
	private static int maxLayer;

	private StationHologramRenderer() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.STATION_HOLOGRAM, (client, handler, buf, response) -> {
			final Update update = read(buf);
			if (update != null) {
				client.execute(() -> apply(update));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(StationHologramRenderer::clear));
		WorldRenderEvents.AFTER_TRANSLUCENT.register(StationHologramRenderer::render);
	}

	/** The plan heights the layer slider can reach; both 0 when no plan is shown. */
	public static int minLayer() {
		return minLayer;
	}

	public static int maxLayer() {
		return maxLayer;
	}

	/** Rebuilds the mesh for a drawing switch; one rebuild per click, never in the frame loop. */
	public static void settingsChanged() {
		requestBuild();
	}

	/** Tells the server whether this client wants the old particle outline instead of the hologram. */
	public static void sendParticlesSetting() {
		if (ClientPlayNetworking.canSend(ModPackets.STATION_HOLOGRAM_SETTINGS)) {
			ClientPlayNetworking.send(ModPackets.STATION_HOLOGRAM_SETTINGS, ModPackets.writeHologramSettings(HologramSettings.particles()));
		}
	}

	private static void clear() {
		GENERATION.incrementAndGet();
		hologram = null;
		minLayer = 0;
		maxLayer = 0;
		rebuildQueued = false;
		closeSections();
	}

	private static void apply(Update update) {
		GENERATION.incrementAndGet();
		rebuildQueued = false;
		closeSections();
		if (update.hologram() == null) {
			hologram = null;
			minLayer = 0;
			maxLayer = 0;
			return;
		}
		hologram = update.hologram();
		minLayer = hologram.minY();
		maxLayer = hologram.maxY();
		HologramSettings.clampLayer(minLayer, maxLayer);
		requestBuild();
	}

	private static void requestBuild() {
		GENERATION.incrementAndGet();
		if (hologram == null || !HologramSettings.enabled()) {
			closeSections();
			return;
		}
		if (building) {
			rebuildQueued = true;
			return;
		}
		building = true;
		final int ticket = GENERATION.get();
		final Hologram snapshot = hologram;
		final int layer = HologramSettings.layer();
		final boolean tint = HologramSettings.tint();
		WORKER.execute(() -> {
			BuiltMesh built;
			try {
				built = build(snapshot, layer, tint);
			} catch (RuntimeException e) {
				AureliaTransitArchitecture.LOGGER.warn("Station hologram could not be meshed", e);
				built = BuiltMesh.EMPTY;
			}
			final BuiltMesh result = built;
			MinecraftClient.getInstance().execute(() -> finish(ticket, result));
		});
	}

	private static void finish(int ticket, BuiltMesh built) {
		building = false;
		if (ticket != GENERATION.get() || hologram == null || !HologramSettings.enabled()) {
			release(built);
		} else {
			upload(built);
		}
		if (rebuildQueued) {
			rebuildQueued = false;
			requestBuild();
		}
	}

	private static void upload(BuiltMesh built) {
		if (built.sections().isEmpty()) {
			closeSections();
			return;
		}
		final List<Section> uploaded = new ArrayList<>(built.sections().size());
		try {
			for (final BuiltSection part : built.sections()) {
				final Section section = new Section(part.minX(), part.minZ(), part.baseY(), part.bounds());
				if (part.full() != null) {
					section.full = new VertexBuffer(VertexBuffer.Usage.STATIC);
					section.full.bind();
					section.full.upload(part.full());
				}
				if (part.boxes() != null) {
					section.boxes = new VertexBuffer(VertexBuffer.Usage.STATIC);
					section.boxes.bind();
					section.boxes.upload(part.boxes());
				}
				uploaded.add(section);
			}
		} catch (RuntimeException e) {
			VertexBuffer.unbind();
			uploaded.forEach(Section::close);
			AureliaTransitArchitecture.LOGGER.warn("Station hologram could not be uploaded", e);
			return;
		}
		VertexBuffer.unbind();
		closeSections();
		sections = uploaded;
	}

	private static void closeSections() {
		for (final Section section : sections) {
			section.close();
		}
		sections = List.of();
	}

	private static void release(BuiltMesh built) {
		for (final BuiltSection section : built.sections()) {
			if (section.full() != null) {
				section.full().release();
			}
			if (section.boxes() != null) {
				section.boxes().release();
			}
		}
	}

	// ---- reading the packet ------------------------------------------------------------------------------------------

	@Nullable
	private static Update read(PacketByteBuf buf) {
		try {
			final int type = buf.readUnsignedByte();
			if (type == TYPE_CLEAR) {
				return new Update(null);
			}
			if (type != TYPE_PLAN) {
				return null;
			}
			final int rawLength = buf.readVarInt();
			if (rawLength < 1 || rawLength > MAX_RAW) {
				return null;
			}
			final byte[] compressed = buf.readByteArray(MAX_COMPRESSED);
			final byte[] raw = inflate(compressed, rawLength);
			if (raw == null) {
				return null;
			}
			final Hologram read = decode(raw);
			return read == null ? null : new Update(read);
		} catch (RuntimeException e) {
			AureliaTransitArchitecture.LOGGER.warn("Ignored a malformed station hologram", e);
			return null;
		}
	}

	@Nullable
	private static byte[] inflate(byte[] compressed, int rawLength) {
		final Inflater inflater = new Inflater();
		final byte[] raw = new byte[rawLength];
		int written = 0;
		try {
			inflater.setInput(compressed);
			while (written < rawLength && !inflater.finished()) {
				final int count = inflater.inflate(raw, written, rawLength - written);
				if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					return null;
				}
				written += count;
			}
		} catch (DataFormatException e) {
			return null;
		} finally {
			inflater.end();
		}
		return written == rawLength ? raw : null;
	}

	@Nullable
	private static Hologram decode(byte[] raw) {
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(raw));
		try {
			if (buf.readVarInt() != PROTOCOL) {
				return null;
			}
			final String dimension = buf.readString(MAX_ID);
			final int ox = buf.readInt();
			final int oy = buf.readInt();
			final int oz = buf.readInt();
			final int forwardIndex = buf.readVarInt();
			if (forwardIndex < 0 || forwardIndex >= Heading.values().length) {
				return null;
			}
			final Heading forward = Heading.values()[forwardIndex];
			final int paletteSize = buf.readVarInt();
			if (paletteSize < 0 || paletteSize > MAX_PALETTE) {
				return null;
			}
			final String[] ids = new String[paletteSize];
			final String[] states = new String[paletteSize];
			for (int i = 0; i < paletteSize; i++) {
				ids[i] = buf.readString(MAX_ID);
				states[i] = buf.readString(MAX_STATE);
			}
			final int count = buf.readVarInt();
			if (count < 0 || count > MAX_BLOCKS) {
				return null;
			}
			final LocalFrame local = new LocalFrame(0, 0, 0, forward);
			final List<Cell> cells = new ArrayList<>(count);
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			for (int i = 0; i < count; i++) {
				final int palette = buf.readVarInt();
				if (palette < 0 || palette >= paletteSize) {
					return null;
				}
				final int u = readSigned(buf);
				final int y = readSigned(buf);
				final int v = readSigned(buf);
				final int frontIndex = buf.readVarInt();
				final int roleIndex = buf.readVarInt();
				if (frontIndex < 0 || frontIndex >= LocalDir.values().length || roleIndex < 0 || roleIndex >= Role.values().length) {
					return null;
				}
				final boolean replace = buf.readBoolean();
				final PlannedBlock block = new PlannedBlock(u, y, v, Role.values()[roleIndex], ids[palette],
						LocalDir.values()[frontIndex], "", states[palette]);
				// local coordinates, so the join lookup never depends on the world position or overflows its packing
				cells.add(new Cell(block, local.x(u, v), y, local.z(u, v), replace));
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
			if (cells.isEmpty()) {
				minY = 0;
				maxY = 0;
			}
			return new Hologram(dimension, ox, oy, oz, forward, List.copyOf(cells), minY, maxY);
		} catch (RuntimeException e) {
			return null;
		} finally {
			buf.release();
		}
	}

	private static int readSigned(PacketByteBuf buf) {
		final int value = buf.readVarInt();
		return (value >>> 1) ^ -(value & 1);
	}

	// ---- meshing, on the worker --------------------------------------------------------------------------------------

	private static BuiltMesh build(Hologram plan, int layer, boolean tint) {
		final MinecraftClient client = MinecraftClient.getInstance();
		// The joins are the same at any origin, so the mesh is built in the plan's own coordinates and moved when drawn.
		final LocalFrame frame = new LocalFrame(0, 0, 0, plan.forward());
		final Map<Long, PlannedBlock> at = new HashMap<>();
		for (final Cell cell : plan.cells()) {
			at.put(StudioApi.cell(cell.x(), cell.block().y(), cell.z()), cell.block());
		}
		final Map<String, Optional<Block>> blocks = new HashMap<>();
		final Connections.FullFace full = (other, heading, side) ->
				blocks.computeIfAbsent(other.blockId(), BlockStateResolver::block)
						.map(b -> BlockStateResolver.withState(BlockStateResolver.state(b, heading), other.state())
								.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, BlockStateResolver.direction(side)))
						.orElse(false);
		final List<StateCell> visible = new ArrayList<>();
		int minY = Integer.MAX_VALUE;
		for (final Cell cell : plan.cells()) {
			if (layer != HologramSettings.ALL_LAYERS && cell.block().y() != layer) {
				continue;
			}
			final Optional<Block> found = blocks.computeIfAbsent(cell.block().blockId(), BlockStateResolver::block);
			if (found.isEmpty()) {
				continue;
			}
			final Heading heading = frame.heading(cell.block().front());
			final BlockState state = BlockStateResolver.withState(BlockStateResolver.state(found.get(), heading),
					Connections.state(cell.block(), at, full, frame));
			visible.add(new StateCell(cell, state));
			minY = Math.min(minY, cell.block().y());
		}
		if (visible.isEmpty()) {
			return BuiltMesh.EMPTY;
		}
		final Map<Long, BlockState> cull = new HashMap<>();
		final Map<Long, List<StateCell>> groups = new LinkedHashMap<>();
		for (final StateCell stateCell : visible) {
			final Cell cell = stateCell.cell();
			cull.put(StudioApi.cell(cell.x(), cell.block().y(), cell.z()), stateCell.state());
			groups.computeIfAbsent(sectionKey(cell.x(), cell.z()), key -> new ArrayList<>()).add(stateCell);
		}
		final List<BuiltSection> built = new ArrayList<>(groups.size());
		final MatrixStack matrices = new MatrixStack();
		final Random random = Random.create(42L);
		final Map<BlockState, Boolean> fullCube = new HashMap<>();
		for (final Map.Entry<Long, List<StateCell>> entry : groups.entrySet()) {
			final int sectionLocalX = sectionX(entry.getKey()) << SECTION_SHIFT;
			final int sectionLocalZ = sectionZ(entry.getKey()) << SECTION_SHIFT;
			final int minX = plan.ox() + sectionLocalX;
			final int minZ = plan.oz() + sectionLocalZ;
			final BufferBuilder fullBuilder = new BufferBuilder(1 << 16);
			final BufferBuilder boxBuilder = new BufferBuilder(1 << 12);
			fullBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
			boxBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
			int boundMinX = Integer.MAX_VALUE;
			int boundMinY = Integer.MAX_VALUE;
			int boundMinZ = Integer.MAX_VALUE;
			int boundMaxX = Integer.MIN_VALUE;
			int boundMaxY = Integer.MIN_VALUE;
			int boundMaxZ = Integer.MIN_VALUE;
			for (final StateCell stateCell : entry.getValue()) {
				final Cell cell = stateCell.cell();
				final int localX = cell.x() - sectionLocalX;
				final int localY = cell.block().y() - minY;
				final int localZ = cell.z() - sectionLocalZ;
				boundMinX = Math.min(boundMinX, plan.ox() + cell.x());
				boundMinY = Math.min(boundMinY, plan.oy() + cell.y());
				boundMinZ = Math.min(boundMinZ, plan.oz() + cell.z());
				boundMaxX = Math.max(boundMaxX, plan.ox() + cell.x());
				boundMaxY = Math.max(boundMaxY, plan.oy() + cell.y());
				boundMaxZ = Math.max(boundMaxZ, plan.oz() + cell.z());
				matrices.push();
				matrices.translate(localX, localY, localZ);
				final BakedModel model = client.getBlockRenderManager().getModel(stateCell.state());
				final boolean tinted = tint && cell.replace();
				for (final Direction direction : DIRECTIONS) {
					final BlockState neighbour = cull.get(StudioApi.cell(cell.x() + direction.getOffsetX(), cell.block().y() + direction.getOffsetY(),
							cell.z() + direction.getOffsetZ()));
					if (neighbour != null && fullCube.computeIfAbsent(neighbour, state -> state.isOpaqueFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN))) {
						continue;
					}
					random.setSeed(42L);
					quads(fullBuilder, matrices, model.getQuads(stateCell.state(), direction, random), tinted);
				}
				random.setSeed(42L);
				quads(fullBuilder, matrices, model.getQuads(stateCell.state(), null, random), tinted);
				box(boxBuilder, matrices.peek().getPositionMatrix(), tinted, mapColor(stateCell.state()));
				matrices.pop();
			}
			final BufferBuilder.BuiltBuffer fullBuffer = fullBuilder.isBatchEmpty() ? null : fullBuilder.end();
			final BufferBuilder.BuiltBuffer boxBuffer = boxBuilder.isBatchEmpty() ? null : boxBuilder.end();
			if (fullBuffer != null || boxBuffer != null) {
				final Box bounds = new Box(boundMinX, boundMinY, boundMinZ, boundMaxX + 1, boundMaxY + 1, boundMaxZ + 1);
				built.add(new BuiltSection(minX, minZ, plan.oy() + minY, bounds, fullBuffer, boxBuffer));
			}
		}
		return new BuiltMesh(List.copyOf(built));
	}

	private static void quads(BufferBuilder out, MatrixStack matrices, List<BakedQuad> quads, boolean tinted) {
		for (final BakedQuad quad : quads) {
			final float brightness = quad.hasShade() ? faceShade(quad.getFace()) : 1;
			final float red = (tinted ? TINT_R : 1) * brightness;
			final float green = (tinted ? TINT_G : 1) * brightness;
			final float blue = (tinted ? TINT_B : 1) * brightness;
			// the colour and alpha go on each vertex: BufferBuilder.quad writes through the fast vertex path, which throws while
			// a fixed colour is set, and it has no alpha of its own
			final MatrixStack.Entry entry = matrices.peek();
			final Vec3i face = quad.getFace().getVector();
			final Vector3f normal = entry.getNormalMatrix().transform(new Vector3f(face.getX(), face.getY(), face.getZ()));
			final int[] data = quad.getVertexData();
			final int stride = data.length / 4;
			for (int i = 0; i < 4; i++) {
				final int at = i * stride;
				final Vector4f pos = entry.getPositionMatrix().transform(new Vector4f(Float.intBitsToFloat(data[at]),
						Float.intBitsToFloat(data[at + 1]), Float.intBitsToFloat(data[at + 2]), 1));
				out.vertex(pos.x(), pos.y(), pos.z(), red, green, blue, ALPHA, Float.intBitsToFloat(data[at + 4]),
						Float.intBitsToFloat(data[at + 5]), OverlayTexture.DEFAULT_UV, LIGHT, normal.x(), normal.y(), normal.z());
			}
		}
	}

	/** A block-sized box for the far level of detail, in the block's map colour or the red replacement colour. */
	private static void box(BufferBuilder out, Matrix4f matrix, boolean tinted, int rgb) {
		final float red = tinted ? TINT_R : ((rgb >> 16) & 255) / 255F;
		final float green = tinted ? TINT_G : ((rgb >> 8) & 255) / 255F;
		final float blue = tinted ? TINT_B : (rgb & 255) / 255F;
		final int alpha = (int) (BOX_ALPHA * 255);
		face(out, matrix, red, green, blue, alpha, 1.0F, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0);
		face(out, matrix, red, green, blue, alpha, 0.5F, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1);
		face(out, matrix, red, green, blue, alpha, 0.8F, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0);
		face(out, matrix, red, green, blue, alpha, 0.8F, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1);
		face(out, matrix, red, green, blue, alpha, 0.6F, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0);
		face(out, matrix, red, green, blue, alpha, 0.6F, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1);
	}

	private static void face(BufferBuilder out, Matrix4f matrix, float red, float green, float blue, int alpha, float shade,
			float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		final int r = (int) (red * shade * 255);
		final int g = (int) (green * shade * 255);
		final int b = (int) (blue * shade * 255);
		out.vertex(matrix, x0, y0, z0).color(r, g, b, alpha).next();
		out.vertex(matrix, x1, y1, z1).color(r, g, b, alpha).next();
		out.vertex(matrix, x2, y2, z2).color(r, g, b, alpha).next();
		out.vertex(matrix, x3, y3, z3).color(r, g, b, alpha).next();
	}

	private static int mapColor(BlockState state) {
		try {
			final int colour = state.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getRenderColor(MapColor.Brightness.NORMAL);
			return colour == 0 ? 0x9AA0A6 : colour & 0xFFFFFF;
		} catch (RuntimeException e) {
			return 0x9AA0A6;
		}
	}

	private static float faceShade(Direction face) {
		return switch (face) {
			case UP -> 1.0F;
			case DOWN -> 0.5F;
			case NORTH, SOUTH -> 0.8F;
			case EAST, WEST -> 0.6F;
		};
	}

	private static long sectionKey(int x, int z) {
		return ((long) (x >> SECTION_SHIFT) << 32) | ((z >> SECTION_SHIFT) & 0xFFFFFFFFL);
	}

	private static int sectionX(long key) {
		return (int) (key >> 32);
	}

	private static int sectionZ(long key) {
		return (int) key;
	}

	// ---- drawing, on the render thread -------------------------------------------------------------------------------

	private static void render(WorldRenderContext context) {
		if (!HologramSettings.enabled() || hologram == null || sections.isEmpty()) {
			return;
		}
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || !hologram.dimension().equals(client.world.getRegistryKey().getValue().toString())) {
			return;
		}
		final Frustum frustum = context.frustum();
		if (context.camera() == null || frustum == null) {
			return;
		}
		final Vec3d camera = context.camera().getPos();
		final Matrix4f base = context.matrixStack().peek().getPositionMatrix();
		final Matrix4f projection = context.projectionMatrix();
		final float[] shaderColor = RenderSystem.getShaderColor();
		final float red = shaderColor[0];
		final float green = shaderColor[1];
		final float blue = shaderColor[2];
		final float alpha = shaderColor[3];
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.depthMask(false);
		RenderSystem.enableDepthTest();
		RenderSystem.enablePolygonOffset();
		RenderSystem.polygonOffset(-1, -1);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
		try {
			// position_color_tex keeps the block texture but drops the lightmap, so the hologram is full-bright and unseen
			// faces stay see-through instead of turning into lit terrain.
			final ShaderProgram textured = GameRenderer.getPositionColorTexProgram();
			for (final Section section : sections) {
				final double distance = section.distanceSquared(camera);
				if (distance > BOX_DISTANCE * BOX_DISTANCE || section.full == null || distance > FULL_DISTANCE * FULL_DISTANCE) {
					continue;
				}
				if (!frustum.isVisible(section.bounds)) {
					continue;
				}
				section.full.bind();
				section.full.draw(section.view(base, camera), projection, textured);
			}
			final ShaderProgram color = GameRenderer.getPositionColorProgram();
			for (final Section section : sections) {
				final double distance = section.distanceSquared(camera);
				if (distance > BOX_DISTANCE * BOX_DISTANCE || section.boxes == null
						|| distance <= FULL_DISTANCE * FULL_DISTANCE && section.full != null) {
					continue;
				}
				if (!frustum.isVisible(section.bounds)) {
					continue;
				}
				section.boxes.bind();
				section.boxes.draw(section.view(base, camera), projection, color);
			}
			VertexBuffer.unbind();
		} finally {
			RenderSystem.setShaderColor(red, green, blue, alpha);
			RenderSystem.disablePolygonOffset();
			RenderSystem.depthMask(true);
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
		}
	}

	// ---- data --------------------------------------------------------------------------------------------------------

	private record Update(@Nullable Hologram hologram) {
	}

	private record Hologram(String dimension, int ox, int oy, int oz, Heading forward, List<Cell> cells, int minY, int maxY) {
	}

	private record Cell(PlannedBlock block, int x, int y, int z, boolean replace) {
	}

	private record StateCell(Cell cell, BlockState state) {
	}

	private record BuiltMesh(List<BuiltSection> sections) {
		private static final BuiltMesh EMPTY = new BuiltMesh(List.of());
	}

	private record BuiltSection(int minX, int minZ, int baseY, Box bounds, @Nullable BufferBuilder.BuiltBuffer full,
			@Nullable BufferBuilder.BuiltBuffer boxes) {
	}

	private static final class Section {

		private final int minX;
		private final int minZ;
		private final int baseY;
		private final Box bounds;
		private final double centerX;
		private final double centerY;
		private final double centerZ;
		@Nullable
		private VertexBuffer full;
		@Nullable
		private VertexBuffer boxes;

		private Section(int minX, int minZ, int baseY, Box bounds) {
			this.minX = minX;
			this.minZ = minZ;
			this.baseY = baseY;
			this.bounds = bounds;
			this.centerX = (bounds.minX + bounds.maxX) / 2;
			this.centerY = (bounds.minY + bounds.maxY) / 2;
			this.centerZ = (bounds.minZ + bounds.maxZ) / 2;
		}

		private double distanceSquared(Vec3d camera) {
			final double dx = centerX - camera.x;
			final double dy = centerY - camera.y;
			final double dz = centerZ - camera.z;
			return dx * dx + dy * dy + dz * dz;
		}

		private Matrix4f view(Matrix4f base, Vec3d camera) {
			return new Matrix4f(base).translate((float) (minX - camera.x), (float) (baseY - camera.y), (float) (minZ - camera.z));
		}

		private void close() {
			if (full != null) {
				full.close();
				full = null;
			}
			if (boxes != null) {
				boxes.close();
				boxes = null;
			}
		}
	}
}
