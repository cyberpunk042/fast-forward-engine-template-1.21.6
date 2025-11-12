package net.cyberpunk042;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.cyberpunk042.mixin.ServerLevelInvoker;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
					 .then(Commands.literal("preset")
						 .then(Commands.literal("low").executes(ctx -> {
							 // Conservative defaults
							 CONFIG.batchSizePerServerTick = 200;
							 CONFIG.randomTickSpeedOverride = null;
							 CONFIG.experimentalAggressiveWarp = false;
							 CONFIG.experimentalMaxWarpMillisPerServerTick = 150;
							 CONFIG.hopperTransfersPerTick = 2;
							 CONFIG.hopperAlwaysOn = false;
							 CONFIG.furnaceTicksPerTick = 2;
							 CONFIG.furnaceAlwaysOn = false;
							 CONFIG.redstoneExperimentalEnabled = false;
							 CONFIG.redstonePassesPerServerTick = 1;
							 CONFIG.redstoneAlwaysOn = false;
							 CONFIG.redstoneSkipEntityTicks = true;
							 CONFIG.composterTicksPerTick = 2;
							 CONFIG.composterAlwaysOn = false;
							 CONFIG.dropperShotsPerPulse = 2;
							 CONFIG.dropperAlwaysOn = false;
							 CONFIG.suppressPlayerTicksDuringWarp = true;
							 CONFIG.suppressNetworkDuringWarp = true;
							 CONFIG.clientHeadlessDuringWarp = true;
							 CONFIG.experimentalBackgroundPrecompute = false;
							 CONFIG.save();
							 ctx.getSource().sendSuccess(() -> Component.literal("FastForward preset applied: LOW"), false);
							 return 1;
						 }))
						 .then(Commands.literal("medium").executes(ctx -> {
							 applyPresetMedium(CONFIG);
							 CONFIG.save();
							 ctx.getSource().sendSuccess(() -> Component.literal("FastForward preset applied: MEDIUM"), false);
							 return 1;
						 }))
						 .then(Commands.literal("high").executes(ctx -> {
							 // Aggressive defaults
							 CONFIG.batchSizePerServerTick = 2000;
							 CONFIG.randomTickSpeedOverride = 100;
							 CONFIG.experimentalAggressiveWarp = true;
							 CONFIG.experimentalMaxWarpMillisPerServerTick = 800;
							 CONFIG.hopperTransfersPerTick = 8;
							 CONFIG.hopperAlwaysOn = true;
							 CONFIG.furnaceTicksPerTick = 8;
							 CONFIG.furnaceAlwaysOn = true;
							 CONFIG.redstoneExperimentalEnabled = true;
							 CONFIG.redstonePassesPerServerTick = 4;
							 CONFIG.redstoneAlwaysOn = false;
							 CONFIG.redstoneSkipEntityTicks = true;
							 CONFIG.composterTicksPerTick = 8;
							 CONFIG.composterAlwaysOn = true;
							 CONFIG.dropperShotsPerPulse = 8;
							 CONFIG.dropperAlwaysOn = true;
							 CONFIG.suppressPlayerTicksDuringWarp = true;
							 CONFIG.suppressNetworkDuringWarp = true;
							 CONFIG.clientHeadlessDuringWarp = true;
							 CONFIG.save();
							 ctx.getSource().sendSuccess(() -> Component.literal("FastForward preset applied: HIGH"), false);
							 return 1;
						 }))
						 .then(Commands.literal("ultra").executes(ctx -> {
							 applyPresetUltra(CONFIG);
							 CONFIG.save();
							 ctx.getSource().sendSuccess(() -> Component.literal("FastForward preset applied: ULTRA"), false);
							 return 1;
						 }))
					 )
					 .then(Commands.literal("show").executes(ctx -> {
						 String json = CONFIG.toPrettyJson();
						 for (String line : json.split("\n")) {
							 ctx.getSource().sendSuccess(() -> Component.literal(line), false);
						 }
						 return 1;
					 }))
					 .then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.word()).executes(ctx -> {
						 String key = StringArgumentType.getString(ctx, "key");
						 String value = getConfigValueAsString(key);
						 if (value == null) {
							 ctx.getSource().sendFailure(Component.literal("Unknown key: " + key));
							 return 0;
						 }
						 ctx.getSource().sendSuccess(() -> Component.literal(key + " = " + value), false);
						 return 1;
					 })))
					 .then(Commands.literal("set")
						 .then(Commands.argument("key", StringArgumentType.word())
							 .then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> {
								 String key = StringArgumentType.getString(ctx, "key");
								 String raw = StringArgumentType.getString(ctx, "value");
								 if (!setConfigValueFromString(key, raw)) {
									 ctx.getSource().sendFailure(Component.literal("Invalid key or value for: " + key));
									 return 0;
								 }
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("Set " + key + " = " + getConfigValueAsString(key)), false);
								 return 1;
							 }))
						 )
					 )
					 .then(Commands.literal("reset").executes(ctx -> {
						 CONFIG = new Config();
						 CONFIG.save();
						 ctx.getSource().sendSuccess(() -> Component.literal("FastForward config reset to defaults"), false);
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
						 .then(Commands.literal("players")
							 .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
								 boolean value = BoolArgumentType.getBool(ctx, "value");
								 CONFIG.suppressPlayerTicksDuringWarp = value;
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("suppressPlayerTicksDuringWarp set to " + value), false);
								 return 1;
							 }))
						 )
						 .then(Commands.literal("scope")
							 .then(Commands.literal("all").executes(ctx -> {
								 CONFIG.warpScope = "all";
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("warpScope set to all"), false);
								 return 1;
							 }))
							 .then(Commands.literal("active").executes(ctx -> {
								 CONFIG.warpScope = "active";
								 CONFIG.save();
								 ctx.getSource().sendSuccess(() -> Component.literal("warpScope set to active (current dimension only)"), false);
								 return 1;
							 }))
							 .then(Commands.literal("dimension")
								 .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
									 String id = StringArgumentType.getString(ctx, "id");
									 CONFIG.warpScope = "dimension";
									 CONFIG.warpScopeDimension = id;
									 CONFIG.save();
									 ctx.getSource().sendSuccess(() -> Component.literal("warpScope set to dimension " + id), false);
									 return 1;
								 }))
							 )
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
			 // Quick run with preset and auto-profile
			 dispatcher.register(Commands.literal("fastforward")
				 .requires(src -> src.hasPermission(2) || src.getServer().isSingleplayer())
				 .then(Commands.literal("quick")
					 .then(Commands.literal("low")
						 .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_000_000_000)).executes(ctx -> {
							 CommandSourceStack src = ctx.getSource();
							 int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
							 MinecraftServer server = src.getServer();
							 server.execute(() -> {
								 if (Engine.isRunning()) {
									 src.sendSuccess(() -> Component.literal("Fast-forward is already running."), false);
									 return;
								 }
								 final Config prev = CONFIG.deepCopy();
								 applyPresetLow(CONFIG);
								 final long startNs = System.nanoTime();
								 final long startGt = server.overworld().getLevelData().getGameTime();
								 Engine.setAfterFinishHook(() -> {
									 long wallNs = System.nanoTime() - startNs;
									 long gtNow = server.overworld().getLevelData().getGameTime();
									 long dt = Math.max(0L, gtNow - startGt);
									 double sec = wallNs / 1_000_000_000.0;
									 double tps = sec > 0 ? dt / sec : 0.0;
									 CONFIG = prev;
									 src.sendSuccess(() -> Component.literal(
										 "Quick profile (LOW): " + dt + " ticks in " + (long)(sec * 1000) + " ms (" +
											 String.format(java.util.Locale.ROOT, "%.1f", tps) + " TPS)"
									 ), false);
								 });
								 Engine.start(src, ticks);
							 });
							 return 1;
						 }))
					 )
					 .then(Commands.literal("medium")
						 .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_000_000_000)).executes(ctx -> {
							 CommandSourceStack src = ctx.getSource();
							 int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
							 MinecraftServer server = src.getServer();
							 server.execute(() -> {
								 if (Engine.isRunning()) {
									 src.sendSuccess(() -> Component.literal("Fast-forward is already running."), false);
									 return;
								 }
								 final Config prev = CONFIG.deepCopy();
								 applyPresetMedium(CONFIG);
								 final long startNs = System.nanoTime();
								 final long startGt = server.overworld().getLevelData().getGameTime();
								 Engine.setAfterFinishHook(() -> {
									 long wallNs = System.nanoTime() - startNs;
									 long gtNow = server.overworld().getLevelData().getGameTime();
									 long dt = Math.max(0L, gtNow - startGt);
									 double sec = wallNs / 1_000_000_000.0;
									 double tps = sec > 0 ? dt / sec : 0.0;
									 CONFIG = prev;
									 src.sendSuccess(() -> Component.literal(
										 "Quick profile (MEDIUM): " + dt + " ticks in " + (long)(sec * 1000) + " ms (" +
											 String.format(java.util.Locale.ROOT, "%.1f", tps) + " TPS)"
									 ), false);
								 });
								 Engine.start(src, ticks);
							 });
							 return 1;
						 }))
					 )
					 .then(Commands.literal("high")
						 .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_000_000_000)).executes(ctx -> {
							 CommandSourceStack src = ctx.getSource();
							 int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
							 MinecraftServer server = src.getServer();
								 server.execute(() -> {
									 if (Engine.isRunning()) {
										 src.sendSuccess(() -> Component.literal("Fast-forward is already running."), false);
										 return;
									 }
									 final Config prev = CONFIG.deepCopy();
									 applyPresetHigh(CONFIG);
									 final long startNs = System.nanoTime();
									 final long startGt = server.overworld().getLevelData().getGameTime();
									 Engine.setAfterFinishHook(() -> {
										 long wallNs = System.nanoTime() - startNs;
										 long gtNow = server.overworld().getLevelData().getGameTime();
										 long dt = Math.max(0L, gtNow - startGt);
										 double sec = wallNs / 1_000_000_000.0;
										 double tps = sec > 0 ? dt / sec : 0.0;
										 CONFIG = prev;
										 src.sendSuccess(() -> Component.literal(
											 "Quick profile (HIGH): " + dt + " ticks in " + (long)(sec * 1000) + " ms (" +
												 String.format(java.util.Locale.ROOT, "%.1f", tps) + " TPS)"
										 ), false);
									 });
									 Engine.start(src, ticks);
								 });
							 return 1;
						 }))
					 )
					 .then(Commands.literal("ultra")
						 .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_000_000_000)).executes(ctx -> {
							 CommandSourceStack src = ctx.getSource();
							 int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
							 MinecraftServer server = src.getServer();
							 server.execute(() -> {
								 if (Engine.isRunning()) {
									 src.sendSuccess(() -> Component.literal("Fast-forward is already running."), false);
									 return;
								 }
								 final Config prev = CONFIG.deepCopy();
								 applyPresetUltra(CONFIG);
								 final long startNs = System.nanoTime();
								 final long startGt = server.overworld().getLevelData().getGameTime();
								 Engine.setAfterFinishHook(() -> {
									 long wallNs = System.nanoTime() - startNs;
									 long gtNow = server.overworld().getLevelData().getGameTime();
									 long dt = Math.max(0L, gtNow - startGt);
									 double sec = wallNs / 1_000_000_000.0;
									 double tps = sec > 0 ? dt / sec : 0.0;
									 CONFIG = prev;
									 src.sendSuccess(() -> Component.literal(
										 "Quick profile (ULTRA): " + dt + " ticks in " + (long)(sec * 1000) + " ms (" +
											 String.format(java.util.Locale.ROOT, "%.1f", tps) + " TPS)"
									 ), false);
								 });
								 Engine.start(src, ticks);
							 });
							 return 1;
						 }))
					 )
				 )
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

			 // Help
			 dispatcher.register(Commands.literal("fastforward")
				 .then(Commands.literal("help").executes(ctx -> {
					 String[] lines = new String[]{
						 "Usage: /fastforward <ticks> | stop | profile <start|stop>",
						 "Config: /fastforward config show|get <key>|set <key> <value>|preset <low|high>|reload|reset",
						 "Groups: warp|hopper|furnace|redstone|composter|dropper",
						 "Quick: /fastforward quick <low|high> <ticks>"
					 };
					 for (String l : lines) {
						 ctx.getSource().sendSuccess(() -> Component.literal(l), false);
					 }
					 return 1;
				 }))
			 );
		 });

		 // Drive extra server ticks on the main thread after vanilla tick completes
		 ServerTickEvents.END_SERVER_TICK.register(Engine::onServerTick);

		 // Experimental: extra world passes when enabled and not warping
		 ServerTickEvents.END_SERVER_TICK.register(server -> {
			 if (!CONFIG.redstoneExperimentalEnabled) return;
			 if (!CONFIG.redstoneAlwaysOn && Engine.isRunning()) return;
			 if (Engine.isPaused()) return;

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

	 public static java.util.concurrent.ExecutorService precomputeExecutor() {
		 return Engine.ensurePrecomputeExec();
	 }

	 // Generic config get/set helpers
	 private static String getConfigValueAsString(String key) {
		 return switch (key) {
			 case "batchSizePerServerTick" -> String.valueOf(CONFIG.batchSizePerServerTick);
			 case "randomTickSpeedOverride" -> String.valueOf(CONFIG.randomTickSpeedOverride);
			 case "experimentalAggressiveWarp" -> String.valueOf(CONFIG.experimentalAggressiveWarp);
			 case "experimentalMaxWarpMillisPerServerTick" -> String.valueOf(CONFIG.experimentalMaxWarpMillisPerServerTick);
			 case "experimentalBackgroundPrecompute" -> String.valueOf(CONFIG.experimentalBackgroundPrecompute);
			 case "clientHeadlessDuringWarp" -> String.valueOf(CONFIG.clientHeadlessDuringWarp);
			 case "hopperTransfersPerTick" -> String.valueOf(CONFIG.hopperTransfersPerTick);
			 case "hopperAlwaysOn" -> String.valueOf(CONFIG.hopperAlwaysOn);
			 case "furnaceTicksPerTick" -> String.valueOf(CONFIG.furnaceTicksPerTick);
			 case "furnaceAlwaysOn" -> String.valueOf(CONFIG.furnaceAlwaysOn);
			 case "redstoneExperimentalEnabled" -> String.valueOf(CONFIG.redstoneExperimentalEnabled);
			 case "redstonePassesPerServerTick" -> String.valueOf(CONFIG.redstonePassesPerServerTick);
			 case "redstoneAlwaysOn" -> String.valueOf(CONFIG.redstoneAlwaysOn);
			 case "redstoneSkipEntityTicks" -> String.valueOf(CONFIG.redstoneSkipEntityTicks);
			 case "composterTicksPerTick" -> String.valueOf(CONFIG.composterTicksPerTick);
			 case "composterAlwaysOn" -> String.valueOf(CONFIG.composterAlwaysOn);
			 case "dropperShotsPerPulse" -> String.valueOf(CONFIG.dropperShotsPerPulse);
			 case "dropperAlwaysOn" -> String.valueOf(CONFIG.dropperAlwaysOn);
			 case "suppressPlayerTicksDuringWarp" -> String.valueOf(CONFIG.suppressPlayerTicksDuringWarp);
			 case "experimentalClientHeadless" -> String.valueOf(CONFIG.experimentalClientHeadless);
			 default -> null;
		 };
	 }

	 private static boolean setConfigValueFromString(String key, String raw) {
		 try {
			 switch (key) {
				 case "batchSizePerServerTick" -> CONFIG.batchSizePerServerTick = Integer.parseInt(raw);
				 case "randomTickSpeedOverride" -> {
					 if ("null".equalsIgnoreCase(raw) || "-1".equals(raw)) CONFIG.randomTickSpeedOverride = null;
					 else CONFIG.randomTickSpeedOverride = Integer.parseInt(raw);
				 }
				 case "experimentalAggressiveWarp" -> CONFIG.experimentalAggressiveWarp = Boolean.parseBoolean(raw);
				 case "experimentalMaxWarpMillisPerServerTick" -> CONFIG.experimentalMaxWarpMillisPerServerTick = Integer.parseInt(raw);
				 case "experimentalBackgroundPrecompute" -> CONFIG.experimentalBackgroundPrecompute = Boolean.parseBoolean(raw);
				 case "hopperTransfersPerTick" -> CONFIG.hopperTransfersPerTick = Integer.parseInt(raw);
				 case "hopperAlwaysOn" -> CONFIG.hopperAlwaysOn = Boolean.parseBoolean(raw);
				 case "furnaceTicksPerTick" -> CONFIG.furnaceTicksPerTick = Integer.parseInt(raw);
				 case "furnaceAlwaysOn" -> CONFIG.furnaceAlwaysOn = Boolean.parseBoolean(raw);
				 case "redstoneExperimentalEnabled" -> CONFIG.redstoneExperimentalEnabled = Boolean.parseBoolean(raw);
				 case "redstonePassesPerServerTick" -> CONFIG.redstonePassesPerServerTick = Integer.parseInt(raw);
				 case "redstoneAlwaysOn" -> CONFIG.redstoneAlwaysOn = Boolean.parseBoolean(raw);
				 case "redstoneSkipEntityTicks" -> CONFIG.redstoneSkipEntityTicks = Boolean.parseBoolean(raw);
				 case "clientHeadlessDuringWarp" -> CONFIG.clientHeadlessDuringWarp = Boolean.parseBoolean(raw);
				 case "composterTicksPerTick" -> CONFIG.composterTicksPerTick = Integer.parseInt(raw);
				 case "composterAlwaysOn" -> CONFIG.composterAlwaysOn = Boolean.parseBoolean(raw);
				 case "dropperShotsPerPulse" -> CONFIG.dropperShotsPerPulse = Integer.parseInt(raw);
				 case "dropperAlwaysOn" -> CONFIG.dropperAlwaysOn = Boolean.parseBoolean(raw);
				 case "suppressPlayerTicksDuringWarp" -> CONFIG.suppressPlayerTicksDuringWarp = Boolean.parseBoolean(raw);
				 case "experimentalClientHeadless" -> CONFIG.experimentalClientHeadless = Boolean.parseBoolean(raw);
				 default -> { return false; }
			 }
			 return true;
		 } catch (Throwable t) {
			 return false;
		 }
	 }

	 private static void applyPresetLow(Config c) {
		 c.batchSizePerServerTick = 200;
		 c.randomTickSpeedOverride = null;
		 c.experimentalAggressiveWarp = false;
		 c.experimentalMaxWarpMillisPerServerTick = 150;
		 c.hopperTransfersPerTick = 2;
		 c.hopperAlwaysOn = false;
		 c.furnaceTicksPerTick = 2;
		 c.furnaceAlwaysOn = false;
		 c.redstoneExperimentalEnabled = false;
		 c.redstonePassesPerServerTick = 1;
		 c.redstoneAlwaysOn = false;
		 c.redstoneSkipEntityTicks = true;
		 c.composterTicksPerTick = 2;
		 c.composterAlwaysOn = false;
		 c.dropperShotsPerPulse = 2;
		 c.dropperAlwaysOn = false;
		 c.suppressPlayerTicksDuringWarp = true;
	 }

	 private static void applyPresetHigh(Config c) {
		 c.batchSizePerServerTick = 2000;
		 c.randomTickSpeedOverride = 0; // eliminate random ticks for throughput
		 c.experimentalAggressiveWarp = true;
		 c.experimentalMaxWarpMillisPerServerTick = 800;
		 c.hopperTransfersPerTick = 6;
		 c.hopperAlwaysOn = false;
		 c.furnaceTicksPerTick = 6;
		 c.furnaceAlwaysOn = false;
		 c.redstoneExperimentalEnabled = true;
		 c.redstonePassesPerServerTick = 2;
		 c.redstoneAlwaysOn = false;
		 c.redstoneSkipEntityTicks = true;
		 c.composterTicksPerTick = 4;
		 c.composterAlwaysOn = false;
		 c.dropperShotsPerPulse = 4;
		 c.dropperAlwaysOn = false;
		 c.suppressPlayerTicksDuringWarp = true;
		 c.experimentalBackgroundPrecompute = true;
		 c.suppressNetworkDuringWarp = true;
		 c.clientHeadlessDuringWarp = true;
	 }

	 private static void applyPresetMedium(Config c) {
		 c.batchSizePerServerTick = 1000;
		 c.randomTickSpeedOverride = 0;
		 c.experimentalAggressiveWarp = true;
		 c.experimentalMaxWarpMillisPerServerTick = 400;
		 c.hopperTransfersPerTick = 4;
		 c.hopperAlwaysOn = false;
		 c.furnaceTicksPerTick = 4;
		 c.furnaceAlwaysOn = false;
		 c.redstoneExperimentalEnabled = true;
		 c.redstonePassesPerServerTick = 1;
		 c.redstoneAlwaysOn = false;
		 c.redstoneSkipEntityTicks = true;
		 c.composterTicksPerTick = 3;
		 c.composterAlwaysOn = false;
		 c.dropperShotsPerPulse = 3;
		 c.dropperAlwaysOn = false;
		 c.suppressPlayerTicksDuringWarp = true;
		 c.experimentalBackgroundPrecompute = true;
		 c.suppressNetworkDuringWarp = true;
		 c.clientHeadlessDuringWarp = true;
	 }

	 private static void applyPresetUltra(Config c) {
		 c.batchSizePerServerTick = 5000;
		 c.randomTickSpeedOverride = 0;
		 c.experimentalAggressiveWarp = true;
		 c.experimentalMaxWarpMillisPerServerTick = 1200;
		 c.hopperTransfersPerTick = 12;
		 c.hopperAlwaysOn = false;
		 c.furnaceTicksPerTick = 12;
		 c.furnaceAlwaysOn = false;
		 c.redstoneExperimentalEnabled = true;
		 c.redstonePassesPerServerTick = 4;
		 c.redstoneAlwaysOn = false;
		 c.redstoneSkipEntityTicks = true;
		 c.composterTicksPerTick = 8;
		 c.composterAlwaysOn = false;
		 c.dropperShotsPerPulse = 8;
		 c.dropperAlwaysOn = false;
		 // Keep player ticks ON to allow container GUI syncing during ultra
		 c.suppressPlayerTicksDuringWarp = false;
		 c.experimentalBackgroundPrecompute = true;
		 c.suppressNetworkDuringWarp = true;
		 c.clientHeadlessDuringWarp = true;
	 }

	 static final class Engine {
	private static volatile boolean running = false;
		 private static volatile boolean warping = false;
		 private static volatile boolean paused = false;
		 private static volatile boolean redstonePassActive = false;
		 // Adaptive parameters (reset on start)
		 private static volatile long adaptiveBatch = -1L;
		 private static volatile long adaptiveBudgetNs = -1L;
		 private static long remainingTicks = 0L;
		 private static long totalTicks = 0L;
		 private static long startedAtMs = 0L;
		 private static CommandSourceStack replyTarget = null;
		 private static String startDimensionKey = null;
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

		 static void setAfterFinishHook(Runnable r) {
			 afterFinishHook = r;
		 }

		 private static volatile Runnable afterFinishHook = null;

		 private static volatile ExecutorService precomputeExec = null;

		 static ExecutorService ensurePrecomputeExec() {
			 if (precomputeExec == null) {
				 precomputeExec = Executors.newSingleThreadExecutor(r -> {
					 Thread t = new Thread(r, "ff-engine-precompute");
					 t.setDaemon(true);
					 return t;
				 });
			 }
			 return precomputeExec;
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
					 // Reset adaptive state
					 adaptiveBatch = Math.max(1L, Fastforwardengine.CONFIG.batchSizePerServerTick);
					 adaptiveBudgetNs = Math.max(10L, Fastforwardengine.CONFIG.experimentalMaxWarpMillisPerServerTick) * 1_000_000L;
					 // Adjust network load if configured
					 Integer savedView = null;
					 Integer savedSim = null;
					 if (CONFIG.suppressNetworkDuringWarp) {
						 try {
							 var pl = server.getPlayerList();
							 try { savedView = (Integer) pl.getClass().getMethod("getViewDistance").invoke(pl); } catch (Throwable ignored) {}
							 try { pl.getClass().getMethod("setViewDistance", int.class).invoke(pl, 2); } catch (Throwable ignored) {}
						 } catch (Throwable ignored) {}
					 }
					 // stash in replyTarget holder to restore later via hook
					 if (savedView != null) {
						 final Integer fv = savedView;
						 Runnable restore = () -> {
							 try {
								 var pl = server.getPlayerList();
								 if (fv != null) { try { pl.getClass().getMethod("setViewDistance", int.class).invoke(pl, fv); } catch (Throwable ignored) {} }
							 } catch (Throwable ignored) {}
						 };
						 // chain any existing afterFinishHook
						 if (afterFinishHook != null) {
							 Runnable prev = afterFinishHook;
							 afterFinishHook = () -> { try { prev.run(); } finally { restore.run(); } };
						 } else {
							 afterFinishHook = restore;
						 }
					 }
					 // Try to disable auto-saving to reduce IO overhead during warp
					 try {
						 MinecraftServer.class.getMethod("setSavingDisabled", boolean.class).invoke(server, true);
				} catch (Throwable ignored) {}
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
					 try { startDimensionKey = src.getLevel().dimension().location().toString(); } catch (Throwable ignored) { startDimensionKey = null; }
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
				 // Determine effective batch/budget (adaptive if enabled via aggressive mode)
				 final boolean aggressive = CONFIG.experimentalAggressiveWarp;
				 long targetBatch = Math.max(1L, adaptiveBatch > 0 ? adaptiveBatch : CONFIG.batchSizePerServerTick);
				 long batch = Math.min(remainingTicks, targetBatch);
				 final long budgetNsEff = Math.max(1L, adaptiveBudgetNs > 0 ? adaptiveBudgetNs
					 : (long) CONFIG.experimentalMaxWarpMillisPerServerTick * 1_000_000L);
				 final long endNs = aggressive ? (System.nanoTime() + budgetNsEff) : Long.MIN_VALUE;
				 final long loopStartNs = System.nanoTime();
				 MinecraftServerInvoker inv = (MinecraftServerInvoker) (Object) server;
				 long i = 0;
				 while (remainingTicks > 0 && i < batch) {
					 try {
						 // Dimension scoping: tick only selected worlds if configured
						 if (!"all".equalsIgnoreCase(CONFIG.warpScope)) {
							 java.util.List<ServerLevel> levels = new java.util.ArrayList<>();
							 if ("active".equalsIgnoreCase(CONFIG.warpScope) && startDimensionKey != null) {
								 for (ServerLevel lvl : server.getAllLevels()) {
									 if (lvl.dimension().location().toString().equals(startDimensionKey)) {
										 levels.add(lvl);
									 }
								 }
							 } else if ("dimension".equalsIgnoreCase(CONFIG.warpScope)) {
								 for (ServerLevel lvl : server.getAllLevels()) {
									 if (lvl.dimension().location().toString().equals(CONFIG.warpScopeDimension)) {
										 levels.add(lvl);
									 }
								 }
							 }
							 if (!levels.isEmpty()) {
								 for (ServerLevel lvl : levels) {
									 try {
										 ((ServerLevelInvoker)(Object)lvl).fastforwardengine$invokeTick(ONE_TICK_ONLY);
									 } catch (Throwable t) {
										 net.cyberpunk042.Fastforwardengine.LOGGER.warn("Scoped world tick failed", t);
									 }
								 }
							 } else {
								 // fallback to server-wide tick if no match
								 try {
									 inv.fastforwardengine$invokeTickChildren(ONE_TICK_ONLY);
								 } catch (Throwable ignored) {
									 inv.fastforwardengine$invokeTickServer(ONE_TICK_ONLY);
								 }
							 }
						 } else {
							 try {
								 // Default: tick all worlds
								 inv.fastforwardengine$invokeTickChildren(ONE_TICK_ONLY);
							 } catch (Throwable ignored) {
								 inv.fastforwardengine$invokeTickServer(ONE_TICK_ONLY);
							 }
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
				 // Adaptive tuning: steer toward ~80% budget utilization
				 if (aggressive) {
					 long usedNs = Math.max(1L, System.nanoTime() - loopStartNs);
					 double util = Math.min(2.0, usedNs / (double) budgetNsEff);
					 // Batch tuning
					 long maxCap = Math.max(1L, CONFIG.batchSizePerServerTick) * 4L;
					 if (util < 0.6 && i >= batch) {
						 // We finished batch before hitting budget → increase batch
						 adaptiveBatch = Math.min(maxCap, Math.max(1L, (long) Math.ceil(targetBatch * 1.25)));
					 } else if (util > 0.95) {
						 // We saturated/overran budget → reduce batch modestly
						 adaptiveBatch = Math.max(1L, (long) Math.floor(targetBatch * 0.85));
					 }
					 // Budget tuning
					 long minBudget = 50_000_000L;   // 50 ms
					 long maxBudget = Math.max(budgetNsEff, 2_000_000_000L); // up to 2000 ms
					 if (util < 0.5 && i >= batch) {
						 adaptiveBudgetNs = Math.min(maxBudget, Math.max(minBudget, (long) (budgetNsEff * 1.2)));
					 } else if (util > 1.05) {
						 adaptiveBudgetNs = Math.max(minBudget, (long) (budgetNsEff * 0.85));
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
				 // Re-enable auto-saving after warp
				 try {
					 MinecraftServer.class.getMethod("setSavingDisabled", boolean.class).invoke(server, false);
				 } catch (Throwable ignored) {}
				 CommandSourceStack target = replyTarget;
				 replyTarget = null;
				 running = false;
				 try {
					 if (afterFinishHook != null) {
						 afterFinishHook.run();
					 }
				 } finally {
					 afterFinishHook = null;
				 }
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


