package net.cyberpunk042;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
	public static final String FILE_NAME = "fast-forward-engine.json";

	@SerializedName("batchSizePerServerTick")
	public int batchSizePerServerTick = 1000;

	@SerializedName("randomTickSpeedOverride")
	public Integer randomTickSpeedOverride = null; // null = no override

	@SerializedName("hopperTransfersPerTick")
	public int hopperTransfersPerTick = 1; // 1 = vanilla, >1 = accelerate hopper IO per tick during fast-forward

	@SerializedName("hopperAlwaysOn")
	public boolean hopperAlwaysOn = false; // true = apply hopperTransfersPerTick even outside fast-forward

	@SerializedName("experimentalAggressiveWarp")
	public boolean experimentalAggressiveWarp = false; // run extra ticks up to a time budget each server tick

	@SerializedName("experimentalMaxWarpMillisPerServerTick")
	public int experimentalMaxWarpMillisPerServerTick = 200; // time budget per server tick when aggressive warp is enabled

	@SerializedName("furnaceTicksPerTick")
	public int furnaceTicksPerTick = 1; // 1 = vanilla, >1 = accelerate furnace/smoker/blast-furnace processing

	@SerializedName("furnaceAlwaysOn")
	public boolean furnaceAlwaysOn = false; // true = apply furnaceTicksPerTick outside fast-forward

	@SerializedName("redstoneExperimentalEnabled")
	public boolean redstoneExperimentalEnabled = false;

	@SerializedName("redstonePassesPerServerTick")
	public int redstonePassesPerServerTick = 1; // extra full world passes when enabled (outside fast-forward)

	@SerializedName("redstoneAlwaysOn")
	public boolean redstoneAlwaysOn = false; // apply redstone passes even when not fast-forwarding

	@SerializedName("redstoneSkipEntityTicks")
	public boolean redstoneSkipEntityTicks = true; // during experimental redstone passes, skip entity ticks

	@SerializedName("suppressPlayerTicksDuringWarp")
	public boolean suppressPlayerTicksDuringWarp = false; // when true, players are not ticked during fast-forward for max throughput

	@SerializedName("experimentalBackgroundPrecompute")
	public boolean experimentalBackgroundPrecompute = false; // try to precompute RNG-heavy steps off-thread (then apply on main thread)

	@SerializedName("suppressNetworkDuringWarp")
	public boolean suppressNetworkDuringWarp = true; // lower view/simulation distance to reduce network load

	@SerializedName("clientHeadlessDuringWarp")
	public boolean clientHeadlessDuringWarp = true; // in integrated SP, reduce client render load during warp

	@SerializedName("suppressLagWarningsDuringWarp")
	public boolean suppressLagWarningsDuringWarp = true; // suppress/avoid "Can't keep up!" spam while warping

	@SerializedName("fixLagEnabled")
	public boolean fixLagEnabled = false; // run extra server ticks each tick to catch up even when not warping

	@SerializedName("fixLagExtraTicksPerServerTick")
	public int fixLagExtraTicksPerServerTick = 0; // number of extra server tick() invocations per server tick when fixLagEnabled

	@SerializedName("experimentalClientHeadless")
	public boolean experimentalClientHeadless = false; // gate client headless under experimental flag

	@SerializedName("warpScope")
	public String warpScope = "all"; // all | active | dimension

	@SerializedName("warpScopeDimension")
	public String warpScopeDimension = "minecraft:overworld"; // used when warpScope == dimension

	@SerializedName("composterTicksPerTick")
	public int composterTicksPerTick = 1; // extra composter tick passes when enabled

	@SerializedName("composterAlwaysOn")
	public boolean composterAlwaysOn = false;

	@SerializedName("dropperShotsPerPulse")
	public int dropperShotsPerPulse = 1; // extra dispense attempts per scheduled tick

	@SerializedName("dropperAlwaysOn")
	public boolean dropperAlwaysOn = false;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Config loadOrCreate() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		Path configFile = configDir.resolve(FILE_NAME);
		Config cfg;
		if (Files.exists(configFile)) {
			try (Reader r = Files.newBufferedReader(configFile)) {
				cfg = GSON.fromJson(r, Config.class);
				if (cfg == null) cfg = new Config();
			} catch (IOException e) {
				Fastforwardengine.LOGGER.warn("Failed to read config, using defaults", e);
				cfg = new Config();
			}
		} else {
			cfg = new Config();
			try {
				Files.createDirectories(configDir);
				try (Writer w = Files.newBufferedWriter(configFile)) {
					GSON.toJson(cfg, w);
				}
			} catch (IOException e) {
				Fastforwardengine.LOGGER.warn("Failed to write default config", e);
			}
		}
		return cfg;
	}

	public void save() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		Path configFile = configDir.resolve(FILE_NAME);
		try {
			Files.createDirectories(configDir);
			try (Writer w = Files.newBufferedWriter(configFile)) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			Fastforwardengine.LOGGER.warn("Failed to save config", e);
		}
	}

	public String toPrettyJson() {
		return GSON.toJson(this);
	}

	public Config deepCopy() {
		return GSON.fromJson(GSON.toJson(this), Config.class);
	}
}


