package net.cyberpunk042;

import com.mojang.brigadier.arguments.BoolArgumentType;
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
				 .then(Commands.literal("config")
					 .then(Commands.literal("reload").executes(ctx -> {
						 CONFIG = Config.loadOrCreate();
						 ctx.getSource().sendSuccess(() -> Component.literal("FastForward config reloaded"), false);
						 return 1;
					 }))
					 .then(Commands.literal("hopper")
						 .then(Commands.argument("rate", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
							 int rate = IntegerArgumentType.getInteger(ctx, "rate");
							 CONFIG.hopperTransfersPerTick = rate;
							 CONFIG.save();
							 ctx.getSource().sendSuccess(() -> Component.literal("hopperTransfersPerTick set to " + rate), false);
							 return 1;
						 }))
						 .then(Commands.literal("always")
							 .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
								 boolean value = BoolArgumentType.getBool(ctx, "value");
								 CONFIG.hopperAlwaysOn = value;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("hopperAlwaysOn set to " + value), false);
								 return 1;
							 }))
						 )
					 )
				 )
				 .then(Commands.literal("profile")
					 .then(Commands.literal("start").executes(ctx -> {
						 CommandSourceStack src = ctx.getSource();
						 MinecraftServer server = src.getServer();
						 server.execute(() -> {
							 if (Engine.profiling) {
								 src.sendSuccess(() -> Component.literal("Profiler already running."), false);
							 } else {
								 ServerLevel overworld = server.overworld();
								 long gt = 0L;
								 try {
									 gt = overworld.getLevelData().getGameTime();
								 } catch (Throwable ignored) {
									 // fallback if needed
								 }
								 Engine.profiling = true;
								 Engine.profileStartWallNs = System.nanoTime();
								 Engine.profileStartGameTime = gt;
								 src.sendSuccess(() -> Component.literal("Profiler started."), false);
							 }
						 });
						 return 1;
					 }))
					 .then(Commands.literal("stop").executes(ctx -> {
						 CommandSourceStack src = ctx.getSource();
						 MinecraftServer server = src.getServer();
						 server.execute(() -> {
							 if (!Engine.profiling) {
								 src.sendSuccess(() -> Component.literal("Profiler is not running."), false);
							 } else {
								 ServerLevel overworld = server.overworld();
								 long gtNow = 0L;
								 try {
									 gtNow = overworld.getLevelData().getGameTime();
								 } catch (Throwable ignored) {}
								 long wallNs = System.nanoTime() - Engine.profileStartWallNs;
								 long ticks = Math.max(0L, gtNow - Engine.profileStartGameTime);
								 double seconds = wallNs / 1_000_000_000.0;
								 double tps = seconds > 0 ? ticks / seconds : 0.0;
								 Engine.profiling = false;
								 src.sendSuccess(() -> Component.literal(
									 "Profile: " + ticks + " ticks in " + (long)(seconds * 1000) + " ms (" +
										 String.format(java.util.Locale.ROOT, "%.1f", tps) + " TPS), in-game +"
										 + ticks + " ticks (~" + (ticks / 20) + " s)"
								 ), false);
							 }
						 });
						 return 1;
					 }))
				 );
			 dispatcher.register(root);
		 });

		 // Drive extra server ticks on the main thread after vanilla tick completes
		 ServerTickEvents.END_SERVER_TICK.register(Engine::onServerTick);
	 }

	 // Public facade for mixins/utilities
	 public static boolean isFastForwardRunning() {
		 return Engine.isRunning();
	 }

	 public static int hopperTransfersPerTick() {
		 return Engine.getHopperTransfersPerTick();
	 }

	 public static boolean isHopperBoostAlwaysOn() {
		 return CONFIG.hopperAlwaysOn;
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
		 // Supplier semantics: returning false makes tickServer perform a single cycle and exit
		 private static final BooleanSupplier ONE_TICK_ONLY = () -> false;
		 @SuppressWarnings("unchecked")
		 private static final GameRules.Key<GameRules.IntegerValue> RANDOM_TICK_KEY = resolveRandomTickKey();

		 // profiler
		 static volatile boolean profiling = false;
		 static volatile long profileStartWallNs = 0L;
		 static volatile long profileStartGameTime = 0L;

		 static boolean isRunning() {
			 return running;
		 }

		 static int getHopperTransfersPerTick() {
			 return Math.max(1, Fastforwardengine.CONFIG.hopperTransfersPerTick);
		 }

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
							 // Prefer ticking worlds directly; this drives block entities (hoppers), redstone, fluids
							 inv.fastforwardengine$invokeTickChildren(ONE_TICK_ONLY);
						 } catch (Throwable ignored) {
							 // Fallback to full server tick if direct world ticking is unavailable
							 inv.fastforwardengine$invokeTickServer(ONE_TICK_ONLY);
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


