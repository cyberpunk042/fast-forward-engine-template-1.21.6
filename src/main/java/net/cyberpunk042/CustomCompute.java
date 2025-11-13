package net.cyberpunk042;

import net.minecraft.server.MinecraftServer;
import net.cyberpunk042.mixin.MinecraftServerInvoker;
import net.cyberpunk042.mixin.ServerLevelInvoker;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.TimeUnit;

/**
 * Placeholder for custom server tick-loop compute. A mixin will route behavior here when enabled.
 */
public final class CustomCompute {
	private static volatile boolean enabled = false;

	public enum Mode { AUGMENT, REPLACE }
	private static volatile Mode mode = Mode.AUGMENT;
	private static volatile int extraPassesPerTick = 0;
	private static volatile int budgetMillis = 0; // 0 = no time budget

	public enum EntitiesMode { ALL, ESSENTIAL, PLAYERS_ONLY }
	private static volatile EntitiesMode entitiesMode = EntitiesMode.ALL;

	public enum Scope { ALL, DIMENSION }
	private static volatile Scope scope = Scope.ALL;
	private static volatile String scopeDimensionId = null; // used when scope == DIMENSION
	private static volatile boolean onlyWhenNoPlayers = false;
	private static volatile boolean schedulerAdvance = false; // use tickServer vs tickChildren for time advancement

	// Simple stats
	private static volatile long lastRunNanos = 0L;
	private static volatile long totalAugmentPasses = 0L;
	private static volatile long totalReplacePasses = 0L;
	private static volatile long totalOverrides = 0L;
	private static final ThreadLocal<Boolean> IN_TICK_SERVER_HOOK = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static volatile Boolean savedFixLagEnabled = null;
	private static volatile boolean suppressRandomTicks = false;
	private static final java.util.Map<ServerLevel, Integer> savedRandomTickPerLevel = new java.util.WeakHashMap<>();
	@SuppressWarnings("unchecked")
	private static net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.IntegerValue> RANDOM_TICK_KEY = null;

	// Block entity whitelist boost
	private static volatile boolean whitelistBlockEntities = false;
	private static final java.util.List<String> defaultWhitelistedBEClassNames = java.util.Arrays.asList(
		// Item transport / IO
		"net.minecraft.world.level.block.entity.HopperBlockEntity",

		// Processing (smelting/cooking)
		"net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity", // covers Furnace/Smoker/BlastFurnace
		"net.minecraft.world.level.block.entity.CampfireBlockEntity",

		// Brewing and potion farms
		"net.minecraft.world.level.block.entity.BrewingStandBlockEntity",

		// Mob spawners (spawner-based farms)
		"net.minecraft.world.level.block.entity.SpawnerBlockEntity",
		// Trial spawner (1.21+)
		"net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity",

		// Bees / honey farms
		"net.minecraft.world.level.block.entity.BeehiveBlockEntity",

		// Redstone automation
		"net.minecraft.world.level.block.entity.CrafterBlockEntity",
		"net.minecraft.world.level.block.entity.DispenserBlockEntity",
		"net.minecraft.world.level.block.entity.DropperBlockEntity",
		// Pistons in motion (for flying machines/farm sweepers)
		"net.minecraft.world.level.block.entity.PistonMovingBlockEntity",

		// Sculk XP farms
		"net.minecraft.world.level.block.entity.SculkCatalystBlockEntity",
		"net.minecraft.world.level.block.entity.SculkShriekerBlockEntity",
		"net.minecraft.world.level.block.entity.SculkSensorBlockEntity",

		// Optional storages that may have tick-side behavior or be used in sorters (harmless to allow)
		"net.minecraft.world.level.block.entity.BarrelBlockEntity",
		"net.minecraft.world.level.block.entity.ChestBlockEntity",
		"net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity",

		// Utility structures sometimes used in farms (optional; ignored if absent)
		"net.minecraft.world.level.block.entity.ConduitBlockEntity",
		"net.minecraft.world.level.block.entity.BeaconBlockEntity",
		"net.minecraft.world.level.block.entity.LecternBlockEntity"
	);
	private static final java.util.Set<Class<?>> resolvedWhitelistedBEClasses = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

