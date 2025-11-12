package net.cyberpunk042.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public interface MinecraftServerInvoker {
	@Invoker("tickChildren")
	void fastforwardengine$invokeTickChildren(BooleanSupplier hasTime);

	@Invoker("tickServer")
	void fastforwardengine$invokeTickServer(BooleanSupplier hasTime);
}


