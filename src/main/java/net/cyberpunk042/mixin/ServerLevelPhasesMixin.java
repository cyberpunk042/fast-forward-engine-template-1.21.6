package net.cyberpunk042.mixin;

import net.cyberpunk042.CustomCompute;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mixin(ServerLevel.class)
abstract class ServerLevelPhasesMixin {
	// Skip scheduled block ticks when disabled
	@Redirect(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V", ordinal = 0)
	)
	private void fastforwardengine$maybeSkipBlockTicks(LevelTicks<Block> instance, long gameTime, int max, BiConsumer<BlockPos, Block> runner) {
		if (!CustomCompute.isEnabled() || CustomCompute.isAllowBlockTicks()) {
			instance.tick(gameTime, max, runner);
		}
	}

	// Skip scheduled fluid ticks when disabled
	@Redirect(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V", ordinal = 1)
	)
	private void fastforwardengine$maybeSkipFluidTicks(LevelTicks<?> instance, long gameTime, int max, BiConsumer<?, ?> runner) {
		if (!CustomCompute.isEnabled() || CustomCompute.isAllowFluidTicks()) {
			@SuppressWarnings({"rawtypes","unchecked"})
			LevelTicks it = (LevelTicks) instance;
			@SuppressWarnings({"rawtypes","unchecked"})
			BiConsumer r = (BiConsumer) runner;
			it.tick(gameTime, max, r);
		}
	}

	// Skip entity ticking phase when disabled (still allows entityManager maintenance later)
	@Redirect(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V")
	)
	private void fastforwardengine$maybeSkipEntityForEach(net.minecraft.world.level.entity.EntityTickList list, Consumer<net.minecraft.world.entity.Entity> consumer) {
		if (!CustomCompute.isEnabled() || CustomCompute.isAllowEntityTicks()) {
			list.forEach(consumer);
		}
	}

	// Skip raids.tick(this)
	@Redirect(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raids;tick(Lnet/minecraft/server/level/ServerLevel;)V")
	)
	private void fastforwardengine$maybeSkipRaids(net.minecraft.world.entity.raid.Raids raids, ServerLevel level) {
		if (!CustomCompute.isEnabled() || !CustomCompute.isSkipRaids()) {
			raids.tick(level);
		}
	}

	// Skip dragonFight.tick()
	@Redirect(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/end/EndDragonFight;tick()V")
	)
	private void fastforwardengine$maybeSkipDragonFight(net.minecraft.world.level.dimension.end.EndDragonFight fight) {
		if (!CustomCompute.isEnabled() || !CustomCompute.isSkipDragonFight()) {
			fight.tick();
		}
	}

	// Skip advanceWeatherCycle
	@Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$maybeSkipWeather(CallbackInfo ci) {
		if (CustomCompute.isEnabled() && CustomCompute.isSkipWeather()) {
			ci.cancel();
		}
	}

	// Skip thunder tick
	@Inject(method = "tickThunder", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$maybeSkipThunder(net.minecraft.world.level.chunk.LevelChunk chunk, CallbackInfo ci) {
		if (CustomCompute.isEnabled() && CustomCompute.isSkipThunder()) {
			ci.cancel();
		}
	}

	// Skip precipitation tick
	@Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$maybeSkipPrecipitation(net.minecraft.core.BlockPos pos, CallbackInfo ci) {
		if (CustomCompute.isEnabled() && CustomCompute.isSkipPrecipitation()) {
			ci.cancel();
		}
	}
}