	private CustomCompute() {}

	public static void enable() {
		enabled = true;
		// disable fix-lag while CustomCompute is active; remember prior state
		try {
			boolean prev = Fastforwardengine.CONFIG != null && Fastforwardengine.CONFIG.fixLagEnabled;
			savedFixLagEnabled = prev;
			if (prev && Fastforwardengine.CONFIG != null) {
				Fastforwardengine.CONFIG.fixLagEnabled = false;
				Fastforwardengine.CONFIG.save();
			}
		} catch (Throwable ignored) {}
	}

	public static void disable() {
		enabled = false;
		// restore fix-lag if it had been enabled before CustomCompute
		try {
			if (savedFixLagEnabled != null && savedFixLagEnabled.booleanValue() && Fastforwardengine.CONFIG != null) {
				Fastforwardengine.CONFIG.fixLagEnabled = true;
				Fastforwardengine.CONFIG.save();
			}
		} catch (Throwable ignored) {}
		savedFixLagEnabled = null;
		// restore random tick speeds
		restoreRandomTicks(null);
		// restore saving if we disabled it
		try {
			if (savedSavingDisabled != null) {
				// reflect setSavingDisabled(false)
				// We can't easily access server singleton; retrieve from any level reference during hooks or leave noop
			}
		} catch (Throwable ignored) {}
		savedSavingDisabled = null;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setMode(Mode m) {
		mode = m != null ? m : Mode.AUGMENT;
	}

	public static Mode getMode() {
		return mode;
	}

	public static void setExtraPassesPerTick(int passes) {
		extraPassesPerTick = Math.max(0, passes);
	}

	public static int getExtraPassesPerTick() {
		return extraPassesPerTick;
	}

	public static void setBudgetMillis(int ms) {
		budgetMillis = Math.max(0, ms);
	}

	public static int getBudgetMillis() {
		return budgetMillis;
	}

	public static void setEntitiesMode(EntitiesMode m) {
		entitiesMode = m != null ? m : EntitiesMode.ALL;
	}

	public static EntitiesMode getEntitiesMode() {
		return entitiesMode;
	}

	private static volatile boolean disableMobAI = false;

	public static void setDisableMobAI(boolean value) {
		disableMobAI = value;
	}

	public static boolean isDisableMobAI() {
		return disableMobAI;
	}

	public static void setScope(Scope s) {
		scope = s != null ? s : Scope.ALL;
	}

	public static Scope getScope() {
		return scope;
	}

	public static void setScopeDimensionId(String id) {
		scopeDimensionId = id;
	}

	public static String getScopeDimensionId() {
		return scopeDimensionId;
	}

	public static void setOnlyWhenNoPlayers(boolean value) {
		onlyWhenNoPlayers = value;
	}

	public static boolean isOnlyWhenNoPlayers() {
		return onlyWhenNoPlayers;
	}

	public static void setSchedulerAdvance(boolean value) {
		schedulerAdvance = value;
	}

	public static boolean isSchedulerAdvance() {
		return schedulerAdvance;
	}

	public static void setSuppressRandomTicks(boolean value) {
		suppressRandomTicks = value;
	}

	public static boolean isSuppressRandomTicks() {
		return suppressRandomTicks;
	}

	public static void setWhitelistBlockEntities(boolean value) {
		whitelistBlockEntities = value;
	}

	public static boolean isWhitelistBlockEntities() {
		return whitelistBlockEntities;
	}

	// Phase gating
	private static volatile boolean allowBlockTicks = true;
	private static volatile boolean allowFluidTicks = true;
	private static volatile boolean allowBlockEvents = true;
	private static volatile boolean allowEntityTicks = true;

	public static void setAllowBlockTicks(boolean v) { allowBlockTicks = v; }
	public static boolean isAllowBlockTicks() { return allowBlockTicks; }
	public static void setAllowFluidTicks(boolean v) { allowFluidTicks = v; }
	public static boolean isAllowFluidTicks() { return allowFluidTicks; }
	public static void setAllowBlockEvents(boolean v) { allowBlockEvents = v; }
	public static boolean isAllowBlockEvents() { return allowBlockEvents; }
	public static void setAllowEntityTicks(boolean v) { allowEntityTicks = v; }
	public static boolean isAllowEntityTicks() { return allowEntityTicks; }

	// Networking/visuals suppression
	private static volatile boolean suppressPackets = false;
	private static volatile boolean suppressParticles = false;
	private static volatile boolean suppressSounds = false;

	public static void setSuppressPackets(boolean v) { suppressPackets = v; }
	public static boolean isSuppressPackets() { return suppressPackets; }
	public static void setSuppressParticles(boolean v) { suppressParticles = v; }
	public static boolean isSuppressParticles() { return suppressParticles; }
	public static void setSuppressSounds(boolean v) { suppressSounds = v; }
	public static boolean isSuppressSounds() { return suppressSounds; }

	// World phase skips
	private static volatile boolean skipWeather = false;
	private static volatile boolean skipThunder = false;
	private static volatile boolean skipPrecipitation = false;
	private static volatile boolean skipRaids = false;
	private static volatile boolean skipDragonFight = false;

	public static void setSkipWeather(boolean v) { skipWeather = v; }
	public static boolean isSkipWeather() { return skipWeather; }
	public static void setSkipThunder(boolean v) { skipThunder = v; }
	public static boolean isSkipThunder() { return skipThunder; }
	public static void setSkipPrecipitation(boolean v) { skipPrecipitation = v; }
	public static boolean isSkipPrecipitation() { return skipPrecipitation; }
	public static void setSkipRaids(boolean v) { skipRaids = v; }
	public static boolean isSkipRaids() { return skipRaids; }
	public static void setSkipDragonFight(boolean v) { skipDragonFight = v; }
	public static boolean isSkipDragonFight() { return skipDragonFight; }

	// Despawn control
	private static volatile boolean skipDespawnChecks = false;
	public static void setSkipDespawnChecks(boolean v) { skipDespawnChecks = v; }
	public static boolean isSkipDespawnChecks() { return skipDespawnChecks; }

	// Saving disable
	private static volatile boolean disableSaving = false;
	private static volatile Boolean savedSavingDisabled = null;
	public static void setDisableSaving(boolean v) { disableSaving = v; }
	public static boolean isDisableSaving() { return disableSaving; }

	private static void ensureWhitelistedBEClassesResolved() {
		if (!resolvedWhitelistedBEClasses.isEmpty()) return;
		for (String n : defaultWhitelistedBEClassNames) {
			try {
				Class<?> cls = Class.forName(n);
				resolvedWhitelistedBEClasses.add(cls);
			} catch (Throwable ignored) {}
		}
	}

	private static boolean isAllowedBE(Object be) {
		if (be == null) return false;
		ensureWhitelistedBEClassesResolved();
		Class<?> c = be.getClass();
		for (Class<?> allow : resolvedWhitelistedBEClasses) {
			if (allow != null && allow.isAssignableFrom(c)) return true;
		}
		return false;
	}

	public static void tickWhitelistedBlockEntities(ServerLevel level) {
		if (!whitelistBlockEntities) return;
		try {
			// Try Level.blockEntityTickers (List of TickingBlockEntity with tick())
			java.lang.reflect.Field fTickers = null;
			Class<?> cls = level.getClass().getSuperclass(); // Level is superclass of ServerLevel
			while (cls != null && fTickers == null) {
				try {
					for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
						if (java.util.List.class.isAssignableFrom(f.getType()) && f.getName().toLowerCase().contains("blockentityticker")) {
							fTickers = f; break;
						}
					}
				} catch (Throwable ignored) {}
				cls = cls.getSuperclass();
			}
			if (fTickers != null) {
				fTickers.setAccessible(true);
				Object listObj = fTickers.get(level);
				if (listObj instanceof java.util.List<?> list) {
					for (Object tbe : list) {
						try {
							if (tbe == null) continue;
							Object be = null;
							// find field 'blockEntity' holding the BE
							for (java.lang.reflect.Field ft : tbe.getClass().getDeclaredFields()) {
								if (net.minecraft.world.level.block.entity.BlockEntity.class.isAssignableFrom(ft.getType())) {
                                    ft.setAccessible(true);
									be = ft.get(tbe);
									break;
								}
							}
							if (be == null) continue;
							if (!isAllowedBE(be)) continue;
							// invoke tick()
							try {
								java.lang.reflect.Method mtick = tbe.getClass().getDeclaredMethod("tick");
								mtick.setAccessible(true);
								mtick.invoke(tbe);
							} catch (NoSuchMethodException ns) {
								// try run method name variants
								for (java.lang.reflect.Method m : tbe.getClass().getDeclaredMethods()) {
									if (m.getParameterCount() == 0 && m.getName().toLowerCase().contains("tick")) {
										m.setAccessible(true);
										m.invoke(tbe);
										break;
									}
								}
							}
						} catch (Throwable ignored) {}
					}
				}
				return;
			}
		} catch (Throwable ignored) {}
		// Fallback: iterate blockEntity list if available
		try {
			java.lang.reflect.Field fList = null;
			Class<?> cls2 = level.getClass().getSuperclass();
			while (cls2 != null && fList == null) {
				for (java.lang.reflect.Field f : cls2.getDeclaredFields()) {
					if (java.util.List.class.isAssignableFrom(f.getType()) && f.getName().toLowerCase().contains("blockentity")) {
						fList = f; break;
					}
				}
				cls2 = cls2.getSuperclass();
			}
			if (fList != null) {
				fList.setAccessible(true);
				Object listObj = fList.get(level);
				if (listObj instanceof java.util.List<?> list) {
					for (Object be : list) {
						if (!isAllowedBE(be)) continue;
						// Best effort: try common static serverTick(be) invocations are already done by vanilla; here we can't re-tick without ticker
						// So we skip in fallback
					}
				}
			}
		} catch (Throwable ignored) {}
	}

