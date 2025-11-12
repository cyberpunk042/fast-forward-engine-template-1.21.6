package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(DispenserBlock.class)
abstract class DispenserBlockMixin {

	private boolean fastforwardengine$reenterGuard = false;
	private static Method fastforwardengine$dispenseFromReflect;

	@Inject(method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V", at = @At("TAIL"))
	private void fastforwardengine$extraShots(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (fastforwardengine$reenterGuard) return;
		if (Fastforwardengine.isPaused()) return;
		boolean active = Fastforwardengine.isFastForwardRunning() || Fastforwardengine.CONFIG.dropperAlwaysOn;
		if (!active) return;
		int extra = Math.max(1, Fastforwardengine.CONFIG.dropperShotsPerPulse) - 1;
		if (extra <= 0) return;
		// Find dispenseFrom or non-obf equivalent once
		if (fastforwardengine$dispenseFromReflect == null) {
			try {
				// Try common MCP-like name first
				fastforwardengine$dispenseFromReflect = DispenserBlock.class.getDeclaredMethod("dispenseFrom", ServerLevel.class, BlockState.class, BlockPos.class);
			} catch (Throwable ignored) {
				try {
					// Fallback: look for any declared method named "dispenseFrom" with 3 params
					for (Method m : DispenserBlock.class.getDeclaredMethods()) {
						if (m.getName().equals("dispenseFrom") && m.getParameterCount() == 3) {
							fastforwardengine$dispenseFromReflect = m;
							break;
						}
					}
				} catch (Throwable ignored2) {}
			}
			if (fastforwardengine$dispenseFromReflect != null) {
				fastforwardengine$dispenseFromReflect.setAccessible(true);
			}
		}
		if (fastforwardengine$dispenseFromReflect == null) return;

		fastforwardengine$reenterGuard = true;
		try {
			for (int i = 0; i < extra; i++) {
				try {
					fastforwardengine$dispenseFromReflect.invoke((DispenserBlock)(Object)this, level, state, pos);
				} catch (Throwable ignored) {
					break;
				}
			}
		} finally {
			fastforwardengine$reenterGuard = false;
		}
	}
}


