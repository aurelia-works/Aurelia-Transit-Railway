package org.mtr.aurelia.vehicle.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.mtr.aurelia.vehicle.RoadVehicleEntity;
import org.mtr.aurelia.vehicle.RoadVehicleKind;
import org.mtr.aurelia.vehicle.RoadVehicles;

import java.util.EnumMap;
import java.util.Map;

/** Draws a road vehicle from its Blockbench model: body in the panel colour, front wheels steered, all wheels rolling. */
public final class RoadVehicleRenderer extends EntityRenderer<RoadVehicleEntity> {

	private final Map<RoadVehicleKind, BlockbenchMesh> models = new EnumMap<>(RoadVehicleKind.class);
	private final Map<RoadVehicleKind, Identifier> textures = new EnumMap<>(RoadVehicleKind.class);

	public RoadVehicleRenderer(EntityRendererFactory.Context context) {
		super(context);
		shadowRadius = 2.1F;
		for (final RoadVehicleKind kind : RoadVehicleKind.values()) {
			models.put(kind, BlockbenchMesh.load(context.getResourceManager(), new Identifier("mtr", "models/road_vehicle/" + kind.model + ".bbmodel")));
			textures.put(kind, new Identifier("mtr", "textures/entity/road_vehicle/" + kind.model + ".png"));
		}
	}

	@Override
	public void render(RoadVehicleEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		final BlockbenchMesh model = models.get(entity.kind());
		if (model != null) {
			final float scale = (float) entity.scale();
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw())));
			matrices.scale(scale / 16, scale / 16, scale / 16);
			final float spin = MathHelper.lerp(tickDelta, entity.prevWheelSpin, entity.wheelSpin);
			model.render(matrices, vertexConsumers, getTexture(entity), light, OverlayTexture.DEFAULT_UV, RoadVehicles.settings(true).colour(entity.kind()), -entity.steer(), spin);
			matrices.pop();
		}
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(RoadVehicleEntity entity) {
		return textures.get(entity.kind());
	}
}
