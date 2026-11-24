package org.mtr.atr.mixin;

import net.minecraft.client.MinecraftClient;
import org.mtr.aurelia.client.screen.KeyBoxScreen;
import org.mtr.aurelia.client.ui.AureliaUiSettings;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.block.BlockDriverKeyDispenser;
import org.mtr.mod.packet.ClientPacketHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right-clicking a driver key dispenser opens ATR's key box instead of MTR's checkbox screen while the key box Look
 * switch is on. With it off, MTR's screen opens as before. Both save the same values.
 */
@Mixin(value = ClientPacketHelper.class, remap = false)
public abstract class KeyBoxOpenMixin {

	@Inject(method = "openBlockEntityScreen", at = @At("HEAD"), cancellable = true)
	private static void atr$openKeyBox(BlockPos blockPos, CallbackInfo ci) {
		if (!AureliaUiSettings.enabled(AureliaUiSettings.ScreenId.KEY_BOX)) {
			return;
		}
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null && client.world.getBlockEntity(blockPos.data) instanceof BlockDriverKeyDispenser.BlockEntity blockEntity) {
			if (!(client.currentScreen instanceof KeyBoxScreen)) {
				client.setScreen(new KeyBoxScreen(blockPos, blockEntity));
			}
			ci.cancel();
		}
	}
}
