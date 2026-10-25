package io.github.kirougc.japandict;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.kirougc.japandict.furigana.ReadingEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class JapanDictClient implements ClientModInitializer {
	public static final String MOD_ID = "japandict";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ReadingEngine READINGS = new ReadingEngine();

	private static JapanDictConfig config = new JapanDictConfig();

	public static JapanDictConfig config() {
		return config;
	}

	/** El mod solo hace algo con el juego en japonés. */
	public static boolean isActive() {
		if (!config.enabled) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.getLanguageManager() != null
				&& "ja_jp".equals(client.getLanguageManager().getLanguage());
	}

	@Override
	public void onInitializeClient() {
		config = JapanDictConfig.load();
		READINGS.setOverrides(loadOverrides());
		long start = System.nanoTime();
		READINGS.initAsync().whenComplete((ignored, error) -> {
			if (error != null) {
				LOGGER.error("No se pudo cargar el diccionario de Kuromoji", error);
			} else {
				LOGGER.info("Kuromoji listo en {} ms", (System.nanoTime() - start) / 1_000_000);
			}
		});
	}

	/**
	 * Correcciones de lecturas: las del mod y, encima, las del jugador en
	 * config/japandict-overrides.json ({"鍛冶": "かじ"}).
	 */
	private static Map<String, String> loadOverrides() {
		Gson gson = new Gson();
		var type = new TypeToken<Map<String, String>>() {}.getType();
		Map<String, String> result = new HashMap<>();
		try (InputStream in = JapanDictClient.class.getResourceAsStream("/assets/japandict/furigana_overrides.json")) {
			if (in != null) {
				Map<String, String> builtIn = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
				result.putAll(builtIn);
			}
		} catch (Exception e) {
			LOGGER.warn("No se pudieron leer las correcciones de furigana del mod", e);
		}
		Path user = FabricLoader.getInstance().getConfigDir().resolve("japandict-overrides.json");
		if (Files.exists(user)) {
			try (Reader reader = Files.newBufferedReader(user, StandardCharsets.UTF_8)) {
				Map<String, String> custom = gson.fromJson(reader, type);
				if (custom != null) {
					result.putAll(custom);
				}
			} catch (Exception e) {
				LOGGER.warn("No se pudo leer {}", user, e);
			}
		}
		return result;
	}
}
