package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HopperBlockEntity.class)
abstract class HopperBlockEntityMixin {

	@Shadow
	private static void pushItemsTick(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper) {}

	private static boolean fastforwardengine$reenterGuard = false;
	private static java.lang.reflect.Method fastforwardengine$suck2 = null;
	private static java.lang.reflect.Method fastforwardengine$suck4 = null;

	@Inject(method = "pushItemsTick", at = @At("TAIL"))
	private static void fastforwardengine$boostTransfers(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper, CallbackInfo ci) {
		if (fastforwardengine$reenterGuard) return;
		if (Fastforwardengine.isPaused()) return;
		boolean active = Fastforwardengine.isFastForwardRunning() || Fastforwardengine.isHopperBoostAlwaysOn();
		if (!active) return;
		int extra = Fastforwardengine.hopperTransfersPerTick() - 1;
		if (extra <= 0) return;
		fastforwardengine$reenterGuard = true;
		try {
			// Resolve suck methods lazily
			if (fastforwardengine$suck2 == null && fastforwardengine$suck4 == null) {
				try {
					for (var m : HopperBlockEntity.class.getDeclaredMethods()) {
						String n = m.getName().toLowerCase();
						int pc = m.getParameterCount();
						if (!(n.contains("suck") || n.contains("move"))) continue;
						if (pc == 2) {
							var p = m.getParameterTypes();
							if (HopperBlockEntity.class.isAssignableFrom(p[1])) {
								m.setAccessible(true);
								fastforwardengine$suck2 = m;
							}
						} else if (pc == 4) {
							m.setAccessible(true);
							fastforwardengine$suck4 = m;
						}
					}
				} catch (Throwable ignored) {}
			}
			// Perform pull-only extras to preserve vertical-down routing; pushing will occur on vanilla path next tick
			for (int i = 0; i < extra; i++) {
				try {
					if (fastforwardengine$suck2 != null) {
						if (level instanceof ServerLevel sl) {
							try { fastforwardengine$suck2.invoke(null, sl, hopper); } catch (Throwable ignored) {}
						} else {
							try { fastforwardengine$suck2.invoke(null, level, hopper); } catch (Throwable ignored) {}
						}
					} else if (fastforwardengine$suck4 != null) {
						try { fastforwardengine$suck4.invoke(null, level, pos, state, hopper); } catch (Throwable ignored) {}
					}
				} catch (Throwable ignored) {}
			}
		} finally {
			fastforwardengine$reenterGuard = false;
		}
	}
}


