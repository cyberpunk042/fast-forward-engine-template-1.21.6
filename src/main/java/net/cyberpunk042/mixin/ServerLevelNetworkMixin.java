package net.cyberpunk042.mixin;

import net.cyberpunk042.CustomCompute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class ServerLevelNetworkMixin {
	@Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressPlaySeededSoundEntity(Entity entity, double d, double e, double f, Holder<SoundEvent> holder, SoundSource source, float g, float h, long l, CallbackInfo ci) {
		if (CustomCompute.isEnabled() && (CustomCompute.isSuppressPackets() || CustomCompute.isSuppressSounds())) ci.cancel();
	}

	@Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressPlaySeededSoundEntity2(Entity from, Entity target, Holder<SoundEvent> holder, SoundSource source, float f, float g, long l, CallbackInfo ci) {
		if (CustomCompute.isEnabled() && (CustomCompute.isSuppressPackets() || CustomCompute.isSuppressSounds())) ci.cancel();
	}

	@Inject(method = "globalLevelEvent", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressGlobalLevelEvent(int i, BlockPos pos, int j, CallbackInfo ci) {
		if (CustomCompute.isEnabled() && CustomCompute.isSuppressPackets()) ci.cancel();
	}

	@Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressLevelEvent(Entity entity, int i, BlockPos pos, int j, CallbackInfo ci) {
		if (CustomCompute.isEnabled() && CustomCompute.isSuppressPackets()) ci.cancel();
	}

	@Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressSendParticles(ServerPlayer player, boolean bl, double d, double e, double f, Packet<?> packet, CallbackInfoReturnable<Boolean> cir) {
		if (CustomCompute.isEnabled() && (CustomCompute.isSuppressPackets() || CustomCompute.isSuppressParticles())) cir.setReturnValue(false);
	}

	@Inject(method = "destroyBlockProgress", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressDestroyBlockProgress(int i, BlockPos pos, int j, CallbackInfo ci) {
		if (CustomCompute.isEnabled() && CustomCompute.isSuppressPackets()) ci.cancel();
	}

	@Inject(method = "broadcastEntityEvent", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressBroadcastEntityEvent(Entity entity, byte b, CallbackInfo ci) {
		if (CustomCompute.isEnabled() && CustomCompute.isSuppressPackets()) ci.cancel();
	}

	@Inject(method = "broadcastDamageEvent", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$suppressBroadcastDamageEvent(Entity entity, net.minecraft.world.damagesource.DamageSource damageSource, CallbackInfo ci) {
		if (CustomCompute.isEnabled() && CustomCompute.isSuppressPackets()) ci.cancel();
	}
}


