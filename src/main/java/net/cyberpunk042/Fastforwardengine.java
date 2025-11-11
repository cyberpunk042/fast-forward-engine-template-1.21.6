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
						Engine.runAsync(ctx, ticks);
						return 1;
					})
				);
			dispatcher.register(root);
		});
	}

	// Minimal engine as a nested class to avoid cross-file symbol issues
	static final class Engine {
		private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "FastForwardEngine");
			t.setDaemon(true);
			return t;
		});

		private static volatile boolean running = false;

		private Engine() {}

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
				worlds.forEach(Engine::setClearWeather);

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
				LOGGER.error("Fast-forward failed", t);
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
				LOGGER.warn("Could not invoke saveEverything/saveAll/saveAllChunks reflectively", t);
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
}