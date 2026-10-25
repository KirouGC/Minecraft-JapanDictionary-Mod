package io.github.kirougc.japandict.dictionary;

import io.github.kirougc.japandict.furigana.Kana;
import io.github.kirougc.japandict.furigana.ReadingEngine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte los textos de la pantalla en la lista de palabras del diccionario: separa las
 * palabras, junta sustantivos compuestos que existen en el diccionario (作業 + 台 → 作業台),
 * quita repetidas y busca los kanji de cada una.
 */
public final class WordCollector {
	public static final int MAX_WORDS = 40;
	private static final int MAX_COMPOUND = 4;

	/** Una pestaña del diccionario. */
	public record Entry(String key, DictionaryModel.Result result, Map<String, DictionaryModel.Kanji> kanji) {
	}

	private WordCollector() {
	}

	public static List<Entry> collect(List<String> texts, ReadingEngine engine, DictionaryDatabase db) throws IOException {
		Map<String, DictionaryModel.Result> found = new LinkedHashMap<>();
		for (String text : texts) {
			List<ReadingEngine.Word> words = engine.words(text);
			int i = 0;
			while (i < words.size() && found.size() < MAX_WORDS) {
				int used = 1;
				DictionaryModel.Result result = null;
				for (int n = Math.min(MAX_COMPOUND, words.size() - i); n >= 2 && result == null; n--) {
					if (!allNouns(words, i, n)) {
						continue;
					}
					StringBuilder compound = new StringBuilder();
					for (int j = i; j < i + n; j++) {
						compound.append(words.get(j).surface());
					}
					DictionaryModel.Result candidate = db.lookup(compound.toString());
					if (!candidate.words().isEmpty()) {
						result = candidate;
						used = n;
					}
				}
				if (result == null) {
					ReadingEngine.Word word = words.get(i);
					result = db.lookup(word.lookupForm());
					if (result.words().isEmpty() && !word.surface().equals(word.lookupForm())) {
						result = db.lookup(word.surface());
					}
				}
				if (!result.words().isEmpty()) {
					found.putIfAbsent(result.key(), result);
				}
				i += used;
			}
		}

		List<Entry> entries = new ArrayList<>();
		for (DictionaryModel.Result result : found.values()) {
			Map<String, DictionaryModel.Kanji> kanji = new LinkedHashMap<>();
			result.key().codePoints().filter(Kana::isKanji).forEach(cp -> {
				String literal = new String(Character.toChars(cp));
				if (!kanji.containsKey(literal)) {
					try {
						DictionaryModel.Kanji info = db.lookup(literal).kanji();
						if (info != null) {
							kanji.put(literal, info);
						}
					} catch (IOException ignored) {
						// Sin datos del kanji, la pestaña igual sirve.
					}
				}
			});
			entries.add(new Entry(result.key(), result, kanji));
		}
		return entries;
	}

	private static boolean allNouns(List<ReadingEngine.Word> words, int from, int count) {
		for (int j = from; j < from + count; j++) {
			if (!words.get(j).isNoun()) {
				return false;
			}
		}
		return true;
	}
}
