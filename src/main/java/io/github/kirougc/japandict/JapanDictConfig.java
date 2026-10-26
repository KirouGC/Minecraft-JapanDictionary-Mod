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
		BELOW,
		HIDDEN;

		public Position next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public boolean enabled = true;
	public Position position = Position.ABOVE;
	/**
	 * Elige el tamaño del furigana según la escala de interfaz para que se vea nítido.
	 * Si es false se usa {@link #scale}.
	 */
	public boolean autoScale = true;
	/** Tamaño del furigana respecto al texto normal, solo con autoScale = false. */
	public float scale = 0.5f;
	/** Desplazamiento vertical extra en píxeles de interfaz (negativo = más arriba). */
	public float verticalOffset = 0f;

	/** De dónde se descarga la base del diccionario la primera vez. */
	public String dictionaryUrl = DEFAULT_DICTIONARY_URL;
	public boolean downloadDictionary = true;
	/** Posición de la ventana del diccionario (esquina superior izquierda); -1 = arriba a la derecha. */
	public int dictionaryX = -1;
	public int dictionaryY = -1;
	/** Clave de la API de DeepL (la gratuita termina en ":fx") para traducir las definiciones japonesas. */
	public String deeplApiKey = "";
	/** Idioma al que DeepL traduce las definiciones: ES, EN-US... */
	public String translationLanguage = "ES";

	public static final String DEFAULT_DICTIONARY_URL =
			"https://github.com/KirouGC/Minecraft-JapanDictionary-Mod/releases/download/dictionary/japandict.db";

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
		if (config.dictionaryUrl == null || config.dictionaryUrl.isBlank()) {
			config.dictionaryUrl = DEFAULT_DICTIONARY_URL;
		}
		if (config.deeplApiKey == null) {
			config.deeplApiKey = "";
		}
		if (config.translationLanguage == null || config.translationLanguage.isBlank()) {
			config.translationLanguage = "ES";
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
