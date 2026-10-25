package io.github.kirougc.japandict.furigana;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RomajiTest {
	@Test
	void hepburn() {
		assertEquals("taberu", Romaji.fromKana("たべる"));
		assertEquals("shashin", Romaji.fromKana("しゃしん"));
		assertEquals("kitte", Romaji.fromKana("きって"));
		assertEquals("matcha", Romaji.fromKana("まっちゃ"));
		assertEquals("daiyamondo", Romaji.fromKana("ダイヤモンド"));
		assertEquals("koohii", Romaji.fromKana("コーヒー"));
		assertEquals("ken'i", Romaji.fromKana("けんい"));
		assertEquals("sagyoudai", Romaji.fromKana("さぎょうだい"));
		assertEquals("fairu", Romaji.fromKana("ファイル"));
		assertEquals("kaji", Romaji.fromKana("かじ"));
	}
}
