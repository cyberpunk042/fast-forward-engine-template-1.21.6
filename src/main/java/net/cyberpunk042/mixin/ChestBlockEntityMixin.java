package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomizableContainerBlockEntity.class)
abstract class ChestBlockEntityMixin {

	@Unique
	private int fastforwardengine$prevSetCount = -1;
	@Unique
	private int fastforwardengine$prevRemoveLimit = -1;

	@Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
	private void fastforwardengine$recordPrevCount(int slot, ItemStack stack, CallbackInfo ci) {
		if (!Fastforwardengine.isProfiling()) return;
		try {
			ItemStack current = ((RandomizableContainerBlockEntity)(Object)this).getItem(slot);
			fastforwardengine$prevSetCount = current == null ? 0 : current.getCount();
		} catch (Throwable ignored) {}
	}

	@Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
	private void fastforwardengine$countInserted(int slot, ItemStack stack, CallbackInfo ci) {
		if (!Fastforwardengine.isProfiling()) return;
		if (fastforwardengine$prevSetCount < 0) return;
		try {
			ItemStack now = ((RandomizableContainerBlockEntity)(Object)this).getItem(slot);
			int curr = now == null ? 0 : now.getCount();
			int delta = curr - fastforwardengine$prevSetCount;
			if (delta > 0) {
				Fastforwardengine.profileAddChestItemsInserted(delta);
				Fastforwardengine.profileAddHopperPushed(delta);
			}
		} catch (Throwable ignored) {
			// ignore
		} finally {
			fastforwardengine$prevSetCount = -1;
		}
	}

	// Count removals as "pulls" (best-effort; includes non-hopper sources)
	@Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
	private void fastforwardengine$recordRemoveLimit(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
		if (!Fastforwardengine.isProfiling()) return;
		fastforwardengine$prevRemoveLimit = amount;
	}

	@Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("TAIL"))
	private void fastforwardengine$countRemoved(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
		if (!Fastforwardengine.isProfiling()) return;
		try {
			int removed = 0;
			ItemStack ret = cir.getReturnValue();
			if (ret != null) removed = ret.getCount();
			if (removed <= 0) removed = Math.max(0, fastforwardengine$prevRemoveLimit);
			if (removed > 0) {
				Fastforwardengine.profileAddHopperPulled(removed);
			}
		} catch (Throwable ignored) {
		} finally {
			fastforwardengine$prevRemoveLimit = -1;
		}
	}
}


