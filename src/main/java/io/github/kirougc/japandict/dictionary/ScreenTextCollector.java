package io.github.kirougc.japandict.dictionary;

import io.github.kirougc.japandict.furigana.Kana;
import net.minecraft.text.OrderedText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recuerda los textos japoneses que se dibujaron en pantalla hace un instante, para saber qué
 * palabras mostrar cuando se abre el diccionario.
 */
public final class ScreenTextCollector {
	private static final long RECENT_NANOS = 300_000_000L;
	private static final int MAX_TEXTS = 512;

	private static final Map<String, Long> lastSeen = new LinkedHashMap<>();
	/** Mientras se dibuja el propio diccionario no se recoge nada. */
	private static boolean paused;

	private ScreenTextCollector() {
	}

	public static void setPaused(boolean value) {
		paused = value;
	}

	public static void record(OrderedText text) {
		if (paused || text == null) {
			return;
		}
		StringBuilder plain = new StringBuilder();
		boolean[] japanese = {false};
		text.accept((index, style, codePoint) -> {
			plain.appendCodePoint(codePoint);
			if (Kana.isKanji(codePoint) || Kana.isHiragana(codePoint) || Kana.isKatakana(codePoint)) {
				japanese[0] = true;
			}
			return true;
		});
		if (!japanese[0]) {
			return;
		}
		String key = plain.toString();
		lastSeen.remove(key);
		lastSeen.put(key, System.nanoTime());
		if (lastSeen.size() > MAX_TEXTS) {
			lastSeen.remove(lastSeen.keySet().iterator().next());
		}
	}

	/** Textos dibujados en los últimos instantes, en el orden en que aparecieron. */
	public static List<String> snapshot() {
		long now = System.nanoTime();
		List<String> recent = new ArrayList<>();
		lastSeen.forEach((text, time) -> {
			if (now - time <= RECENT_NANOS) {
				recent.add(text);
			}
		});
		return recent;
	}
}
