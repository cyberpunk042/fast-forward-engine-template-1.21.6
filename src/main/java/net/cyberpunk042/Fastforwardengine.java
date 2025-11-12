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
					 .then(Commands.literal("warp")
						 .then(Commands.literal("aggressive")
							 .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
								 boolean value = BoolArgumentType.getBool(ctx, "value");
								 CONFIG.experimentalAggressiveWarp = value;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("experimentalAggressiveWarp set to " + value), false);
								 return 1;
							 }))
						 )
						 .then(Commands.literal("budgetMs")
							 .then(Commands.argument("ms", IntegerArgumentType.integer(10, 10_000)).executes(ctx -> {
								 int ms = IntegerArgumentType.getInteger(ctx, "ms");
								 CONFIG.experimentalMaxWarpMillisPerServerTick = ms;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("experimentalMaxWarpMillisPerServerTick set to " + ms), false);
								 return 1;
							 }))
						 )
					 )
					 .then(Commands.literal("furnace")
						 .then(Commands.argument("rate", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
							 int rate = IntegerArgumentType.getInteger(ctx, "rate");
							 CONFIG.furnaceTicksPerTick = rate;
							 CONFIG.save();
							 ctx.getSource().sendSuccess(() -> Component.literal("furnaceTicksPerTick set to " + rate), false);
							 return 1;
						 }))
						 .then(Commands.literal("always")
							 .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
								 boolean value = BoolArgumentType.getBool(ctx, "value");
								 CONFIG.furnaceAlwaysOn = value;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("furnaceAlwaysOn set to " + value), false);
								 return 1;
							 }))
						 )
					 )
					 .then(Commands.literal("redstone")
						 .then(Commands.literal("enabled")
							 .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
								 boolean value = BoolArgumentType.getBool(ctx, "value");
								 CONFIG.redstoneExperimentalEnabled = value;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("redstoneExperimentalEnabled set to " + value), false);
								 return 1;
							 }))
						 )
						 .then(Commands.literal("passes")
							 .then(Commands.argument("count", IntegerArgumentType.integer(1, 16)).executes(ctx -> {
								 int count = IntegerArgumentType.getInteger(ctx, "count");
								 CONFIG.redstonePassesPerServerTick = count;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("redstonePassesPerServerTick set to " + count), false);
								 return 1;
							 }))
						 )
						 .then(Commands.literal("always")
							 .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
								 boolean value = BoolArgumentType.getBool(ctx, "value");
								 CONFIG.redstoneAlwaysOn = value;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("redstoneAlwaysOn set to " + value), false);
								 return 1;
							 }))
						 )
						 .then(Commands.literal("skipEntities")
							 .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
								 boolean value = BoolArgumentType.getBool(ctx, "value");
								 CONFIG.redstoneSkipEntityTicks = value;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("redstoneSkipEntityTicks set to " + value), false);
								 return 1;
							 }))
						 )
					 )
					 .then(Commands.literal("composter")
						 .then(Commands.argument("rate", IntegerArgumentType.integer(1, 32)).executes(ctx -> {
							 int rate = IntegerArgumentType.getInteger(ctx, "rate");
							 CONFIG.composterTicksPerTick = rate;
							 CONFIG.save();
							 ctx.getSource().sendSuccess(() -> Component.literal("composterTicksPerTick set to " + rate), false);
							 return 1;
						 }))
						 .then(Commands.literal("always")
							 .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
								 boolean value = BoolArgumentType.getBool(ctx, "value");
								 CONFIG.composterAlwaysOn = value;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("composterAlwaysOn set to " + value), false);
								 return 1;
							 }))
						 )
					 )
					 .then(Commands.literal("dropper")
						 .then(Commands.argument("shots", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
							 int shots = IntegerArgumentType.getInteger(ctx, "shots");
							 CONFIG.dropperShotsPerPulse = shots;
							 CONFIG.save();
							 ctx.getSource().sendSuccess(() -> Component.literal("dropperShotsPerPulse set to " + shots), false);
							 return 1;
						 }))
						 .then(Commands.literal("always")
							 .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
								 boolean value = BoolArgumentType.getBool(ctx, "value");
								 CONFIG.dropperAlwaysOn = value;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("dropperAlwaysOn set to " + value), false);
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
			 // Pause/resume time controls
			 dispatcher.register(Commands.literal("fastpause")
				 .requires(src -> src.hasPermission(2) || src.getServer().isSingleplayer())
				 .then(Commands.literal("on").executes(ctx -> {
					 ctx.getSource().sendSuccess(() -> Component.literal("World pause enabled"), false);
					 Fastforwardengine.setPaused(true);
					 return 1;
				 }))
				 .then(Commands.literal("off").executes(ctx -> {
					 Fastforwardengine.setPaused(false);
					 ctx.getSource().sendSuccess(() -> Component.literal("World pause disabled"), false);
					 return 1;
				 }))
				 .then(Commands.literal("status").executes(ctx -> {
					 boolean p = Fastforwardengine.isPaused();
					 ctx.getSource().sendSuccess(() -> Component.literal("World pause: " + (p ? "ON" : "OFF")), false);
					 return 1;
				 }))
			 );
			 dispatcher.register(root);
		 });

		 // Drive extra server ticks on the main thread after vanilla tick completes
		 ServerTickEvents.END_SERVER_TICK.register(Engine::onServerTick);

		 // Experimental: extra world passes when enabled and not warping
		 ServerTickEvents.END_SERVER_TICK.register(server -> {
			 if (!CONFIG.redstoneExperimentalEnabled) return;
			 if (!CONFIG.redstoneAlwaysOn && Engine.isRunning()) return;

			 int passes = Math.max(1, CONFIG.redstonePassesPerServerTick) - 1;
			 if (passes <= 0) return;
			 try {
				 MinecraftServerInvoker inv = (MinecraftServerInvoker) (Object) server;
				 for (int i = 0; i < passes; i++) {
					 try {
						 Engine.redstonePassActive = CONFIG.redstoneSkipEntityTicks;
						 inv.fastforwardengine$invokeTickChildren(() -> false); // single world pass
					 } catch (Throwable ignored) {
						 Engine.redstonePassActive = CONFIG.redstoneSkipEntityTicks;
						 inv.fastforwardengine$invokeTickServer(() -> false);
					 } finally {
						 Engine.redstonePassActive = false;
					 }
				 }
			 } catch (Throwable t) {
				 LOGGER.warn("Redstone experimental passes failed", t);
			 }
		 });
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

	 public static int furnaceTicksPerTick() {
		 return Math.max(1, CONFIG.furnaceTicksPerTick);
	 }

	 public static boolean isFurnaceBoostAlwaysOn() {
		 return CONFIG.furnaceAlwaysOn;
	 }

	 public static boolean isPaused() {
		 return Engine.isPaused();
	 }

	 public static void setPaused(boolean value) {
		 Engine.setPaused(value);
	 }

	 public static boolean isRedstonePassActive() {
		 return Engine.isRedstonePassActive();
	 }

	 static final class Engine {
		 private static volatile boolean running = false;
		 private static volatile boolean warping = false;
		 private static volatile boolean paused = false;
		 private static volatile boolean redstonePassActive = false;
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

		 static boolean isPaused() {
			 return paused;
		 }

		 static void setPaused(boolean value) {
			 paused = value;
		 }

		 static boolean isRedstonePassActive() {
			 return redstonePassActive;
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
			 if (!running || warping || paused) return;
			 warping = true;
			 try {
				 long batch = Math.min(remainingTicks, Math.max(1L, CONFIG.batchSizePerServerTick));
				 final boolean aggressive = CONFIG.experimentalAggressiveWarp;
				 final long budgetNs = Math.max(1L, CONFIG.experimentalMaxWarpMillisPerServerTick) * 1_000_000L;
				 final long endNs = aggressive ? (System.nanoTime() + budgetNs) : Long.MIN_VALUE;
				 MinecraftServerInvoker inv = (MinecraftServerInvoker) (Object) server;
				 long i = 0;
				 while (remainingTicks > 0 && i < batch) {
					 try {
						 try {
							 // Prefer ticking worlds directly; this drives block entities (hoppers), redstone, fluids
							 inv.fastforwardengine$invokeTickChildren(ONE_TICK_ONLY);
						 } catch (Throwable ignored) {
							 // Fallback to full server tick if direct world ticking is unavailable
							 inv.fastforwardengine$invokeTickServer(ONE_TICK_ONLY);
						 }
						 remainingTicks--;
						 i++;
						 if (aggressive && System.nanoTime() >= endNs) {
							 break;
						 }
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


