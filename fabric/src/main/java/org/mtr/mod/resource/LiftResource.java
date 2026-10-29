package org.mtr.mod.resource;

import org.mtr.core.serializer.ReaderBase;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.generated.resource.LiftResourceSchema;

public final class LiftResource extends LiftResourceSchema {

	public LiftResource(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return TextHelper.translatable(name).getString();
	}

	public int getColor() {
		return CustomResourceTools.colorStringToInt(color);
	}

	public Identifier getTexture() {
		return CustomResourceTools.formatIdentifierWithDefault(textureResource, "png");
	}

	/** Cab model id. Only {@link LiftStyleIds#MODEL_LIFT_1} is drawn today; other ids still use that model. */
	public String getModel() {
		return model == null || model.isEmpty() ? LiftStyleIds.MODEL_LIFT_1 : model;
	}

	/** {@link LiftStyleIds#GLASS} or {@link LiftStyleIds#OPAQUE_PART}, or a later custom token stored on the style. */
	public String getWalls() {
		return walls == null || walls.isEmpty() ? LiftStyleIds.GLASS : walls;
	}

	/** {@link LiftStyleIds#GLASS} or {@link LiftStyleIds#OPAQUE_PART}, or a later custom token stored on the style. */
	public String getDoors() {
		return doors == null || doors.isEmpty() ? LiftStyleIds.GLASS : doors;
	}

	/**
	 * Atlas the cab is drawn with. Declared {@code textureResource}, unless this style still points at the glass
	 * atlas while asking for opaque walls and opaque doors.
	 */
	public Identifier cabTexture() {
		return CustomResourceTools.formatIdentifierWithDefault(LiftStyleIds.cabTexturePath(textureResource, getWalls(), getDoors()), "png");
	}
}
