package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$pauseWorld(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
		if (!Fastforwardengine.isPaused()) return;
		ServerLevel level = (ServerLevel)(Object)this;
		for (ServerPlayer sp : level.players()) {
			try {
				sp.tick();
			} catch (Throwable ignored) {}
		}
		ci.cancel();
	}
}


