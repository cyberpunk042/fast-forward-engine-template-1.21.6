package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ComposterBlock.class)
abstract class ComposterBlockMixin {

	private boolean fastforwardengine$reenterGuard = false;
	private static java.lang.reflect.Method fastforwardengine$tickReflect;
	private static final java.util.Random fastforwardengine$seedRng = new java.util.Random();

	@Inject(method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V", at = @At("TAIL"))
	private void fastforwardengine$boostComposter(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (fastforwardengine$reenterGuard) return;
		if (Fastforwardengine.isPaused()) return;
		boolean active = Fastforwardengine.isFastForwardRunning() || Fastforwardengine.CONFIG.composterAlwaysOn;
		if (!active) return;
		int extra = Math.max(1, Fastforwardengine.CONFIG.composterTicksPerTick) - 1;
		if (extra <= 0) return;
		fastforwardengine$reenterGuard = true;
		try {
			// Resolve protected tick via reflection once
			if (fastforwardengine$tickReflect == null) {
				try {
					fastforwardengine$tickReflect = ComposterBlock.class.getDeclaredMethod(
						"tick", BlockState.class, ServerLevel.class, BlockPos.class, RandomSource.class
					);
					fastforwardengine$tickReflect.setAccessible(true);
				} catch (Throwable ignored) {
					// mapping mismatch; abort boost
					return;
				}
			}
			if (Fastforwardengine.CONFIG.experimentalBackgroundPrecompute) {
				// Precompute random seeds off-thread
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
						fastforwardengine$tickReflect.invoke((ComposterBlock)(Object)this, state, level, pos, r);
					} catch (Throwable ignored) {
						break;
					}
				}
			} else {
				for (int i = 0; i < extra; i++) {
					try {
						fastforwardengine$tickReflect.invoke((ComposterBlock)(Object)this, state, level, pos, random);
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


