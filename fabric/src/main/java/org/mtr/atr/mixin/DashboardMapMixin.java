package org.mtr.atr.mixin;

import org.mtr.aurelia.client.AureliaClient;
import org.mtr.aurelia.dashboard.DashboardLayout;
import org.mtr.aurelia.dashboard.DashboardMapState;
import org.mtr.aurelia.dashboard.WidgetMapCameraAccess;
import org.mtr.mapping.mapper.ClickableWidgetExtension;
import org.mtr.mod.screen.WidgetMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Exposes {@link WidgetMap}'s center/zoom for unified dashboard mode switching and shifts top-right cursor coordinates
 * below the unified mode switcher bar so nothing overlaps at {@code 854x480} or {@code 1080p}.
 */
@Mixin(value = WidgetMap.class, remap = false)
public abstract class DashboardMapMixin extends ClickableWidgetExtension implements WidgetMapCameraAccess {

	@Shadow
	private double scale;

	@Shadow
	private double centerX;

	@Shadow
	private double centerY;

	protected DashboardMapMixin(int x, int y, int width, int height) {
		super(x, y, width, height);
	}

	@Override
	public double atr$centerX() {
		return this.centerX;
	}

	@Override
	public double atr$centerZ() {
		return this.centerY;
	}

	@Override
	public double atr$scale() {
		return this.scale;
	}

	@Override
	public void atr$setView(double nextCenterX, double nextCenterZ, double nextScale) {
		final DashboardMapState clamped = new DashboardMapState(nextCenterX, nextCenterZ, nextScale, false);
		this.centerX = clamped.centerX();
		this.centerY = clamped.centerZ();
		this.scale = clamped.scale();
	}

	@ModifyArg(
			method = "render",
			at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/mapper/GraphicsHolder;drawText(Lorg/mtr/mapping/holder/MutableText;IIIZI)V"),
			index = 2
	)
	private int atr$offsetEditHintBelowButtons(int y) {
		return this.getY2() + DashboardLayout.editHintY(AureliaClient.isMagicLoaded());
	}

	@ModifyArg(
			method = "drawMousePositionText",
			at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/mapper/GraphicsHolder;drawText(Ljava/lang/String;IIIZI)V"),
			index = 2
	)
	private int atr$offsetMousePositionBelowBar(int y) {
		return this.getY2() + DashboardLayout.MOUSE_COORDS_Y;
	}
}
