package net.cyberpunk042.mixin;

import net.cyberpunk042.CustomCompute;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
abstract class LevelBlockEntitiesMixin {
	// Boost whitelisted BEs AFTER vanilla has ticked all block entities.
	@Inject(method = "tickBlockEntities", at = @At("TAIL"))
	private void fastforwardengine$boostWhitelistedBlockEntities(CallbackInfo ci) {
		if (!CustomCompute.isEnabled() || !CustomCompute.isWhitelistBlockEntities()) return;
		Level self = (Level)(Object)this;
		if (!(self instanceof ServerLevel sl)) return;
		CustomCompute.tickWhitelistedBlockEntities(sl);
	}
}


