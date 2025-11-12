package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

@Mixin(LevelTicks.class)
abstract class LevelTicksMixin<T> {

	@Unique
	private final Set<String> fastforwardengine$scheduledThisTick = new HashSet<>();

	@Inject(method = "tick(JILjava/util/function/BiConsumer;)V", at = @At("HEAD"))
	private void fastforwardengine$resetCoalescePerServerTick(long gameTime, int max, BiConsumer<BlockPos, T> runner, CallbackInfo ci) {
		// Bound memory and confine dedupe to the current server tick
		fastforwardengine$scheduledThisTick.clear();
	}

	@Inject(method = "schedule(Lnet/minecraft/world/ticks/ScheduledTick;)V", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$coalesceDuplicates(ScheduledTick<T> scheduledTick, CallbackInfo ci) {
		if (!(Fastforwardengine.isFastForwardRunning() || Fastforwardengine.CONFIG.redstoneExperimentalEnabled)) return;

		T type = scheduledTick.type();
		if (!(type instanceof Block block)) return; // only coalesce block ticks

		String blockId;
		try {
			var key = BuiltInRegistries.BLOCK.getKey(block);
			blockId = (key != null) ? key.toString() : String.valueOf(block.hashCode());
		} catch (Throwable t) {
			blockId = String.valueOf(block.hashCode());
		}

		long targetTick = scheduledTick.triggerTick();
		long posLong = scheduledTick.pos().asLong();
		// Include priority ordinal to avoid collapsing different priorities
		int priorityOrdinal;
		try {
			priorityOrdinal = scheduledTick.priority().ordinal();
		} catch (Throwable t) {
			priorityOrdinal = -1;
		}
		String key = blockId + "|" + posLong + "|" + targetTick + "|" + priorityOrdinal;

		if (fastforwardengine$scheduledThisTick.contains(key)) {
			ci.cancel();
			return;
		}
		fastforwardengine$scheduledThisTick.add(key);
	}
}


