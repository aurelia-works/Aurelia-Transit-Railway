package com.aureliatransit.architecture.client.airport;

import com.aureliatransit.architecture.airport.AirportDepartures;
import com.aureliatransit.architecture.block.DeparturesBoardBlock;
import com.aureliatransit.architecture.block.entity.DeparturesBoardBlockEntity;
import com.aureliatransit.architecture.client.interactive.PanelDrawer;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

/**
 * Flight rows on the departures board's face. The frame is the block model; this only draws the text.
 */
public final class DeparturesBoardRenderer implements BlockEntityRenderer<DeparturesBoardBlockEntity> {

	private static final int COLOUR = 0xFFE7F2FF;
	private final TextRenderer textRenderer;

	public DeparturesBoardRenderer(BlockEntityRendererFactory.Context context) {
		this.textRenderer = context.getTextRenderer();
	}

	@Override
	public void render(DeparturesBoardBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (entity.getWorld() != null) {
			entity.advance(entity.getWorld().getTime());
		}
		final BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof DeparturesBoardBlock)) {
			return;
		}
		final List<String> lines = entity.lines();
		final List<String> shown = lines.isEmpty() ? List.of(I18n.translate(AirportDepartures.NONE_KEY)) : lines;
		final int textLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		matrices.push();
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(state.get(DeparturesBoardBlock.FACING))));
		matrices.translate(-0.5, 0, -0.5);
		PanelDrawer.beginFace(matrices, 8, 8, 13, true);
		matrices.scale(0.01F, -0.01F, 0.01F);
		float y = -shown.size() * 5F;
		for (final String line : shown) {
			final int width = textRenderer.getWidth(line);
			textRenderer.draw(line, -width / 2F, y, COLOUR, false, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, textLight);
			y += 10F;
		}
		PanelDrawer.endFace(matrices);
		matrices.pop();
	}
}
