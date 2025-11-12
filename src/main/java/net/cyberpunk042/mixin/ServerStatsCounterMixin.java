package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatsCounter.class)
abstract class ServerStatsCounterMixin {
	@Inject(method = "sendStats", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressStatSync(ServerPlayer player, CallbackInfo ci) {
		if (Fastforwardengine.isFastForwardRunning()) {
			ci.cancel();
		}
	}
}


