package net.cyberpunk042.mixin;

import net.cyberpunk042.CustomCompute;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
abstract class MobMixin {
	@Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$maybeSkipAI(CallbackInfo ci) {
		if (!CustomCompute.isEnabled()) return;
		if (CustomCompute.isDisableMobAI()) {
			ci.cancel();
		}
	}
}


