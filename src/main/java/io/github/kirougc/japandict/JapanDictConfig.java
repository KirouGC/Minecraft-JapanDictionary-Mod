package io.github.kirougc.japandict;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configuración guardada en config/japandict.json. */
public final class JapanDictConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum Position {
		ABOVE,
		BELOW
	}

	public boolean enabled = true;
	public Position position = Position.ABOVE;
	/** Tamaño del furigana respecto al texto normal. */
	public float scale = 0.5f;
	/** Desplazamiento vertical extra en píxeles de interfaz (negativo = más arriba). */
	public float verticalOffset = 0f;

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("japandict.json");
	}

	public static JapanDictConfig load() {
		Path path = path();
		JapanDictConfig config = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, JapanDictConfig.class);
			} catch (Exception e) {
				JapanDictClient.LOGGER.warn("Configuración inválida en {}, uso valores por defecto", path, e);
			}
		}
		if (config == null) {
			config = new JapanDictConfig();
		}
		if (config.position == null) {
			config.position = Position.ABOVE;
		}
		config.scale = Math.max(0.25f, Math.min(1f, config.scale));
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			JapanDictClient.LOGGER.warn("No se pudo guardar la configuración", e);
		}
	}
}
