/* 
package net.cyberpunk042;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FastForwardEngine {
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "FastForwardEngine");
		t.setDaemon(true);
		return t;
	});

	private static volatile boolean running = false;

	private FastForwardEngine() {}

	static synchronized boolean isRunning() {
		return running;
	}

	static void runAsync(CommandContext<CommandSourceStack> ctx, long ticks) {
		final CommandSourceStack source = ctx.getSource();
		final MinecraftServer server = source.getServer();
		if (isRunning()) {
			source.sendFailure(Component.literal("Fast-forward is already running."));
			return;
		}

		source.sendSuccess(() -> Component.literal("Starting fast-forward of " + ticks + " ticks."), true);

		running = true;
		CompletableFuture.runAsync(() -> runInternal(server, source, ticks), EXECUTOR)
			.whenComplete((ok, err) -> running = false);
	}

	private static void runInternal(MinecraftServer server, CommandSourceStack source, long ticks) {
		final List<ServerLevel> worlds = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			worlds.add(level);
		}

		try {
			worlds.forEach(FastForwardEngine::setClearWeather);

			long start = System.currentTimeMillis();

			for (long i = 0; i < ticks; i++) {
				List<CompletableFuture<Void>> tasks = new ArrayList<>(worlds.size());
				for (ServerLevel level : worlds) {
					tasks.add(CompletableFuture.runAsync(() -> fastTickWorld(level)));
				}
				for (CompletableFuture<Void> task : tasks) {
					task.join();
				}
			}

			long elapsed = System.currentTimeMillis() - start;
			saveServer(server);

			source.sendSuccess(() -> Component.literal("Simulated " + ticks + " ticks in " + elapsed + " ms"), false);
		} catch (Throwable t) {
			Fastforwardengine.LOGGER.error("Fast-forward failed", t);
			source.sendFailure(Component.literal("Fast-forward failed: " + t.getClass().getSimpleName() + ": " + t.getMessage()));
		}
	}

	private static void setClearWeather(ServerLevel level) {
		try {
			level.setWeatherParameters(0, 0, false, false);
		} catch (Throwable ignored) {}
	}

	private static void saveServer(MinecraftServer server) {
		try {
			Method m = findMethod(server.getClass(),
				new String[] { "saveEverything", "saveAll", "saveAllChunks" },
				boolean.class, boolean.class, boolean.class);
			if (m != null) {
				m.invoke(server, false, true, true);
			}
		} catch (Throwable t) {
			Fastforwardengine.LOGGER.warn("Could not invoke saveEverything/saveAll/saveAllChunks reflectively", t);
		}
	}

	private static Method findMethod(Class<?> cls, String[] names, Class<?>... args) {
		for (String name : names) {
			try {
				Method m = cls.getMethod(name, args);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException ignored) {}
		}
		return null;
	}

private static void fastTickWorld(ServerLevel level) {
		setClearWeather(level);
	}
}

*/
package net.cyberpunk042;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fastforwardengine implements ModInitializer {
	public static final String MOD_ID = "fast-forward-engine";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("FastForward Engine initializing");

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fastforward")
				.requires(source -> source.hasPermission(2))
				.then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_000_000_000))
					.executes(ctx -> {
						int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
						Engine.start(ctx, ticks);
						return 1;
					})
				);
			dispatcher.register(root);
		});

		// Register tick handler once (use END tick to add extra ticks safely)
		ServerTickEvents.END_SERVER_TICK.register(Engine::onServerTick);
	}

	// Minimal engine as a nested class to avoid cross-file symbol issues
	static final class Engine {
		private static volatile boolean running = false;
		private static long remainingTicks = 0L;
		private static long totalTicks = 0L;
		private static long startMs = 0L;
		private static CommandSourceStack replyTarget = null;
		private static final java.util.Map<ServerLevel, Boolean> originalDoMobSpawning = new java.util.HashMap<>();
		private static volatile boolean warpingNow = false;
		private static final BooleanSupplier ALWAYS_TRUE = () -> true;

		private Engine() {}

		static synchronized boolean isRunning() {
			return running;
		}

		static void start(CommandContext<CommandSourceStack> ctx, long ticks) {
			final CommandSourceStack source = ctx.getSource();
			final MinecraftServer server = source.getServer();
			if (isRunning()) {
				source.sendFailure(Component.literal("Fast-forward is already running."));
				return;
			}

			source.sendSuccess(() -> Component.literal("Starting fast-forward of " + ticks + " ticks."), true);

			// Initialize on the main thread
			server.execute(() -> {
				synchronized (Engine.class) {
					originalDoMobSpawning.clear();
					for (ServerLevel level : server.getAllLevels()) {
						setClearWeather(level);
						var rule = level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
						originalDoMobSpawning.put(level, rule.get());
						rule.set(false, server);
					}
					remainingTicks = ticks;
					totalTicks = ticks;
					startMs = System.currentTimeMillis();
					replyTarget = source;
					running = true;
				}
			});
		}

		static void onServerTick(MinecraftServer server) {
			if (!running) return;
			if (warpingNow) return; // avoid re-entrance from nested ticks

			warpingNow = true;
			try {
				long batch = Math.min(remainingTicks, 1000L); // do up to 1000 extra ticks per server tick
				final net.cyberpunk042.mixin.MinecraftServerInvoker invoker = (net.cyberpunk042.mixin.MinecraftServerInvoker)(Object)server;
				for (long i = 0; i < batch; i++) {
					try {
						invoker.fastforwardengine$invokeTickChildren(ALWAYS_TRUE);
					} catch (Throwable t) {
						LOGGER.error("Fast-forward tick failed", t);
						break;
					}
					remainingTicks--;
					if (remainingTicks == 0) break;
				}
			} finally {
				warpingNow = false;
			}

			// Consume one tick; all redstone logic runs as part of the server tick naturally
			if (remainingTicks <= 0) {
				long elapsed = System.currentTimeMillis() - startMs;
				// Restore gamerules
				for (ServerLevel level : server.getAllLevels()) {
					try {
						Boolean v = originalDoMobSpawning.get(level);
						if (v != null) {
							level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(v, server);
						}
					} catch (Throwable ignored) {}
				}
				originalDoMobSpawning.clear();
				// Save
				try {
					server.saveEverything(false, true, true);
				} catch (Throwable t) {
					LOGGER.warn("Server save failed", t);
				}
				// Notify
				CommandSourceStack target = replyTarget;
				replyTarget = null;
				running = false;
				if (target != null) {
					long simulated = totalTicks;
					target.sendSuccess(() -> Component.literal("Simulated " + simulated + " ticks in " + elapsed + " ms"), false);
				} else {
					LOGGER.info("Simulated {} ticks in {} ms", totalTicks, elapsed);
				}
			}
		}

		private static void setClearWeather(ServerLevel level) {
			try {
				level.setWeatherParameters(0, 0, false, false);
			} catch (Throwable ignored) {}
		}

		private static java.lang.reflect.Method resolveTickMethod(MinecraftServer server) { return null; }
	}
}