package io.github.kirougc.japandict.furigana;

/**
 * Coloca el furigana de una línea sin que se pise: cada lectura va centrada sobre su kanji, pero
 * si es más ancha que el kanji y choca con la anterior (攻撃速度 → こうげき|そくど) se corre a la
 * derecha, y nunca empieza antes que el propio texto.
 */
public final class FuriganaLayout {
	private FuriganaLayout() {
	}

	/**
	 * @param baseX      inicio de cada tramo de kanji, relativo al inicio del texto
	 * @param baseWidth  ancho de cada tramo de kanji
	 * @param rubyWidth  ancho de cada lectura ya escalada
	 * @return el x de cada lectura, relativo al inicio del texto
	 */
	public static float[] place(float[] baseX, float[] baseWidth, float[] rubyWidth) {
		float[] x = new float[baseX.length];
		float previousRight = 0;
		for (int i = 0; i < x.length; i++) {
			float centered = baseX[i] + (baseWidth[i] - rubyWidth[i]) / 2f;
			x[i] = Math.max(centered, previousRight);
			previousRight = x[i] + rubyWidth[i];
		}
		return x;
	}
}
