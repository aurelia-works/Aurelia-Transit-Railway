package org.mtr.atr.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.mtr.aurelia.client.screen.TrainPickerScreen;
import org.mtr.aurelia.client.ui.AureliaUiSettings;
import org.mtr.mod.screen.VehicleSelectorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * When the siding screen opens MTR's train selector, ATR's train picker opens in its place while the "Train picker"
 * Look switch is on. Both edit the same siding, so turning the switch off brings MTR's screen back unchanged.
 */
@Mixin(MinecraftClient.class)
public abstract class TrainPickerOpenMixin {

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen atr$openTrainPicker(Screen screen) {
		if (screen instanceof VehicleSelectorScreen && AureliaUiSettings.enabled(AureliaUiSettings.ScreenId.TRAIN_PICKER)) {
			return new TrainPickerScreen(((VehicleSelectorScreenAccessor) screen).atr$siding(), ((MinecraftClient) (Object) this).currentScreen);
		}
		return screen;
	}
}