	private static void ensureRandomTickKey() {
		if (RANDOM_TICK_KEY != null) return;
		try {
			var f = net.minecraft.world.level.GameRules.class.getDeclaredField("RULE_RANDOM_TICK_SPEED");
			f.setAccessible(true);
			RANDOM_TICK_KEY = (net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.IntegerValue>) f.get(null);
		} catch (Throwable first) {
			try {
				var f2 = net.minecraft.world.level.GameRules.class.getDeclaredField("RANDOM_TICK_SPEED");
				f2.setAccessible(true);
				RANDOM_TICK_KEY = (net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.IntegerValue>) f2.get(null);
			} catch (Throwable ignored) {
				RANDOM_TICK_KEY = null;
			}
		}
	}

	private static void applyRandomTicks(MinecraftServer server) {
		if (!suppressRandomTicks) return;
		ensureRandomTickKey();
		if (RANDOM_TICK_KEY == null) return;
		try {
			for (ServerLevel lvl : server.getAllLevels()) {
				try {
					var gr = lvl.getGameRules();
					var rts = gr.getRule(RANDOM_TICK_KEY);
					if (!savedRandomTickPerLevel.containsKey(lvl)) {
						savedRandomTickPerLevel.put(lvl, rts.get());
					}
					rts.set(0, server);
				} catch (Throwable ignored) {}
			}
		} catch (Throwable ignored) {}
	}

