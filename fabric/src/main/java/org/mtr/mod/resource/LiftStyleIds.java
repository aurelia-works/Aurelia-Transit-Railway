package org.mtr.mod.resource;

/**
 * Lift style ids and how a style picks its cab texture.
 *
 * <p>The only value stored on a lift, and the only value sent over the network, is the style id string
 * ({@code Lift.style}). Walls and doors are not saved on the lift: they live on the style resource, so an old world
 * whose style is {@link #TRANSPARENT} (or blank) keeps the glass cab. An unknown id is left as-is in the save and
 * drawn as glass until that style exists again, which is what a missing resource pack needs.
 *
 * <p>Opaque walls and opaque doors are separate. {@link LiftGlassRegions} knows which texels belong to which,
 * so a later custom wall or door (upstream issue 598) can fill one part without a new saved field.
 */
public final class LiftStyleIds {

	/** Glass walls and a glass panel in each door. The style new lifts and generated stations are given. */
	public static final String TRANSPARENT = "default_transparent";
	/** Same cab, with the glass panes filled in. Chosen from the lift refresher. */
	public static final String OPAQUE = "default_opaque";

	public static final String MODEL_LIFT_1 = "mtr:lift_1";
	public static final String GLASS = "glass";
	public static final String OPAQUE_PART = "opaque";

	public static final String GLASS_TEXTURE = "mtr:textures/vehicle/lift_1.png";
	public static final String OPAQUE_TEXTURE = "mtr:textures/vehicle/lift_1_opaque.png";

	private LiftStyleIds() {
	}

	/**
	 * Style id to draw. {@code known} is false when the id is not in the loaded resources.
	 * Blank and unknown ids draw as {@link #TRANSPARENT}; the saved string is not rewritten.
	 */
	public static String resolve(String styleId, boolean known) {
		if (!known || styleId == null || styleId.isEmpty()) {
			return TRANSPARENT;
		}
		return styleId;
	}

	/**
	 * Index of the glass style, or 0 when that id is absent (a pack replaced the whole list).
	 * Matches {@code CustomResourceLoader.defaultLiftResource}.
	 */
	public static int fallbackIndex(Iterable<String> ids) {
		int index = 0;
		int transparent = -1;
		int seen = 0;
		for (final String id : ids) {
			if (TRANSPARENT.equals(id)) {
				transparent = index;
			}
			index++;
			seen++;
		}
		if (seen == 0) {
			throw new IllegalStateException("no lift styles");
		}
		return transparent >= 0 ? transparent : 0;
	}

	/**
	 * Texture path to draw. A style that still points at the glass atlas but asks for opaque walls and opaque doors
	 * uses the builtin opaque atlas. Any other texture is kept, including one a pack painted itself, and a request
	 * that changes only walls or only doors stays on the given texture: that combination is {@link LiftGlassRegions},
	 * not a second saved atlas.
	 */
	public static String cabTexturePath(String textureResource, String walls, String doors) {
		if (GLASS_TEXTURE.equals(textureResource) && OPAQUE_PART.equals(walls) && OPAQUE_PART.equals(doors)) {
			return OPAQUE_TEXTURE;
		}
		return textureResource;
	}

	/**
	 * True for the only cab model. {@code RenderLifts} draws lift 1 for every id, including one this returns false for,
	 * so a style that names a model which does not exist yet still shows a cab. A later model branches on this.
	 */
	public static boolean usesLift1Model(String model) {
		return model == null || model.isEmpty() || MODEL_LIFT_1.equals(model);
	}
}
