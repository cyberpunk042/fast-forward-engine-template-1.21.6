package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class ServerLevelItemSpawnMixin {

	@Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"))
	private void fastforwardengine$countCrafterSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!Fastforwardengine.isProfiling()) return;
		if (!(entity instanceof ItemEntity itemEntity)) return;
		ServerLevel self = (ServerLevel)(Object)this;
		try {
			BlockPos pos = BlockPos.containing(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
			// scan current cell and all 6 neighbors for a Crafter block
			if (isCrafterAround(self, pos)) {
				int count = itemEntity.getItem().getCount();
				if (count > 0) {
					Fastforwardengine.profileAddCrafted(count);
				}
			}
		} catch (Throwable ignored) {}
	}

	private static boolean isCrafterAround(ServerLevel level, BlockPos pos) {
		try {
			final BlockPos[] toCheck = new BlockPos[] {
				pos, pos.above(), pos.below(),
				pos.relative(Direction.NORTH),
				pos.relative(Direction.SOUTH),
				pos.relative(Direction.EAST),
				pos.relative(Direction.WEST)
			};
			for (BlockPos p : toCheck) {
				BlockState st = level.getBlockState(p);
				if (st.getBlock() instanceof CrafterBlock) return true;
			}
		} catch (Throwable ignored) {}
		return false;
	}
}