	private static void restoreRandomTicks(MinecraftServer server) {
		try {
			if (RANDOM_TICK_KEY == null) return;
			for (var entry : savedRandomTickPerLevel.entrySet()) {
				try {
					ServerLevel lvl = entry.getKey();
					Integer prev = entry.getValue();
					if (lvl != null && prev != null) {
						var gr = lvl.getGameRules();
						gr.getRule(RANDOM_TICK_KEY).set(prev, (server != null ? server : lvl.getServer()));
					}
				} catch (Throwable ignored) {}
			}
		} catch (Throwable ignored) {
		} finally {
			savedRandomTickPerLevel.clear();
		}
	}

	public static long getLastRunNanos() {
		return lastRunNanos;
	}

	public static long getTotalAugmentPasses() {
		return totalAugmentPasses;
	}

	public static long getTotalReplacePasses() {
		return totalReplacePasses;
	}

	public static long getTotalOverrides() {
		return totalOverrides;
	}

	public static void resetStats() {
		lastRunNanos = 0L;
		totalAugmentPasses = 0L;
		totalReplacePasses = 0L;
		totalOverrides = 0L;
	}

	private static void runOnePass(MinecraftServer server) throws Throwable {
		if (scope == Scope.ALL) {
			MinecraftServerInvoker inv = (MinecraftServerInvoker)(Object)server;
			// Never call tickServer from inside tickServer(HEAD) to avoid recursion
			if (schedulerAdvance && !Boolean.TRUE.equals(IN_TICK_SERVER_HOOK.get())) {
				inv.fastforwardengine$invokeTickServer(() -> false);
			} else inv.fastforwardengine$invokeTickChildren(() -> false);
		} else {
			java.util.ArrayList<ServerLevel> order = new java.util.ArrayList<>();
			for (ServerLevel lvl : server.getAllLevels()) { order.add(lvl); }
			// Micro-scheduler: prioritize active dimension (players) and anchored dimensions
			final String targetDim = scopeDimensionId;
			final java.util.Set<String> anchoredDims = new java.util.HashSet<>();
			try {
				if (net.cyberpunk042.Fastforwardengine.CONFIG != null && net.cyberpunk042.Fastforwardengine.CONFIG.anchors != null) {
					for (var a : net.cyberpunk042.Fastforwardengine.CONFIG.anchors) {
						if (a != null && a.dimension != null) anchoredDims.add(a.dimension);
					}
				}
			} catch (Throwable ignored) {}
			order.sort((a, b) -> {
				int pa = 0, pb = 0;
				try { if (!a.players().isEmpty()) pa += 100; } catch (Throwable ignored) {}
				try { if (!b.players().isEmpty()) pb += 100; } catch (Throwable ignored) {}
				try { if (anchoredDims.contains(a.dimension().location().toString())) pa += 50; } catch (Throwable ignored) {}
				try { if (anchoredDims.contains(b.dimension().location().toString())) pb += 50; } catch (Throwable ignored) {}
				try { if (a.dimension().location().toString().equals(targetDim)) pa += 200; } catch (Throwable ignored) {}
				try { if (b.dimension().location().toString().equals(targetDim)) pb += 200; } catch (Throwable ignored) {}
				return Integer.compare(pb, pa);
			});
			for (ServerLevel lvl : order) {
				if (!lvl.dimension().location().toString().equals(targetDim)) continue;
				((ServerLevelInvoker)(Object)lvl).fastforwardengine$invokeTick(() -> false);
				// Optional: boost whitelisted block entities for farms
				tickWhitelistedBlockEntities(lvl);
			}
		}
	}

