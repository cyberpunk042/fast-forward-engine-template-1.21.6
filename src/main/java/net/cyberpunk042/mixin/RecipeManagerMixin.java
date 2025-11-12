package net.cyberpunk042.mixin;

import net.cyberpunk042.Fastforwardengine;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {

	private static long fastforwardengine$recipeCacheGt = Long.MIN_VALUE;
	private static final Map<String, Optional<?>> fastforwardengine$recipeCache = new HashMap<>();

	private static String fastforwardengine$itemKey(ItemStack stack) {
		try {
			var item = stack.getItem();
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			return id != null ? id.toString() : String.valueOf(item.hashCode());
		} catch (Throwable t) {
			return "unknown";
		}
	}

	private static String fastforwardengine$levelKey(Level level) {
		try {
			return level.dimension().location().toString();
		} catch (Throwable t) {
			return "dim";
		}
	}

	private static String fastforwardengine$makeKey(RecipeType<?> type, RecipeInput input, Level level) {
		String itemKey = "none";
		try {
			int sz = input.size();
			if (sz > 0) {
				ItemStack in = input.getItem(0);
				if (in != null && !in.isEmpty()) itemKey = fastforwardengine$itemKey(in);
			}
		} catch (Throwable ignored) {}
		return fastforwardengine$levelKey(level) + "|" + String.valueOf(type) + "|" + itemKey;
	}

	private static boolean fastforwardengine$cachingActive() {
		return Fastforwardengine.isFastForwardRunning() || Fastforwardengine.isFurnaceBoostAlwaysOn();
	}

	@Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
	private void fastforwardengine$cacheHead3(RecipeType<?> type, RecipeInput input, Level level, CallbackInfoReturnable<Optional<?>> cir) {
		if (!fastforwardengine$cachingActive()) return;
		long gt;
		try { gt = level.getGameTime(); } catch (Throwable t) { gt = Long.MIN_VALUE; }
		if (gt != fastforwardengine$recipeCacheGt) {
			fastforwardengine$recipeCache.clear();
			fastforwardengine$recipeCacheGt = gt;
		}
		String key = fastforwardengine$makeKey(type, input, level);
		Optional<?> cached = fastforwardengine$recipeCache.get(key);
		if (cached != null) {
			cir.setReturnValue(cached);
			cir.cancel();
		}
	}

	@Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = false)
	private void fastforwardengine$cacheReturn3(RecipeType<?> type, RecipeInput input, Level level, CallbackInfoReturnable<Optional<?>> cir) {
		if (!fastforwardengine$cachingActive()) return;
		Optional<?> result = cir.getReturnValue();
		long gt;
		try { gt = level.getGameTime(); } catch (Throwable t) { gt = Long.MIN_VALUE; }
		if (gt != fastforwardengine$recipeCacheGt) {
			fastforwardengine$recipeCache.clear();
			fastforwardengine$recipeCacheGt = gt;
		}
		String key = fastforwardengine$makeKey(type, input, level);
		fastforwardengine$recipeCache.put(key, result);
	}
}


