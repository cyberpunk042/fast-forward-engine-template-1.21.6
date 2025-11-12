package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ComposterBlock.class)
abstract class ComposterBlockMixin {

	private boolean fastforwardengine$reenterGuard = false;
	private static java.lang.reflect.Method fastforwardengine$tickReflect;
	private static final java.util.Random fastforwardengine$seedRng = new java.util.Random();
	private static final java.util.HashMap<Long, Integer> fastforwardengine$lastLevels = new java.util.HashMap<>();
	@Unique private static final java.util.HashMap<Long, Integer> fastforwardengine$prevAddBefore = new java.util.HashMap<>();

	@Inject(method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V", at = @At("TAIL"))
	private void fastforwardengine$boostComposter(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		// Profiling: count when composter produces bone meal (level reaches 8)
		if (net.cyberpunk042.Fastforwardengine.isProfiling()) {
			try {
				BlockState after = level.getBlockState(pos);
				int curr = after.getValue(ComposterBlock.LEVEL);
				long key = pos.asLong();
				Integer prev = fastforwardengine$lastLevels.get(key);
				if (prev != null && prev < 8 && curr == 8) {
					net.cyberpunk042.Fastforwardengine.profileCountComposterOutput();
				}
				fastforwardengine$lastLevels.put(key, curr);
			} catch (Throwable ignored) {}
		}
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

	// Also track transitions to level 8 when items are inserted (addItem can bump level)
	// Overload: static BlockState addItem(Entity, BlockState, LevelAccessor, BlockPos, ItemStack)
	@Inject(method = "addItem(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), require = 0)
	private static void fastforwardengine$recordPreAdd(Entity entity, BlockState state, LevelAccessor level, BlockPos pos, ItemStack stack, CallbackInfoReturnable<BlockState> cir) {
		if (!net.cyberpunk042.Fastforwardengine.isProfiling()) return;
		try {
			int curr = level.getBlockState(pos).getValue(ComposterBlock.LEVEL);
			fastforwardengine$prevAddBefore.put(pos.asLong(), curr);
		} catch (Throwable ignored) {}
	}

	@Inject(method = "addItem(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("TAIL"), require = 0)
	private static void fastforwardengine$countOnAdd(Entity entity, BlockState state, LevelAccessor level, BlockPos pos, ItemStack stack, CallbackInfoReturnable<BlockState> cir) {
		if (!net.cyberpunk042.Fastforwardengine.isProfiling()) return;
		try {
			long key = pos.asLong();
			Integer prev = fastforwardengine$prevAddBefore.remove(key);
			if (prev != null) {
				BlockState newState = cir.getReturnValue();
				int curr = newState != null ? newState.getValue(ComposterBlock.LEVEL) : level.getBlockState(pos).getValue(ComposterBlock.LEVEL);
				if (prev < 8 && curr == 8) {
					net.cyberpunk042.Fastforwardengine.profileCountComposterOutput();
				}
			}
		} catch (Throwable ignored) {}
	}
}


