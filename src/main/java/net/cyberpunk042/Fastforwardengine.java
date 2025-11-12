package net.cyberpunk042;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.cyberpunk042.mixin.MinecraftServerInvoker;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class Fastforwardengine implements ModInitializer {
	 public static final String MOD_ID = "fast-forward-engine";
	 public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);
	 public static Config CONFIG = new Config();

	 @Override
	 public void onInitialize() {
		 LOGGER.info("FastForward Engine initializing");
		 CONFIG = Config.loadOrCreate();

		 // Register /fastforward commands
		 CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			 LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fastforward")
				 .requires(src -> src.hasPermission(2) || src.getServer().isSingleplayer())
				 .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_000_000_000))
					 .executes(ctx -> {
						 int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
						 Engine.start(ctx.getSource(), ticks);
						 return 1;
					 }))
				 .then(Commands.literal("stop").executes(ctx -> {
					 Engine.stop(ctx.getSource().getServer(), ctx.getSource());
					 return 1;
				 }))
				 .then(Commands.literal("config").then(Commands.literal("reload").executes(ctx -> {
					 CONFIG = Config.loadOrCreate();
					 ctx.getSource().sendSuccess(() -> Component.literal("FastForward config reloaded"), false);
					 return 1;
				 })));
			 dispatcher.register(root);
		 });

		 // Drive extra server ticks on the main thread
		 ServerTickEvents.END_SERVER_TICK.register(Engine::onServerTick);
	 }

	 static final class Engine {
		 private static volatile boolean running = false;
		 private static volatile boolean warping = false;
		 private static long remainingTicks = 0L;
		 private static long totalTicks = 0L;
		 private static long startedAtMs = 0L;
		 private static CommandSourceStack replyTarget = null;
		 private static final Map<ServerLevel, Boolean> originalDoMobSpawning = new HashMap<>();
		 private static final Map<ServerLevel, Integer> originalRandomTick = new HashMap<>();
		 private static final BooleanSupplier ALWAYS_TRUE = () -> true;
		 @SuppressWarnings("unchecked")
		 private static final GameRules.Key<GameRules.IntegerValue> RANDOM_TICK_KEY = resolveRandomTickKey();

		 static void start(CommandSourceStack src, long ticks) {
			 final MinecraftServer server = src.getServer();
			 if (running) {
				 src.sendSuccess(() -> Component.literal("Fast-forward is already running."), false);
				 return;
			 }
			 src.sendSuccess(() -> Component.literal("Starting fast-forward of " + ticks + " ticks."), false);
			 server.execute(() -> {
				 synchronized (Engine.class) {
					 originalDoMobSpawning.clear();
					 originalRandomTick.clear();
					 for (ServerLevel level : server.getAllLevels()) {
						 setClearWeather(level);
						 GameRules.BooleanValue dm = level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
						 originalDoMobSpawning.put(level, dm.get());
						 dm.set(false, server);
						 if (CONFIG.randomTickSpeedOverride != null && RANDOM_TICK_KEY != null) {
							 try {
								 GameRules.IntegerValue rts = level.getGameRules().getRule(RANDOM_TICK_KEY);
								 originalRandomTick.put(level, rts.get());
								 rts.set(CONFIG.randomTickSpeedOverride, server);
							 } catch (Throwable ignored) {}
						 }
					 }
					 remainingTicks = ticks;
					 totalTicks = ticks;
					 startedAtMs = System.currentTimeMillis();
					 replyTarget = src;
					 running = true;
				 }
			 });
		 }

		 static void stop(MinecraftServer server, CommandSourceStack src) {
			 server.execute(() -> {
				 if (!running) {
					 src.sendSuccess(() -> Component.literal("No fast-forward is running."), false);
					 return;
				 }
				 long left = remainingTicks;
				 remainingTicks = 0;
				 src.sendSuccess(() -> Component.literal("Stopping fast-forward (remaining " + left + " ticks)."), false);
			 });
		 }

		 static void onServerTick(MinecraftServer server) {
			 if (!running || warping) return;
			 warping = true;
			 try {
				 long batch = Math.min(remainingTicks, Math.max(1L, CONFIG.batchSizePerServerTick));
				 MinecraftServerInvoker inv = (MinecraftServerInvoker) (Object) server;
				 for (long i = 0; i < batch && remainingTicks > 0; i++) {
					 try {
						 try {
							 inv.fastforwardengine$invokeTickServer(ALWAYS_TRUE);
						 } catch (Throwable ignored) {
							 inv.fastforwardengine$invokeTickChildren(ALWAYS_TRUE);
						 }
						 remainingTicks--;
					 } catch (Throwable t) {
						 net.cyberpunk042.Fastforwardengine.LOGGER.error("Fast-forward tick failed", t);
						 break;
					 }
				 }
			 } finally {
				 warping = false;
			 }
			 if (remainingTicks <= 0) {
				 long elapsed = System.currentTimeMillis() - startedAtMs;
				 for (ServerLevel level : server.getAllLevels()) {
					 try {
						 Boolean dm = originalDoMobSpawning.get(level);
						 if (dm != null) {
							 level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(dm, server);
						 }
						 if (RANDOM_TICK_KEY != null) {
							 Integer rt = originalRandomTick.get(level);
							 if (rt != null) {
								 try {
									 level.getGameRules().getRule(RANDOM_TICK_KEY).set(rt, server);
								 } catch (Throwable ignored) {}
							 }
						 }
					 } catch (Throwable ignored) {}
				 }
				 originalDoMobSpawning.clear();
				 originalRandomTick.clear();
				 try {
					 server.saveEverything(false, true, true);
				 } catch (Throwable t) {
					 net.cyberpunk042.Fastforwardengine.LOGGER.warn("Server save failed", t);
				 }
				 CommandSourceStack target = replyTarget;
				 replyTarget = null;
				 running = false;
				 if (target != null) {
					 long sim = totalTicks;
					 target.sendSuccess(() -> Component.literal("Simulated " + sim + " ticks in " + elapsed + " ms"), false);
				 } else {
					 net.cyberpunk042.Fastforwardengine.LOGGER.info("Simulated {} ticks in {} ms", totalTicks, elapsed);
				 }
			 }
		 }

		 private static void setClearWeather(ServerLevel level) {
			 try {
				 level.setWeatherParameters(0, 0, false, false);
			 } catch (Throwable ignored) {
				 Object data = level.getLevelData();
				 try { data.getClass().getMethod("setRaining", boolean.class).invoke(data, false); } catch (Throwable __) {}
				 try { data.getClass().getMethod("setThundering", boolean.class).invoke(data, false); } catch (Throwable __) {}
				 try { data.getClass().getMethod("setRainTime", int.class).invoke(data, 0); } catch (Throwable __) {}
				 try { data.getClass().getMethod("setThunderTime", int.class).invoke(data, 0); } catch (Throwable __) {}
			 }
		 }

		 @SuppressWarnings("unchecked")
		 private static GameRules.Key<GameRules.IntegerValue> resolveRandomTickKey() {
			 try {
				 var f = GameRules.class.getDeclaredField("RULE_RANDOM_TICK_SPEED");
				 f.setAccessible(true);
				 return (GameRules.Key<GameRules.IntegerValue>) f.get(null);
			 } catch (Throwable first) {
				 try {
					 var f2 = GameRules.class.getDeclaredField("RANDOM_TICK_SPEED");
					 f2.setAccessible(true);
					 return (GameRules.Key<GameRules.IntegerValue>) f2.get(null);
				 } catch (Throwable ignored) {
					 return null;
				 }
			 }
		 }
	 }
}


