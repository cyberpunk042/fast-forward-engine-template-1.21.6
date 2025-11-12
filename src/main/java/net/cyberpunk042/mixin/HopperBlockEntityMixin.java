package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
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
			for (int i = 0; i < extra; i++) {
				pushItemsTick(level, pos, state, hopper);
			}
		} finally {
			fastforwardengine$reenterGuard = false;
		}
	}
}


