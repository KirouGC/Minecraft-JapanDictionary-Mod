package io.github.kirougc.japandict.furigana;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Calcula el furigana de un texto con Kuromoji. El diccionario tarda un par de segundos en
 * cargarse, así que se inicia en segundo plano; mientras tanto {@link #furigana} devuelve vacío.
 */
public final class ReadingEngine {
	private static final int CACHE_SIZE = 4096;

	private volatile Tokenizer tokenizer;
	private volatile Map<String, String> overrides = Map.of();
	private final Map<String, List<RubySpan>> cache = Collections.synchronizedMap(
			new LinkedHashMap<>(256, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, List<RubySpan>> eldest) {
					return size() > CACHE_SIZE;
				}
			});

	public CompletableFuture<Void> initAsync() {
		return CompletableFuture.runAsync(() -> tokenizer = new Tokenizer(), task -> {
			Thread thread = new Thread(task, "japandict-kuromoji");
			thread.setDaemon(true);
			thread.start();
		});
	}

	/** Para pruebas: carga el diccionario en este mismo hilo. */
	public void initBlocking() {
		tokenizer = new Tokenizer();
	}

	/** Lecturas corregidas a mano, por palabra (ej. 鍛冶 → かじ, que IPADIC lee たんや). */
	public void setOverrides(Map<String, String> overrides) {
		this.overrides = Map.copyOf(overrides);
		cache.clear();
	}

	public boolean isReady() {
		return tokenizer != null;
	}

	/** Lecturas de los tramos de kanji del texto, con índices en code points. */
	public List<RubySpan> furigana(String text) {
		Tokenizer t = tokenizer;
		if (t == null || !Kana.containsKanji(text)) {
			return List.of();
		}
		List<RubySpan> cached = cache.get(text);
		if (cached != null) {
			return cached;
		}
		List<RubySpan> spans = compute(t, text);
		cache.put(text, spans);
		return spans;
	}

	private List<RubySpan> compute(Tokenizer t, String text) {
		List<RubySpan> spans = new ArrayList<>();
		for (Token token : t.tokenize(text)) {
			String surface = token.getSurface();
			if (!Kana.containsKanji(surface)) {
				continue;
			}
			// getPosition() es un índice UTF-16; lo pasamos a code points.
			int cpOffset = text.codePointCount(0, token.getPosition());
			String reading = overrides.getOrDefault(surface, token.getReading());
			spans.addAll(FuriganaAligner.align(surface, reading, cpOffset));
		}
		return List.copyOf(spans);
	}
}