	/**
	 * Run lightweight augmentations each server tick without blocking vanilla tick.
	 */
	public static void onServerTickHook(MinecraftServer server) {
		if (!enabled) return;
		// reconcile saving disable
		try {
			if (disableSaving && savedSavingDisabled == null) {
				java.lang.reflect.Method m = MinecraftServer.class.getMethod("setSavingDisabled", boolean.class);
				m.setAccessible(true);
				m.invoke(server, true);
				savedSavingDisabled = Boolean.TRUE;
			} else if (!disableSaving && savedSavingDisabled != null) {
				java.lang.reflect.Method m = MinecraftServer.class.getMethod("setSavingDisabled", boolean.class);
				m.setAccessible(true);
				m.invoke(server, false);
				savedSavingDisabled = null;
			}
		} catch (Throwable ignored) {}
		if (mode != Mode.AUGMENT) return;
		// avoid conflicts with engine-driven extra ticks
		if (Fastforwardengine.isPaused()) return;
		if (Fastforwardengine.isFastForwardRunning()) return;
		if (Fastforwardengine.isRedstonePassActive()) return;
		// Apply random tick suppression if configured
		applyRandomTicks(server);
		if (onlyWhenNoPlayers) {
			try {
				if (!server.getPlayerList().getPlayers().isEmpty()) return;
			} catch (Throwable ignored) {}
		}
		int extra = extraPassesPerTick;
		if (extra <= 0) return;
		long startNs = System.nanoTime();
		int executed = 0;
		try {
			for (int i = 0; i < extra; i++) {
				try {
					runOnePass(server);
					totalAugmentPasses++;
					executed++;
				} catch (Throwable t) {
					Fastforwardengine.LOGGER.warn("CustomCompute augment pass failed", t);
					break;
				}
			}
		} catch (Throwable t) {
			Fastforwardengine.LOGGER.warn("CustomCompute augment failed", t);
		} finally {
			lastRunNanos = System.nanoTime() - startNs;
			try { Fastforwardengine.recordCustomSimPasses(executed, lastRunNanos); } catch (Throwable ignored) {}
		}
	}

