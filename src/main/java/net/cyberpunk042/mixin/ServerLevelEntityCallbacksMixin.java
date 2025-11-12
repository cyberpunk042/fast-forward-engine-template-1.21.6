package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
abstract class ServerLevelEntityCallbacksMixin {

	@Inject(method = "onCreated", at = @At("HEAD"))
	private void fastforwardengine$profileEntityCreated(Entity entity, CallbackInfo ci) {
		if (Fastforwardengine.isProfiling()) {
			Fastforwardengine.profileCountEntityCreated();
		}
	}
}


