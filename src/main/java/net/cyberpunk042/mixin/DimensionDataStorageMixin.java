package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DimensionDataStorage.class)
abstract class DimensionDataStorageMixin {

	// Avoid mid-warp scheduled saves of raids/poi/maps/etc.; final save will flush.
	@Inject(method = "scheduleSave", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$deferScheduledSaves(CallbackInfo ci) {
		if (Fastforwardengine.isFastForwardRunning()) {
			ci.cancel();
		}
	}
}


