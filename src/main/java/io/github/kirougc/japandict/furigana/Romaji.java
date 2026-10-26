package io.github.kirougc.japandict.furigana;

import java.util.HashMap;
import java.util.Map;

/**
 * Pasa una lectura en kana a romaji Hepburn: たべる → taberu, しゃしん → shashin,
 * きって → kitte, ダイヤモンド → daiyamondo, こーひー → koohii.
 */
public final class Romaji {
	private static final Map<String, String> TABLE = new HashMap<>();

	static {
		String[][] pairs = {
				{"あ", "a"}, {"い", "i"}, {"う", "u"}, {"え", "e"}, {"お", "o"},
				{"か", "ka"}, {"き", "ki"}, {"く", "ku"}, {"け", "ke"}, {"こ", "ko"},
				{"さ", "sa"}, {"し", "shi"}, {"す", "su"}, {"せ", "se"}, {"そ", "so"},
				{"た", "ta"}, {"ち", "chi"}, {"つ", "tsu"}, {"て", "te"}, {"と", "to"},
				{"な", "na"}, {"に", "ni"}, {"ぬ", "nu"}, {"ね", "ne"}, {"の", "no"},
				{"は", "ha"}, {"ひ", "hi"}, {"ふ", "fu"}, {"へ", "he"}, {"ほ", "ho"},
				{"ま", "ma"}, {"み", "mi"}, {"む", "mu"}, {"め", "me"}, {"も", "mo"},
				{"や", "ya"}, {"ゆ", "yu"}, {"よ", "yo"},
				{"ら", "ra"}, {"り", "ri"}, {"る", "ru"}, {"れ", "re"}, {"ろ", "ro"},
				{"わ", "wa"}, {"ゐ", "i"}, {"ゑ", "e"}, {"を", "o"}, {"ん", "n"},
				{"が", "ga"}, {"ぎ", "gi"}, {"ぐ", "gu"}, {"げ", "ge"}, {"ご", "go"},
				{"ざ", "za"}, {"じ", "ji"}, {"ず", "zu"}, {"ぜ", "ze"}, {"ぞ", "zo"},
				{"だ", "da"}, {"ぢ", "ji"}, {"づ", "zu"}, {"で", "de"}, {"ど", "do"},
				{"ば", "ba"}, {"び", "bi"}, {"ぶ", "bu"}, {"べ", "be"}, {"ぼ", "bo"},
				{"ぱ", "pa"}, {"ぴ", "pi"}, {"ぷ", "pu"}, {"ぺ", "pe"}, {"ぽ", "po"},
				{"ゔ", "vu"},
				{"ぁ", "a"}, {"ぃ", "i"}, {"ぅ", "u"}, {"ぇ", "e"}, {"ぉ", "o"},
				{"ゃ", "ya"}, {"ゅ", "yu"}, {"ょ", "yo"}, {"ゎ", "wa"},
				{"きゃ", "kya"}, {"きゅ", "kyu"}, {"きょ", "kyo"},
				{"しゃ", "sha"}, {"しゅ", "shu"}, {"しょ", "sho"}, {"しぇ", "she"},
				{"ちゃ", "cha"}, {"ちゅ", "chu"}, {"ちょ", "cho"}, {"ちぇ", "che"},
				{"にゃ", "nya"}, {"にゅ", "nyu"}, {"にょ", "nyo"},
				{"ひゃ", "hya"}, {"ひゅ", "hyu"}, {"ひょ", "hyo"},
				{"みゃ", "mya"}, {"みゅ", "myu"}, {"みょ", "myo"},
				{"りゃ", "rya"}, {"りゅ", "ryu"}, {"りょ", "ryo"},
				{"ぎゃ", "gya"}, {"ぎゅ", "gyu"}, {"ぎょ", "gyo"},
				{"じゃ", "ja"}, {"じゅ", "ju"}, {"じょ", "jo"}, {"じぇ", "je"},
				{"ぢゃ", "ja"}, {"ぢゅ", "ju"}, {"ぢょ", "jo"},
				{"びゃ", "bya"}, {"びゅ", "byu"}, {"びょ", "byo"},
				{"ぴゃ", "pya"}, {"ぴゅ", "pyu"}, {"ぴょ", "pyo"},
				{"ふぁ", "fa"}, {"ふぃ", "fi"}, {"ふぇ", "fe"}, {"ふぉ", "fo"},
				{"てぃ", "ti"}, {"でぃ", "di"}, {"とぅ", "tu"}, {"どぅ", "du"},
				{"うぃ", "wi"}, {"うぇ", "we"}, {"うぉ", "wo"},
				{"ゔぁ", "va"}, {"ゔぃ", "vi"}, {"ゔぇ", "ve"}, {"ゔぉ", "vo"},
		};
		for (String[] pair : pairs) {
			TABLE.put(pair[0], pair[1]);
		}
	}

	private Romaji() {
	}

	public static String fromKana(String kana) {
		String hira = Kana.toHiragana(kana.replace('ヴ', 'ゔ'));
		StringBuilder out = new StringBuilder();
		boolean doubleNext = false;
		int i = 0;
		while (i < hira.length()) {
			char c = hira.charAt(i);
			if (c == 'っ') {
				doubleNext = true;
				i++;
				continue;
			}
			if (c == 'ー') {
				// Vocal larga en katakana: se repite la vocal anterior.
				char last = out.isEmpty() ? '-' : out.charAt(out.length() - 1);
				out.append("aeiou".indexOf(last) >= 0 ? last : '-');
				i++;
				continue;
			}
			String syllable = null;
			int length = 1;
			if (i + 1 < hira.length()) {
				syllable = TABLE.get(hira.substring(i, i + 2));
				length = 2;
			}
			if (syllable == null) {
				syllable = TABLE.get(String.valueOf(c));
				length = 1;
			}
			if (syllable == null) {
				syllable = String.valueOf(c);
			}
			if (doubleNext) {
				out.append(syllable.startsWith("ch") ? 't' : syllable.charAt(0));
				doubleNext = false;
			}
			if (c == 'ん' && i + 1 < hira.length() && "あいうえおやゆよ".indexOf(hira.charAt(i + 1)) >= 0) {
				syllable = "n'";
			}
			out.append(syllable);
			i += length;
		}
		return out.toString();
	}
}
