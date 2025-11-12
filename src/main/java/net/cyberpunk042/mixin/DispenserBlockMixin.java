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
import net.minecraft.util.RandomSource;

@Mixin(DispenserBlock.class)
abstract class DispenserBlockMixin {

	private boolean fastforwardengine$reenterGuard = false;
	private static Method fastforwardengine$dispenseFromReflect;
	private static Method fastforwardengine$tickReflect;
	private static final java.util.Random fastforwardengine$seedRng = new java.util.Random();

	@Inject(method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V", at = @At("TAIL"))
	private void fastforwardengine$extraShots(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (fastforwardengine$reenterGuard) return;
		if (Fastforwardengine.isPaused()) return;
		boolean active = Fastforwardengine.isFastForwardRunning() || Fastforwardengine.CONFIG.dropperAlwaysOn;
		if (!active) return;
		int extra = Math.max(1, Fastforwardengine.CONFIG.dropperShotsPerPulse) - 1;
		if (extra <= 0) return;

		fastforwardengine$reenterGuard = true;
		try {
			boolean bg = Fastforwardengine.CONFIG.experimentalBackgroundPrecompute;
			if (bg) {
				// reflect tick method to reuse scheduling semantics with custom RandomSource
				if (fastforwardengine$tickReflect == null) {
					try {
						fastforwardengine$tickReflect = DispenserBlock.class.getDeclaredMethod(
							"tick", BlockState.class, ServerLevel.class, BlockPos.class, RandomSource.class);
						fastforwardengine$tickReflect.setAccessible(true);
					} catch (Throwable ignored) {}
				}
				if (fastforwardengine$tickReflect != null) {
					long[] seeds = null;
					try {
						var exec = net.cyberpunk042.Fastforwardengine.precomputeExecutor();
						java.util.concurrent.Future<long[]> fut = exec.submit(() -> {
							long[] arr = new long[extra];
							for (int i = 0; i < extra; i++) arr[i] = fastforwardengine$seedRng.nextLong();
							return arr;
						});
						seeds = fut.get();
					} catch (Throwable ignored) {}
					for (int i = 0; i < extra; i++) {
						try {
							RandomSource r = (seeds != null) ? RandomSource.create(seeds[i]) : random;
							fastforwardengine$tickReflect.invoke((DispenserBlock)(Object)this, state, level, pos, r);
						} catch (Throwable ignored) {
							break;
						}
					}
				}
			} else {
				// fallback to direct dispenseFrom if available
				if (fastforwardengine$dispenseFromReflect == null) {
					try {
						fastforwardengine$dispenseFromReflect = DispenserBlock.class.getDeclaredMethod("dispenseFrom", ServerLevel.class, BlockState.class, BlockPos.class);
						fastforwardengine$dispenseFromReflect.setAccessible(true);
					} catch (Throwable ignored) {}
				}
				if (fastforwardengine$dispenseFromReflect == null) return;
				for (int i = 0; i < extra; i++) {
					try {
						fastforwardengine$dispenseFromReflect.invoke((DispenserBlock)(Object)this, level, state, pos);
					} catch (Throwable ignored) {
						break;
					}
				}
			}
		} finally {
			fastforwardengine$reenterGuard = false;
		}
	}
}


