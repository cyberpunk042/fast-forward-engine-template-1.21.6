package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class AbstractFurnaceBlockEntityMixin {

	private static java.lang.reflect.Method fastforwardengine$serverTickReflect;
	private static final java.util.WeakHashMap<AbstractFurnaceBlockEntity, Integer> fastforwardengine$lastOut = new java.util.WeakHashMap<>();
	private static void fastforwardengine$callServerTick(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity be) {
		try {
			if (fastforwardengine$serverTickReflect == null) {
				fastforwardengine$serverTickReflect = AbstractFurnaceBlockEntity.class.getDeclaredMethod("serverTick", Level.class, BlockPos.class, BlockState.class, AbstractFurnaceBlockEntity.class);
				fastforwardengine$serverTickReflect.setAccessible(true);
			}
			fastforwardengine$serverTickReflect.invoke(null, level, pos, state, be);
		} catch (Throwable ignored) {
			// give up silently if mappings differ
		}
	}

	private static boolean fastforwardengine$reenterGuard = false;

	@Inject(method = "serverTick", at = @At("TAIL"))
	private static void fastforwardengine$boostFurnace(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity be, CallbackInfo ci) {
		// Profiling: count smelt outputs via output slot delta (index 2)
		if (net.cyberpunk042.Fastforwardengine.isProfiling()) {
			try {
				int curr = be.getItem(2).getCount();
				Integer prev = fastforwardengine$lastOut.get(be);
				if (prev != null && curr > prev) {
					net.cyberpunk042.Fastforwardengine.profileAddFurnaceOutputs(curr - prev);
				}
				fastforwardengine$lastOut.put(be, curr);
			} catch (Throwable ignored) {}
		}
		if (fastforwardengine$reenterGuard) return;
		if (Fastforwardengine.isPaused()) return;
		boolean active = Fastforwardengine.isFastForwardRunning() || Fastforwardengine.isFurnaceBoostAlwaysOn();
		if (!active) return;
		int extra = Math.max(1, Fastforwardengine.furnaceTicksPerTick()) - 1;
		if (extra <= 0) return;
		fastforwardengine$reenterGuard = true;
		try {
			for (int i = 0; i < extra; i++) {
				fastforwardengine$callServerTick(level, pos, state, be);
			}
		} finally {
			fastforwardengine$reenterGuard = false;
		}
	}
}


