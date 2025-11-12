package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ComposterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(MinecartHopper.class)
abstract class MinecartHopperMixin {

	private static int fastforwardengine$prevTotalItems = -1;
	private static boolean fastforwardengine$reenterGuard = false;
	private static Method fastforwardengine$suck2 = null;

	@Inject(method = "tick", at = @At("HEAD"))
	private void fastforwardengine$profileMinecartHead(CallbackInfo ci) {
		if (!Fastforwardengine.isProfiling()) { fastforwardengine$prevTotalItems = -1; return; }
		try {
			MinecartHopper self = (MinecartHopper)(Object)this;
			int total = 0;
			int size = self.getContainerSize();
			for (int i = 0; i < size; i++) total += self.getItem(i).getCount();
			fastforwardengine$prevTotalItems = total;
		} catch (Throwable ignored) {
			fastforwardengine$prevTotalItems = -1;
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void fastforwardengine$profileMinecartTail(CallbackInfo ci) {
		if (Fastforwardengine.isProfiling() && fastforwardengine$prevTotalItems >= 0) {
			try {
				MinecartHopper self = (MinecartHopper)(Object)this;
				int total = 0;
				int size = self.getContainerSize();
				for (int i = 0; i < size; i++) total += self.getItem(i).getCount();
				int delta = total - fastforwardengine$prevTotalItems;
				if (delta > 0) {
					Fastforwardengine.profileAddHopperPulled(delta);
					// Attribute composter output when pulled from composter above rail
					try {
						MinecartHopper cart = (MinecartHopper)(Object)this;
						BlockPos bp = cart.blockPosition();
						BlockState s1 = ((ServerLevel)cart.level()).getBlockState(bp.above());
						BlockState s2 = ((ServerLevel)cart.level()).getBlockState(bp.above(2));
						if ((s1 != null && s1.getBlock() instanceof ComposterBlock) ||
							(s2 != null && s2.getBlock() instanceof ComposterBlock)) {
							Fastforwardengine.profileCountComposterOutput();
						}
					} catch (Throwable ignored) {}
				} else if (delta < 0) {
					int pushed = -delta;
					Fastforwardengine.profileAddHopperPushed(pushed);
					// Best-effort destination attribution: container directly below cart
					try {
						Level lvl = self.level();
						if (lvl instanceof ServerLevel sl) {
							BlockPos below = self.blockPosition().below();
							BlockEntity beBelow = sl.getBlockEntity(below);
							if (beBelow instanceof HopperBlockEntity) {
								// Attribute to the hopper's facing destination if it's a container
								try {
									BlockState belowState = sl.getBlockState(below);
									Direction facing = belowState.getValue(HopperBlock.FACING);
									BlockPos dstPos = below.relative(facing);
									BlockEntity dstBe = sl.getBlockEntity(dstPos);
									if (dstBe instanceof ShulkerBoxBlockEntity) {
										Fastforwardengine.profileAddShulkerItemsInserted(pushed);
									} else if (dstBe instanceof ChestBlockEntity || dstBe instanceof BarrelBlockEntity) {
										Fastforwardengine.profileAddChestItemsInserted(pushed);
									}
								} catch (Throwable ignored) {}
							} else if (beBelow instanceof ShulkerBoxBlockEntity) {
								Fastforwardengine.profileAddShulkerItemsInserted(pushed);
							} else if (beBelow instanceof ChestBlockEntity || beBelow instanceof BarrelBlockEntity) {
								Fastforwardengine.profileAddChestItemsInserted(pushed);
							}
						}
					} catch (Throwable ignored) {}
				}
			} catch (Throwable ignored) {
			} finally {
				fastforwardengine$prevTotalItems = -1;
			}
		}
		// Acceleration: perform extra suck cycles similar to hopper boost
		if (fastforwardengine$reenterGuard) return;
		if (Fastforwardengine.isPaused()) return;
		boolean active = Fastforwardengine.isFastForwardRunning() || Fastforwardengine.isHopperBoostAlwaysOn();
		if (!active) return;
		int extra = Fastforwardengine.hopperTransfersPerTick() - 1;
		if (extra <= 0) return;
		fastforwardengine$reenterGuard = true;
		try {
			// Resolve suck helper lazily from HopperBlockEntity: static boolean <suck...>(Level,Hopper)
			if (fastforwardengine$suck2 == null) {
				try {
					for (Method m : net.minecraft.world.level.block.entity.HopperBlockEntity.class.getDeclaredMethods()) {
						String n = m.getName().toLowerCase();
						Class<?>[] p = m.getParameterTypes();
						if (m.getParameterCount() == 2 &&
							(Level.class.isAssignableFrom(p[0]) || ServerLevel.class.isAssignableFrom(p[0])) &&
							Hopper.class.isAssignableFrom(p[1]) &&
							(n.contains("suck") || n.contains("pull"))) {
							m.setAccessible(true);
							fastforwardengine$suck2 = m;
							break;
						}
					}
				} catch (Throwable ignored) {}
			}
			if (fastforwardengine$suck2 != null) {
				Level lvl = ((MinecartHopper)(Object)this).level();
				Hopper self = (Hopper)(Object)this;
				for (int i = 0; i < extra; i++) {
					try { fastforwardengine$suck2.invoke(null, lvl, self); } catch (Throwable ignored) {}
				}
			}
		} finally {
			fastforwardengine$reenterGuard = false;
		}
	}
}