	/**
	 * Called from mixin at tickServer(HEAD). If returns true, mixin should cancel vanilla and we already executed custom passes.
	 */
	public static boolean handleServerTickPossiblyOverride(MinecraftServer server) {
		if (!enabled) return false;
		// reconcile saving disable
		try {
			if (disableSaving && savedSavingDisabled == null) {
				java.lang.reflect.Method m = MinecraftServer.class.getMethod("setSavingDisabled", boolean.class);
				m.setAccessible(true);
				m.invoke(server, true);
				savedSavingDisabled = Boolean.TRUE;
			} else if (!disableSaving && savedSavingDisabled != null) {
				java.lang.reflect.Method m = MinecraftServer.class.getMethod("setSavingDisabled", boolean.class);
				m.setAccessible(true);
				m.invoke(server, false);
				savedSavingDisabled = null;
			}
		} catch (Throwable ignored) {}
		if (mode != Mode.REPLACE) return false;
		// cooperate with fix-lag feature: never cancel vanilla tick when fix-lag is enabled
		try {
			if (Fastforwardengine.CONFIG != null && Fastforwardengine.CONFIG.fixLagEnabled) {
				return false;
			}
		} catch (Throwable ignored) {}
		// avoid conflicts with engine-driven extra ticks
		if (Fastforwardengine.isPaused()) return false;
		if (Fastforwardengine.isFastForwardRunning()) return false;
		if (Fastforwardengine.isRedstonePassActive()) return false;
		// Apply random tick suppression if configured
		applyRandomTicks(server);
		if (onlyWhenNoPlayers) {
			try {
				if (!server.getPlayerList().getPlayers().isEmpty()) return false;
			} catch (Throwable ignored) {}
		}
		int extra = extraPassesPerTick;
		long deadlineNs = 0L;
		if (budgetMillis > 0) {
			deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis);
		}
		long startNs = System.nanoTime();
		try {
			int iterations = Math.max(1, 1 + extra);
			int executed = 0;
			for (int i = 0; i < iterations; i++) {
				if (deadlineNs != 0L && System.nanoTime() >= deadlineNs) break;
				try {
					runOnePass(server);
					totalReplacePasses++;
					executed++;
				} catch (Throwable t) {
					Fastforwardengine.LOGGER.warn("CustomCompute replace pass failed", t);
					break;
				}
			}
			totalOverrides++;
			lastRunNanos = System.nanoTime() - startNs;
			try { Fastforwardengine.recordCustomSimPasses(executed, lastRunNanos); } catch (Throwable ignored) {}
			return true; // cancel vanilla tickServer
		} catch (Throwable t) {
			Fastforwardengine.LOGGER.warn("CustomCompute replace failed", t);
			return false;
		}
	}

	public static void markInTickServerHook(boolean value) {
		IN_TICK_SERVER_HOOK.set(value ? Boolean.TRUE : Boolean.FALSE);
	}
}


