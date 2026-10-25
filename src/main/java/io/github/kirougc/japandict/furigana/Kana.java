package io.github.kirougc.japandict.furigana;

/**
 * Utilidades de clasificación y conversión de caracteres japoneses.
 */
public final class Kana {
	private Kana() {
	}

	/** Kanji, más los signos que se leen como kanji (々 de repetición, 〆, ヶ). */
	public static boolean isKanji(int cp) {
		if (cp == '々' || cp == '〆' || cp == 'ヶ' || cp == 'ヵ') {
			return true;
		}
		return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN;
	}

	public static boolean isHiragana(int cp) {
		return cp >= 0x3041 && cp <= 0x309F;
	}

	public static boolean isKatakana(int cp) {
		return (cp >= 0x30A0 && cp <= 0x30FF) || (cp >= 0x31F0 && cp <= 0x31FF);
	}

	public static boolean containsKanji(CharSequence s) {
		return s.codePoints().anyMatch(Kana::isKanji);
	}

	/** Convierte katakana a hiragana; el resto de caracteres queda igual. */
	public static String toHiragana(CharSequence s) {
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().forEach(cp -> {
			if (cp >= 0x30A1 && cp <= 0x30F6) {
				sb.appendCodePoint(cp - 0x60);
			} else {
				sb.appendCodePoint(cp);
			}
		});
		return sb.toString();
	}
}
