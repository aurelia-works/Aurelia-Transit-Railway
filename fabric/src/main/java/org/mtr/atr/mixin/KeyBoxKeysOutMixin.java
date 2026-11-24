package org.mtr.atr.mixin;

import org.mtr.aurelia.keybox.KeysOut;
import org.mtr.core.data.Depot;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.World;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockDriverKeyDispenser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers when the keys a driver key box drops run out, so its hooks show which keys are out. Saved under new keys
 * ({@code atr_key_back_at_*}); a box from before has none and shows every key on its hook. MTR's own data is untouched.
 */
@Mixin(value = BlockDriverKeyDispenser.BlockEntity.class, remap = false)
public abstract class KeyBoxKeysOutMixin implements KeysOut {

	@Unique
	private static final String[] ATR$KEYS = {"atr_key_back_at_basic", "atr_key_back_at_advanced", "atr_key_back_at_guard"};
	@Unique
	private final long[] atr$backAt = new long[3];

	@Override
	public long atr$keyBackAt(int index) {
		return atr$backAt[index];
	}

	@Inject(method = "readCompoundTag", at = @At("TAIL"))
	private void atr$readKeysOut(CompoundTag compoundTag, CallbackInfo ci) {
		for (int i = 0; i < ATR$KEYS.length; i++) {
			atr$backAt[i] = compoundTag.getLong(ATR$KEYS[i]);
		}
	}

	@Inject(method = "writeCompoundTag", at = @At("HEAD"))
	private void atr$writeKeysOut(CompoundTag compoundTag, CallbackInfo ci) {
		for (int i = 0; i < ATR$KEYS.length; i++) {
			compoundTag.putLong(ATR$KEYS[i], atr$backAt[i]);
		}
	}

	@Inject(method = "spawnItemStack", at = @At("TAIL"))
	private void atr$keyTaken(World world, Depot depot, ItemStack itemStack, Direction direction, CallbackInfo ci) {
		final Object item = itemStack.getItem().data;
		final int index = item == Items.BASIC_DRIVER_KEY.get().data ? BASIC : item == Items.ADVANCED_DRIVER_KEY.get().data ? ADVANCED
				: item == Items.GUARD_KEY.get().data ? GUARD : -1;
		if (index >= 0) {
			final BlockDriverKeyDispenser.BlockEntity self = (BlockDriverKeyDispenser.BlockEntity) (Object) this;
			atr$backAt[index] = System.currentTimeMillis() + self.getTimeout();
			self.markDirty2();
		}
	}
}
