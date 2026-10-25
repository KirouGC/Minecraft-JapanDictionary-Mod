package io.github.kirougc.japandict.dictionary;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Genera una base con los datos de muestra de tools/testdata y la lee como lo hace el mod. */
class DictionaryDatabaseTest {
	private static DictionaryDatabase db;

	@BeforeAll
	static void build() throws Exception {
		Path out = Files.createTempFile("japandict", ".db");
		String data = "tools/testdata/";
		Process process = new ProcessBuilder("python3", "tools/build_dictionary.py",
				"--jmdict", data + "JMdict_sample.xml",
				"--kanjidic", data + "kanjidic2_sample.xml",
				"--tatoeba-jpn", data + "jpn_sentences.tsv",
				"--tatoeba-spa", data + "spa_sentences.tsv",
				"--tatoeba-eng", data + "eng_sentences.tsv",
				"--tatoeba-jpn-spa", data + "jpn-spa_links.tsv",
				"--tatoeba-jpn-eng", data + "jpn-eng_links.tsv",
				"--wiktionary", data + "wiktionary_sample.jsonl",
				"--out", out.toString())
				.inheritIO().start();
		assertEquals(0, process.waitFor(), "build_dictionary.py falló");
		db = DictionaryDatabase.open(out);
	}

	@AfterAll
	static void close() throws Exception {
		db.close();
	}

	@Test
	void wordHasSpanishEnglishAndPartOfSpeech() throws Exception {
		DictionaryModel.Result result = db.lookup("食べる");
		assertEquals(1, result.words().size());
		DictionaryModel.Word word = result.words().get(0);
		assertEquals(List.of("たべる"), word.r);
		assertTrue(word.isCommon());
		assertEquals(List.of(List.of("comer", "alimentarse")), word.es);
		assertEquals(List.of("to eat"), word.s.get(0).en);
		assertEquals(List.of("v1", "vt"), word.s.get(0).p);
		// La categoría gramatical sigue valiendo para la acepción siguiente, como dice JMdict.
		assertEquals(List.of("v1", "vt"), word.s.get(1).p);
	}

	@Test
	void readingFindsTheSameWord() throws Exception {
		assertEquals("食べる", db.lookup("たべる").words().get(0).k.get(0));
	}

	@Test
	void examplesPreferSpanish() throws Exception {
		DictionaryModel.Word word = db.lookup("食べる").words().get(0);
		assertEquals("私はパンを食べる。", word.x.get(0).ja);
		assertEquals("Yo como pan.", word.x.get(0).es);
		assertEquals(2, word.x.size());
	}

	@Test
	void kanaOnlyWordHasExample() throws Exception {
		DictionaryModel.Word word = db.lookup("ダイヤモンド").words().get(0);
		assertEquals(List.of(List.of("diamante")), word.es);
		assertEquals("He has a diamond sword.", word.x.get(0).en);
	}

	@Test
	void kanjiInfo() throws Exception {
		DictionaryModel.Kanji kanji = db.lookup("食").kanji();
		assertNotNull(kanji);
		assertEquals(List.of("ショク", "ジキ"), kanji.on);
		assertEquals(List.of("く.う", "た.べる"), kanji.kun);
		assertEquals(List.of("comer", "comida"), kanji.es);
		assertEquals(9, kanji.st);
	}

	@Test
	void singleKanjiWordAndKanjiShareAKey() throws Exception {
		DictionaryModel.Result result = db.lookup("剣");
		assertEquals("けん", result.words().get(0).r.get(0));
		assertEquals(List.of("espada"), result.kanji().es);
		assertEquals("両刃の刀剣。つるぎ。", result.definitions().get(0).d.get(0));
	}

	@Test
	void japaneseDefinitions() throws Exception {
		DictionaryModel.Result result = db.lookup("食べる");
		assertEquals("動詞", result.definitions().get(0).p);
		assertEquals(2, result.definitions().get(0).d.size());
	}

	@Test
	void entitiesAreDecoded() throws Exception {
		assertEquals("logs & raw timber", db.lookup("原木").words().get(0).s.get(0).en.get(0));
	}

	@Test
	void unknownWordIsEmpty() throws Exception {
		assertTrue(db.lookup("存在しない").isEmpty());
	}
}
