package io.github.kirougc.japandict.furigana;

import io.github.kirougc.japandict.JapanDictClient;
import io.github.kirougc.japandict.JapanDictConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja el furigana de un texto de la interfaz justo antes de que el juego dibuje el texto.
 */
public final class FuriganaRenderer {
	/** Evita procesar el propio furigana cuando lo dibujamos con drawText. */
	private static boolean drawing;

	private FuriganaRenderer() {
	}

	public static void render(DrawContext context, TextRenderer textRenderer, OrderedText text,
							  int x, int y, int color, boolean shadow) {
		if (drawing || text == null || !JapanDictClient.isActive() || !JapanDictClient.READINGS.isReady()) {
			return;
		}

		List<Integer> codePoints = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		boolean[] hasKanji = {false};
		text.accept((index, style, codePoint) -> {
			codePoints.add(codePoint);
			styles.add(style);
			if (Kana.isKanji(codePoint)) {
				hasKanji[0] = true;
			}
			return true;
		});
		if (!hasKanji[0]) {
			return;
		}

		StringBuilder plain = new StringBuilder(codePoints.size());
		codePoints.forEach(plain::appendCodePoint);
		List<RubySpan> spans = JapanDictClient.READINGS.furigana(plain.toString());
		if (spans.isEmpty()) {
			return;
		}

		JapanDictConfig config = JapanDictClient.config();
		float scale = config.scale;
		float rubyY = config.position == JapanDictConfig.Position.ABOVE
				? y - textRenderer.fontHeight * scale + config.verticalOffset
				: y + textRenderer.fontHeight - 1 + config.verticalOffset;

		Matrix3x2fStack matrices = context.getMatrices();
		drawing = true;
		try {
			for (RubySpan span : spans) {
				int prefixWidth = textRenderer.getWidth(slice(codePoints, styles, 0, span.start()));
				int baseWidth = textRenderer.getWidth(slice(codePoints, styles, span.start(), span.end()));
				Style style = styles.get(span.start()).withUnderline(false).withStrikethrough(false);
				OrderedText ruby = OrderedText.styledForwardsVisitedString(span.reading(), style);
				float rubyWidth = textRenderer.getWidth(ruby) * scale;

				matrices.pushMatrix();
				matrices.translate(x + prefixWidth + (baseWidth - rubyWidth) / 2f, rubyY);
				matrices.scale(scale, scale);
				context.drawText(textRenderer, ruby, 0, 0, color, shadow);
				matrices.popMatrix();
			}
		} finally {
			drawing = false;
		}
	}

	private static OrderedText slice(List<Integer> codePoints, List<Style> styles, int from, int to) {
		return visitor -> {
			for (int i = from; i < to; i++) {
				if (!visitor.accept(i - from, styles.get(i), codePoints.get(i))) {
					return false;
				}
			}
			return true;
		};
	}
}
