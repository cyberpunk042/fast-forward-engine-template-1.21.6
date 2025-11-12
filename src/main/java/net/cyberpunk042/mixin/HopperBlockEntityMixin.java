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

import java.lang.reflect.Method;

@Mixin(HopperBlockEntity.class)
abstract class HopperBlockEntityMixin {

	@Shadow
	private static void pushItemsTick(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper) {}

	private static boolean fastforwardengine$reenterGuard = false;
	private static Method fastforwardengine$pull2 = null;
	private static Method fastforwardengine$pull4 = null;
	private static Method fastforwardengine$push4 = null;

	// Note: removed redirect-based neighbor container cache due to mapping variance across versions.

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
			// Resolve push/pull helpers lazily
			if (fastforwardengine$pull2 == null && fastforwardengine$pull4 == null && fastforwardengine$push4 == null) {
				try {
					for (Method m : HopperBlockEntity.class.getDeclaredMethods()) {
						String n = m.getName().toLowerCase();
						int pc = m.getParameterCount();
						var p = m.getParameterTypes();
						if (pc == 2 && (n.contains("suck") || n.contains("pull"))) {
							if (p.length == 2 && Level.class.isAssignableFrom(p[0]) && HopperBlockEntity.class.isAssignableFrom(p[1])) {
								m.setAccessible(true);
								fastforwardengine$pull2 = m;
								continue;
							}
						}
						if (pc == 4 && Level.class.isAssignableFrom(p[0]) && BlockPos.class.isAssignableFrom(p[1]) && BlockState.class.isAssignableFrom(p[2]) && HopperBlockEntity.class.isAssignableFrom(p[3])) {
							m.setAccessible(true);
							if (n.contains("suck") || n.contains("pull")) {
								fastforwardengine$pull4 = m;
							} else if (n.contains("move") || n.contains("eject") || n.contains("push") || n.contains("transfer")) {
								fastforwardengine$push4 = m;
							}
						}
					}
				} catch (Throwable ignored) {}
			}
			// Perform extra push then pull cycles to accelerate throughput to crafters
			for (int i = 0; i < extra; i++) {
				try {
					// Push out
					if (fastforwardengine$push4 != null) {
						try { fastforwardengine$push4.invoke(null, level, pos, state, hopper); } catch (Throwable ignored) {}
					} else {
						// Fallback: invoke vanilla push routine again
						try { pushItemsTick(level, pos, state, hopper); } catch (Throwable ignored) {}
					}
					// Pull in
					if (fastforwardengine$pull2 != null) {
						if (level instanceof ServerLevel sl) {
							try { fastforwardengine$pull2.invoke(null, sl, hopper); } catch (Throwable ignored) {}
						} else {
							try { fastforwardengine$pull2.invoke(null, level, hopper); } catch (Throwable ignored) {}
						}
					} else if (fastforwardengine$pull4 != null) {
						try { fastforwardengine$pull4.invoke(null, level, pos, state, hopper); } catch (Throwable ignored) {}
					}
				} catch (Throwable ignored) {}
			}
		} finally {
			fastforwardengine$reenterGuard = false;
		}
	}
}


