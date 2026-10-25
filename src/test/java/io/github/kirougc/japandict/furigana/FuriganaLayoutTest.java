package io.github.kirougc.japandict.furigana;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class FuriganaLayoutTest {
	@Test
	void narrowRubyIsCentered() {
		assertArrayEquals(new float[]{4f}, FuriganaLayout.place(new float[]{0}, new float[]{16}, new float[]{8}));
	}

	@Test
	void wideRubyNeverStartsBeforeTheText() {
		// 耐久力 (24) con たいきゅうりょく (37): centrado empezaría en -6.5.
		assertArrayEquals(new float[]{0f}, FuriganaLayout.place(new float[]{0}, new float[]{24}, new float[]{37}));
	}

	@Test
	void collidingRubiesArePushedRight() {
		// 攻撃 (0-16) こうげき 21 de ancho, 速度 (16-32) そくど 16 de ancho.
		float[] x = FuriganaLayout.place(new float[]{10, 26}, new float[]{16, 16}, new float[]{21, 16});
		assertArrayEquals(new float[]{7.5f, 28.5f}, x);
	}
}
