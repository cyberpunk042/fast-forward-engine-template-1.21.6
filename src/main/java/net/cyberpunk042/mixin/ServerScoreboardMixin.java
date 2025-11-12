package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerScoreboard.class)
abstract class ServerScoreboardMixin {
	@Inject(method = "onObjectiveAdded(Lnet/minecraft/world/scores/Objective;)V", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$onObjectiveAdded(Objective objective, CallbackInfo ci) {
		if (Fastforwardengine.isFastForwardRunning()) ci.cancel();
	}

	@Inject(method = "onObjectiveChanged(Lnet/minecraft/world/scores/Objective;)V", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$onObjectiveChanged(Objective objective, CallbackInfo ci) {
		if (Fastforwardengine.isFastForwardRunning()) ci.cancel();
	}

	@Inject(method = "onObjectiveRemoved(Lnet/minecraft/world/scores/Objective;)V", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$onObjectiveRemoved(Objective objective, CallbackInfo ci) {
		if (Fastforwardengine.isFastForwardRunning()) ci.cancel();
	}
}


