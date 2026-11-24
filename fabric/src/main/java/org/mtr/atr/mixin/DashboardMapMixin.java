package org.mtr.atr.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import org.mtr.aurelia.client.AureliaClient;
import org.mtr.aurelia.client.map.MapPinLayer;
import org.mtr.aurelia.client.map.PinMenu;
import org.mtr.aurelia.dashboard.DashboardLayout;
import org.mtr.aurelia.dashboard.DashboardMapLayers;
import org.mtr.aurelia.dashboard.DashboardMapState;
import org.mtr.aurelia.dashboard.WidgetMapCameraAccess;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.ClickableWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.screen.WidgetMap;
import org.mtr.mod.screen.WorldMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Exposes {@link WidgetMap}'s center/zoom for unified dashboard mode switching and shifts top-right cursor coordinates
 * below the unified mode switcher bar so nothing overlaps at {@code 854x480} or {@code 1080p}.
 * <p>
 * The ground under MTR's overlays is ATR's explored map of the whole world ({@link DashboardMapLayers}). MTR's
 * {@link WorldMap}, which only pictures the loaded chunks by reading them block by block on the render thread, now
 * runs only for the "current Y" view, the one thing the explored map does not show.
 */
@Mixin(value = WidgetMap.class, remap = false)
public abstract class DashboardMapMixin extends ClickableWidgetExtension implements WidgetMapCameraAccess {

	@Shadow
	private double scale;

	@Shadow
	private double centerX;

	@Shadow
	private double centerY;

	@Shadow
	private boolean showStations;

	@Unique
	private final PinMenu atr$pinMenu = new PinMenu();

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

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/screen/WorldMap;forEachTile(Ljava/util/function/Consumer;)V"))
	private void atr$drawExploredGround(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		final DrawContext context = ((GraphicsHolderDrawContext) (Object) graphicsHolder).atr$getDrawContext();
		DashboardMapLayers.ground(context, this.getX2(), this.getY2(), this.width, this.height, this.centerX, this.centerY, this.scale);
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/screen/WorldMap;tick(Lorg/mtr/mapping/holder/World;Lorg/mtr/mapping/holder/ClientPlayerEntity;F)V"))
	private void atr$scanOnlyForCurrentY(WorldMap map, World world, ClientPlayerEntity player, float delta) {
		if (map.isMapOverlayMode(WorldMap.MapOverlayMode.CURRENT_Y)) {
			map.tick(world, player, delta);
		}
	}

	@Redirect(method = "setMapOverlayMode", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/screen/WorldMap;updateMap(Lorg/mtr/mapping/holder/World;Lorg/mtr/mapping/holder/ClientPlayerEntity;)V"))
	private void atr$rescanOnlyForCurrentY(WorldMap map, World world, ClientPlayerEntity player) {
		if (map.isMapOverlayMode(WorldMap.MapOverlayMode.CURRENT_Y)) {
			map.updateMap(world, player);
		} else {
			map.disposeImages();
		}
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/screen/WidgetMap;drawMousePositionText(Lorg/mtr/mapping/mapper/GraphicsHolder;Lorg/mtr/libraries/it/unimi/dsi/fastutil/doubles/DoubleDoubleImmutablePair;)V"))
	private void atr$drawFarStationsAndPins(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		final DrawContext context = ((GraphicsHolderDrawContext) (Object) graphicsHolder).atr$getDrawContext();
		if (this.showStations) {
			DashboardMapLayers.farStations(context, this.getX2(), this.getY2(), this.width, this.height, this.centerX, this.centerY, this.scale);
		}
		DashboardMapLayers.pins(context, this.getX2(), this.getY2(), this.width, this.height, this.centerX, this.centerY, this.scale);
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void atr$drawPinMenu(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (this.atr$pinMenu.isOpen()) {
			final DrawContext context = ((GraphicsHolderDrawContext) (Object) graphicsHolder).atr$getDrawContext();
			final MinecraftClient client = MinecraftClient.getInstance();
			this.atr$pinMenu.render(context, mouseX, mouseY, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
		}
	}

	/** Right-click opens the home and landmark menu; while it is open, a click goes to it and nowhere else. */
	@Inject(method = "mouseClicked2", at = @At("HEAD"), cancellable = true)
	private void atr$pinMenuClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (this.atr$pinMenu.click(mouseX, mouseY)) {
			cir.setReturnValue(true);
		} else if (button == 1 && this.isMouseOver2(mouseX, mouseY)) {
			final int worldX = (int) Math.floor((mouseX - this.getX2() - this.width / 2.0) / this.scale + this.centerX);
			final int worldZ = (int) Math.floor((mouseY - this.getY2() - this.height / 2.0) / this.scale + this.centerY);
			this.atr$pinMenu.open((int) mouseX, (int) mouseY, worldX, worldZ, MapPinLayer.hit(mouseX, mouseY, this.getX2(), this.getY2(), this.width, this.height, this.centerX, this.centerY, this.scale));
			cir.setReturnValue(true);
		}
	}
}
