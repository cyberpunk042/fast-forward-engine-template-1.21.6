package net.cyberpunk042;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.ExperienceOrb;

/**
 * Utilities for one-shot world cleanup (/stripworld) and periodic cleanup (/keepclean).
 */
public final class WorldMaintenance {
	private static volatile boolean keepCleanEnabled = false;
	private static volatile int keepCleanIntervalTicks = 20;
	private static int keepCleanTickCounter = 0;
	// Items younger than this many ticks are preserved during cleanup
	private static volatile int itemGraceTicks = 200;

	private WorldMaintenance() {}

	public static boolean isKeepCleanEnabled() {
		return keepCleanEnabled;
	}

	public static int getKeepCleanIntervalTicks() {
		return keepCleanIntervalTicks;
	}

	public static void enableKeepClean(int intervalTicks) {
		keepCleanEnabled = true;
		keepCleanIntervalTicks = Math.max(1, intervalTicks);
		keepCleanTickCounter = 0;
	}

	public static void disableKeepClean() {
		keepCleanEnabled = false;
	}

	public static void setItemGraceTicks(int ticks) {
		itemGraceTicks = Math.max(0, ticks);
	}

	public static int getItemGraceTicks() {
		return itemGraceTicks;
	}

	/**
	 * Server tick hook to perform periodic cleanup when keep-clean is enabled.
	 */
	public static void onServerTick(MinecraftServer server) {
		if (!keepCleanEnabled) return;
		keepCleanTickCounter++;
		if (keepCleanTickCounter >= keepCleanIntervalTicks) {
			keepCleanTickCounter = 0;
			try {
				stripWorld(server, true, true, true, true);
			} catch (Throwable t) {
				Fastforwardengine.LOGGER.warn("keepclean tick failed", t);
			}
		}
	}

	/**
	 * Remove non-essential entities across all loaded dimensions.
	 * @return number of entities removed.
	 */
	public static int stripWorld(MinecraftServer server) {
		return stripWorld(server, true, true, true, true);
	}

	/**
	 * Remove entities matching the provided categories.
	 * @param removeMobs remove all mobs (hostile/passive), excluding players
	 * @param removeItems remove dropped item entities
	 * @param removeProjectiles remove projectiles (arrows, tridents, etc.)
	 * @param removeXp remove XP orbs
	 * @return number removed
	 */
	public static int stripWorld(MinecraftServer server, boolean removeMobs, boolean removeItems, boolean removeProjectiles, boolean removeXp) {
		int removed = 0;
		for (ServerLevel level : server.getAllLevels()) {
			try {
				removed += stripLevel(level, removeMobs, removeItems, removeProjectiles, removeXp);
			} catch (Throwable t) {
				Fastforwardengine.LOGGER.warn("stripWorld failed in dimension {}", level.dimension().location(), t);
			}
		}
		return removed;
	}

	/**
	 * Remove non-essential entities from a single level using the provided category flags.
	 */
	public static int stripLevel(ServerLevel level, boolean removeMobs, boolean removeItems, boolean removeProjectiles, boolean removeXp) {
		int removed = 0;
		for (Entity e : level.getAllEntities()) {
			if (e == null) continue;
			if (e instanceof Player) continue;
			boolean kill = false;
			if (removeItems && e instanceof ItemEntity) {
				try {
					// Preserve freshly dropped items
					if (((ItemEntity)e).tickCount < itemGraceTicks) {
						continue;
					}
				} catch (Throwable ignored) {}
				kill = true;
			}
			else if (removeXp && e instanceof ExperienceOrb) kill = true;
			else if (removeProjectiles && e instanceof Projectile) kill = true;
			else if (removeMobs && e instanceof Mob) kill = true;
			if (kill) {
				try {
					e.discard();
					removed++;
				} catch (Throwable ignored) {}
			}
		}
		return removed;
	}
}


