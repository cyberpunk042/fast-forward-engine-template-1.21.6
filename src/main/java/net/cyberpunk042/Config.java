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
}


