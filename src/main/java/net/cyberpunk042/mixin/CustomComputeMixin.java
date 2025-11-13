package net.cyberpunk042.mixin;

import net.cyberpunk042.CustomCompute;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class CustomComputeMixin {
	@Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$customComputeHead(BooleanSupplier hasTime, CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer)(Object)this;
		CustomCompute.markInTickServerHook(true);
		// Augment path: run custom hook but let vanilla proceed
		try {
			CustomCompute.onServerTickHook(server);
			// Replace path: if returns true we take over and cancel vanilla tickServer
			if (CustomCompute.handleServerTickPossiblyOverride(server)) {
				ci.cancel();
			}
		} finally {
			CustomCompute.markInTickServerHook(false);
		}
	}
}


