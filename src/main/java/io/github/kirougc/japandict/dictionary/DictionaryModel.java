package io.github.kirougc.japandict.dictionary;

import java.util.List;

/**
 * Datos tal como vienen en la base generada por tools/build_dictionary.py. Los nombres cortos
 * de los campos son los del JSON, para que el archivo pese menos.
 */
public final class DictionaryModel {
	private DictionaryModel() {
	}

	/** Una palabra de JMdict. */
	public static final class Word {
		/** Formas con kanji (puede faltar, ej. ダイヤモンド). */
		public List<String> k = List.of();
		/** Lecturas en kana. */
		public List<String> r = List.of();
		/** 1 si es una palabra común. */
		public int c;
		/** Acepciones en inglés, con categoría gramatical. */
		public List<Sense> s = List.of();
		/** Acepciones en español (cada una es una lista de traducciones). */
		public List<List<String>> es = List.of();
		/** Frases de ejemplo. */
		public List<Example> x = List.of();

		public boolean isCommon() {
			return c == 1;
		}
	}

	public static final class Sense {
		/** Categorías gramaticales en código JMdict: v1, n, adj-i... */
		public List<String> p = List.of();
		public List<String> en = List.of();
		/** Notas de uso en código JMdict: uk, col, hon... */
		public List<String> m = List.of();
	}

	public static final class Example {
		public String ja;
		public String es;
		public String en;
	}

	/** Un kanji de KANJIDIC2. */
	public static final class Kanji {
		public List<String> on = List.of();
		public List<String> kun = List.of();
		public List<String> en = List.of();
		public List<String> es = List.of();
		/** Grado escolar (1-6, 8 = secundaria, 9-10 = nombres). */
		public int g;
		/** Número de trazos. */
		public int st;
		/** Ranking de frecuencia (1 = el más usado). */
		public int f;
		/** Nivel JLPT antiguo (4 = el más fácil). */
		public int jlpt;
	}

	/** Definición en japonés del Wiktionary japonés. */
	public static final class Definition {
		/** Categoría gramatical, ej. 名詞. */
		public String p;
		public List<String> d = List.of();
	}

	/** Todo lo que la base sabe de una clave (palabra, lectura o kanji). */
	public record Result(String key, List<Word> words, Kanji kanji, List<Definition> definitions) {
		public boolean isEmpty() {
			return words.isEmpty() && kanji == null && definitions.isEmpty();
		}
	}
}
