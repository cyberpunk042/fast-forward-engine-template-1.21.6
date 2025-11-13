package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressEntityTicks(CallbackInfo ci) {
		// CustomCompute entity gating
		try {
			if (net.cyberpunk042.CustomCompute.isEnabled()) {
				var mode = net.cyberpunk042.CustomCompute.getEntitiesMode();
				if (mode == net.cyberpunk042.CustomCompute.EntitiesMode.PLAYERS_ONLY) {
					if (!(((Object)this) instanceof Player)) {
						ci.cancel();
						return;
					}
				} else if (mode == net.cyberpunk042.CustomCompute.EntitiesMode.ESSENTIAL) {
					Object self = this;
					boolean allow =
						(self instanceof Player) ||
						(self instanceof ItemEntity) ||
						(self instanceof ExperienceOrb) ||
						(self instanceof MinecartHopper);
					if (!allow) {
						ci.cancel();
						return;
					}
				}
			}
		} catch (Throwable ignored) {}
		// Hard pause: freeze everything except players
		if (Fastforwardengine.isPaused()) {
			if (!(((Object)this) instanceof Player)) {
				ci.cancel();
			}
			return;
		}
		// Fast-forward run: keep only essential entities ticking for farm fidelity
		if (Fastforwardengine.isFastForwardRunning()) {
			Object self = this;
			boolean playersTick = !Fastforwardengine.CONFIG.suppressPlayerTicksDuringWarp;
			boolean essential =
				(playersTick && (self instanceof Player)) ||
				(self instanceof ItemEntity) ||
				(self instanceof ExperienceOrb) ||
				(self instanceof MinecartHopper);
			if (!essential) {
				ci.cancel();
				return;
			}
		}
		// Redstone experimental: optionally skip entity ticks during extra passes
		if (Fastforwardengine.isRedstonePassActive() && Fastforwardengine.CONFIG.redstoneSkipEntityTicks) {
			// Always allow players and item entities to tick during redstone passes
			Object self = this;
			if (!((self instanceof Player) || (self instanceof net.minecraft.world.entity.item.ItemEntity))) {
				ci.cancel();
			}
		}
	}
}


