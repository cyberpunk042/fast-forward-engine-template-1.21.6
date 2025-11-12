package net.cyberpunk042.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public interface ServerLevelInvoker {
	@Invoker("tick")
	void fastforwardengine$invokeTick(BooleanSupplier hasTime);
}


