package io.github.kirougc.japandict.furigana;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadingEngineTest {
	private static final ReadingEngine ENGINE = new ReadingEngine();

	@BeforeAll
	static void init() {
		ENGINE.initBlocking();
		ENGINE.setOverrides(java.util.Map.of("鍛冶", "かじ"));
	}

	/** Formato compacto para comparar: 食(た)べる */
	private static String render(String text) {
		List<RubySpan> spans = ENGINE.furigana(text);
		int[] cps = text.codePoints().toArray();
		StringBuilder sb = new StringBuilder();
		int i = 0;
		for (RubySpan s : spans) {
			sb.append(new String(cps, i, s.start() - i));
			sb.append(new String(cps, s.start(), s.end() - s.start())).append('(').append(s.reading()).append(')');
			i = s.end();
		}
		sb.append(new String(cps, i, cps.length - i));
		return sb.toString();
	}

	@Test
	void okuriganaIsNotRepeated() {
		assertEquals("食(た)べる", render("食べる"));
	}

	@Test
	void minecraftTexts() {
		assertEquals("ゲームに戻(もど)る", render("ゲームに戻る"));
		assertEquals("ダイヤモンドの剣(けん)", render("ダイヤモンドの剣"));
		assertEquals("設定(せってい)...", render("設定..."));
		assertEquals("取引(とりひき)", render("取引"));
	}

	@Test
	void overridesFixKnownMistakes() {
		assertEquals("道具(どうぐ)鍛冶(かじ) - 達人(たつじん)", render("道具鍛冶 - 達人"));
	}

	@Test
	void contentWordsUseDictionaryForm() {
		List<String> words = ENGINE.words("ダイヤモンドの剣を食べた！").stream()
				.map(ReadingEngine.Word::lookupForm).toList();
		assertEquals(List.of("ダイヤモンド", "剣", "食べる"), words);
	}

	@Test
	void kanaOnlyHasNoFurigana() {
		assertTrue(ENGINE.furigana("インベントリ").isEmpty());
		assertTrue(ENGINE.furigana("Minecraft Realms").isEmpty());
	}

	@Test
	void alignerSplitsMixedWords() {
		String r = FuriganaAligner.align("取り引き", "トリヒキ", 0).stream()
				.map(s -> s.start() + "-" + s.end() + ":" + s.reading()).collect(Collectors.joining(","));
		assertEquals("0-1:と,2-3:ひ", r);
	}

	@Test
	void indicesAreCodePoints() {
		// 𠮷 está fuera del BMP (2 chars UTF-16); el índice debe contarlo como 1.
		List<RubySpan> spans = ENGINE.furigana("𠮷田の剣");
		RubySpan sword = spans.get(spans.size() - 1);
		assertEquals(3, sword.start());
		assertEquals("けん", sword.reading());
	}

	@Test
	void printSamples() {
		for (String s : new String[]{"𠮷田の剣", "オークの原木", "司書 - 新米", "道具鍛冶 - 達人", "ラージチェスト", "ベッドから出る",
				"スクリーンショットを2026-10-25_19.18.26.pngとして保存しました", "視野: 最大", "難易度: ノーマル",
				"言語設定...", "アクセシビリティ設定...", "クレジットと帰属表示...", "データの遠隔収集...",
				"フィードバックを送る", "バグを報告する", "セーブしてタイトルへ戻る", "LANに公開", "進捗", "統計",
				"ワールドを保存中", "検索...", "クラフト", "Minecraft 1.21.10/Fabric (Mod導入済み)", "配布禁止！", "終了",
				"作業台", "木の剣", "今日"}) {
			System.out.println(render(s));
		}
	}
}
