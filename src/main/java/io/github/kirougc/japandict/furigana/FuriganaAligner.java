package io.github.kirougc.japandict.furigana;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reparte la lectura de una palabra entre sus tramos de kanji, sin repetir el okurigana.
 * Ejemplos: 食べる/たべる → 食(た); 取り引き/とりひき → 取(と), 引(ひ); 原木/げんぼく → 原木(げんぼく).
 */
public final class FuriganaAligner {
	private FuriganaAligner() {
	}

	/**
	 * @param surface palabra tal como aparece en pantalla
	 * @param reading lectura en hiragana o katakana
	 * @param offset  índice (en code points) donde empieza la palabra dentro del texto completo
	 */
	public static List<RubySpan> align(String surface, String reading, int offset) {
		List<RubySpan> out = new ArrayList<>();
		if (reading == null || reading.isEmpty() || reading.equals("*") || !Kana.containsKanji(surface)) {
			return out;
		}
		String hira = Kana.toHiragana(reading);
		int[] cps = surface.codePoints().toArray();

		// Partir la palabra en tramos: kanji o no-kanji.
		List<int[]> runs = new ArrayList<>(); // {start, end, isKanji}
		int i = 0;
		while (i < cps.length) {
			boolean k = Kana.isKanji(cps[i]);
			int j = i;
			while (j < cps.length && Kana.isKanji(cps[j]) == k) {
				j++;
			}
			runs.add(new int[]{i, j, k ? 1 : 0});
			i = j;
		}

		StringBuilder regex = new StringBuilder("^");
		for (int[] run : runs) {
			if (run[2] == 1) {
				regex.append("(.+?)");
			} else {
				regex.append(Pattern.quote(Kana.toHiragana(new String(cps, run[0], run[1] - run[0]))));
			}
		}
		regex.append('$');

		Matcher m = Pattern.compile(regex.toString()).matcher(hira);
		if (m.matches()) {
			int group = 1;
			for (int[] run : runs) {
				if (run[2] == 1) {
					out.add(new RubySpan(offset + run[0], offset + run[1], m.group(group++)));
				}
			}
			return out;
		}

		// Sin alineación posible: una sola lectura desde el primer kanji hasta el último.
		int first = runs.stream().filter(r -> r[2] == 1).findFirst().map(r -> r[0]).orElse(0);
		int last = runs.stream().filter(r -> r[2] == 1).reduce((a, b) -> b).map(r -> r[1]).orElse(cps.length);
		out.add(new RubySpan(offset + first, offset + last, hira));
		return out;
	}
}
