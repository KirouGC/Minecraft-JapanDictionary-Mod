package io.github.kirougc.japandict.dictionary;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.github.kirougc.japandict.JapanDictClient;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Traduce las definiciones del 国語辞典 con DeepL, solo las que el jugador abre, y guarda cada
 * traducción en config/japandict/translations.json para no volver a pedirla.
 */
public final class DefinitionTranslator {
	private static final Gson GSON = new Gson();

	private final Path cacheFile;
	private final Map<String, String> cache = new ConcurrentHashMap<>();
	private final Set<String> pending = ConcurrentHashMap.newKeySet();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final AtomicInteger version = new AtomicInteger();
	private volatile String error;
	/** Tras un error se espera un minuto antes de volver a llamar a DeepL. */
	private volatile long retryAfter;

	public DefinitionTranslator(Path cacheFile) {
		this.cacheFile = cacheFile;
		load();
	}

	/** Sube cada vez que llega una traducción nueva, para que la ventana se vuelva a armar. */
	public int version() {
		return version.get();
	}

	/** Último error de DeepL (clave inválida, sin cuota...), o null. */
	public String error() {
		return error;
	}

	public String cached(String text) {
		return cache.get(text);
	}

	public boolean isPending(String text) {
		return pending.contains(text);
	}

	/** Pide a DeepL, en una sola llamada, los textos que todavía no están traducidos. */
	public void request(Collection<String> texts, String apiKey, String targetLang) {
		if (apiKey == null || apiKey.isBlank() || System.currentTimeMillis() < retryAfter) {
			return;
		}
		List<String> missing = new ArrayList<>();
		for (String text : texts) {
			if (!cache.containsKey(text) && pending.add(text)) {
				missing.add(text);
			}
		}
		if (missing.isEmpty()) {
			return;
		}

		JsonObject body = new JsonObject();
		JsonArray array = new JsonArray();
		missing.forEach(array::add);
		body.add("text", array);
		body.addProperty("source_lang", "JA");
		body.addProperty("target_lang", targetLang);
		String key = apiKey.trim();
		// Las claves del plan gratuito terminan en ":fx" y usan otro servidor.
		String host = key.endsWith(":fx") ? "https://api-free.deepl.com" : "https://api.deepl.com";
		HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/v2/translate"))
				.timeout(Duration.ofSeconds(20))
				.header("Authorization", "DeepL-Auth-Key " + key)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
				.build();

		http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((response, failure) -> {
			try {
				if (failure != null) {
					retryAfter = System.currentTimeMillis() + 60_000;
					error = "sin conexión con DeepL";
					JapanDictClient.LOGGER.warn("No se pudo conectar con DeepL", failure);
					return;
				}
				if (response.statusCode() != 200) {
					retryAfter = System.currentTimeMillis() + 60_000;
					error = switch (response.statusCode()) {
						case 403 -> "la clave de DeepL no es válida";
						case 456 -> "se terminó la cuota mensual de DeepL";
						case 429 -> "demasiadas consultas a DeepL, probá en un rato";
						default -> "DeepL respondió " + response.statusCode();
					};
					JapanDictClient.LOGGER.warn("DeepL respondió {}: {}", response.statusCode(), response.body());
					return;
				}
				JsonArray translations = GSON.fromJson(response.body(), JsonObject.class).getAsJsonArray("translations");
				for (int i = 0; i < Math.min(missing.size(), translations.size()); i++) {
					cache.put(missing.get(i), translations.get(i).getAsJsonObject().get("text").getAsString());
				}
				error = null;
				save();
			} catch (Exception e) {
				retryAfter = System.currentTimeMillis() + 60_000;
				error = "respuesta inválida de DeepL";
				JapanDictClient.LOGGER.warn("No se pudo leer la respuesta de DeepL", e);
			} finally {
				pending.removeAll(missing);
				version.incrementAndGet();
			}
		});
	}

	private void load() {
		if (!Files.exists(cacheFile)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
			Map<String, String> saved = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {
			}.getType());
			if (saved != null) {
				cache.putAll(saved);
			}
		} catch (Exception e) {
			JapanDictClient.LOGGER.warn("No se pudo leer {}", cacheFile, e);
		}
	}

	private synchronized void save() {
		try {
			Files.createDirectories(cacheFile.getParent());
			Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(new HashMap<>(cache), writer);
			}
			Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			JapanDictClient.LOGGER.warn("No se pudo guardar {}", cacheFile, e);
		}
	}
}
