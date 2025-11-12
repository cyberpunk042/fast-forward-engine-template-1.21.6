package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DropperBlock.class)
abstract class DropperBlockMixin {

	// Count drops on actual dispense call to avoid mapping variance on tick()
	@Inject(method = "dispenseFrom(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", at = @At("TAIL"), require = 0)
	private void fastforwardengine$countDrop(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
		if (Fastforwardengine.isProfiling()) {
			Fastforwardengine.profileIncDropperDrops();
		}
	}
}


