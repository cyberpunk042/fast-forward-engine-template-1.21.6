package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerAdvancements.class)
abstract class PlayerAdvancementsMixin {
	@Inject(method = "flushDirty", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressAdvancementFlush(ServerPlayer player, boolean fullSync, CallbackInfo ci) {
		if (Fastforwardengine.isFastForwardRunning()) {
			ci.cancel();
		}
	}
}


