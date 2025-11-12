package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressDuringRedstone(CallbackInfo ci) {
		if (Fastforwardengine.isRedstonePassActive() && Fastforwardengine.CONFIG.redstoneSkipEntityTicks) {
			ci.cancel();
		}
	}
}


