package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
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
	private static int fastforwardengine$prevTotalItems = -1;

	// Note: removed redirect-based neighbor container cache due to mapping variance across versions.

	@Inject(method = "pushItemsTick", at = @At("HEAD"))
	private static void fastforwardengine$profileHopperHead(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper, CallbackInfo ci) {
		if (!net.cyberpunk042.Fastforwardengine.isProfiling()) { fastforwardengine$prevTotalItems = -1; return; }
		try {
			int total = 0;
			int size = hopper.getContainerSize();
			for (int i = 0; i < size; i++) {
				total += hopper.getItem(i).getCount();
			}
			fastforwardengine$prevTotalItems = total;
		} catch (Throwable ignored) {
			fastforwardengine$prevTotalItems = -1;
		}
	}

	@Inject(method = "pushItemsTick", at = @At("TAIL"))
	private static void fastforwardengine$boostTransfers(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper, CallbackInfo ci) {
		// Profiling: infer pushes/pulls by total item count delta in hopper
		if (net.cyberpunk042.Fastforwardengine.isProfiling() && fastforwardengine$prevTotalItems >= 0) {
			try {
				int total = 0;
				int size = hopper.getContainerSize();
				for (int i = 0; i < size; i++) {
					total += hopper.getItem(i).getCount();
				}
				int delta = total - fastforwardengine$prevTotalItems;
				if (delta > 0) {
					net.cyberpunk042.Fastforwardengine.profileAddHopperPulled(delta);
					// Attribute composter output when pulled directly from above
					try {
						BlockPos srcPos = pos.above();
						BlockState srcState = level.getBlockState(srcPos);
						if (srcState.getBlock() instanceof net.minecraft.world.level.block.ComposterBlock) {
							net.cyberpunk042.Fastforwardengine.profileCountComposterOutput();
						}
					} catch (Throwable ignored) {}
				} else if (delta < 0) {
					int pushed = -delta;
					net.cyberpunk042.Fastforwardengine.profileAddHopperPushed(pushed);
					// Attribute pushes to chest/barrel destination based on hopper facing
					try {
						Direction facing = state.getValue(HopperBlock.FACING);
						BlockPos dst = pos.relative(facing);
						BlockEntity be = level.getBlockEntity(dst);
						if (be instanceof ShulkerBoxBlockEntity) {
							net.cyberpunk042.Fastforwardengine.profileAddShulkerItemsInserted(pushed);
						} else if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
							net.cyberpunk042.Fastforwardengine.profileAddChestItemsInserted(pushed);
						}
					} catch (Throwable ignored) {}
				}
			} catch (Throwable ignored) {
			} finally {
				fastforwardengine$prevTotalItems = -1;
			}
		}
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


