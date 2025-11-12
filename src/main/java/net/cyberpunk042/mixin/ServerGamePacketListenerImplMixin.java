package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {

	// Cancel server-enforced teleport back during speed checks
	@Redirect(method = {
		"handleMovePlayer",
		"handleMoveVehicle"
	}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"), require = 0)
	private void fastforwardengine$skipAntiCheatTeleport(ServerGamePacketListenerImpl self, double x, double y, double z, float yaw, float pitch) {
		if (!Fastforwardengine.CONFIG.disablePlayerMoveSpeedChecks) {
			self.teleport(x, y, z, yaw, pitch);
		}
	}
}


