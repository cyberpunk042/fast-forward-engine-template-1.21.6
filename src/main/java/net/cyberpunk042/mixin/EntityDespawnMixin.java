package net.cyberpunk042.mixin;

import net.cyberpunk042.CustomCompute;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityDespawnMixin {
	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$skipDespawn(CallbackInfo ci) {
		if (CustomCompute.isEnabled() && CustomCompute.isSkipDespawnChecks()) {
			ci.cancel();
		}
	}
}


