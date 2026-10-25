package io.github.kirougc.japandict.furigana;

import io.github.kirougc.japandict.JapanDictClient;
import io.github.kirougc.japandict.JapanDictConfig;
import net.minecraft.client.MinecraftClient;
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

	/** Furigana de una línea ya colocado; los x son relativos al inicio del texto. */
	public record Layout(List<OrderedText> rubies, float[] x, float scale, float right) {
	}

	/** Calcula el furigana de un texto, o null si no lleva (sin kanji, mod inactivo, etc.). */
	public static Layout layout(TextRenderer textRenderer, OrderedText text) {
		if (drawing || text == null || !JapanDictClient.isActive() || !JapanDictClient.READINGS.isReady()) {
			return null;
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
			return null;
		}

		StringBuilder plain = new StringBuilder(codePoints.size());
		codePoints.forEach(plain::appendCodePoint);
		List<RubySpan> spans = JapanDictClient.READINGS.furigana(plain.toString());
		if (spans.isEmpty()) {
			return null;
		}

		float scale = scale();
		int n = spans.size();
		List<OrderedText> rubies = new ArrayList<>(n);
		float[] baseX = new float[n];
		float[] baseWidth = new float[n];
		float[] rubyWidth = new float[n];
		for (int i = 0; i < n; i++) {
			RubySpan span = spans.get(i);
			baseX[i] = textRenderer.getWidth(slice(codePoints, styles, 0, span.start()));
			baseWidth[i] = textRenderer.getWidth(slice(codePoints, styles, span.start(), span.end()));
			Style style = styles.get(span.start()).withUnderline(false).withStrikethrough(false);
			OrderedText ruby = OrderedText.styledForwardsVisitedString(span.reading(), style);
			rubies.add(ruby);
			rubyWidth[i] = textRenderer.getWidth(ruby) * scale;
		}
		float[] x = FuriganaLayout.place(baseX, baseWidth, rubyWidth);
		return new Layout(rubies, x, scale, x[n - 1] + rubyWidth[n - 1]);
	}

	/** Alto extra que necesita una línea con furigana para no pisar la línea vecina. */
	public static int extraLineHeight(TextRenderer textRenderer, OrderedText text) {
		Layout layout = layout(textRenderer, text);
		return layout == null ? 0 : (int) Math.ceil(textRenderer.fontHeight * layout.scale());
	}

	/** Ancho que ocupa el furigana, que puede pasar del final del texto. */
	public static int rubyWidth(TextRenderer textRenderer, OrderedText text) {
		Layout layout = layout(textRenderer, text);
		return layout == null ? 0 : (int) Math.ceil(layout.right());
	}

	public static void render(DrawContext context, TextRenderer textRenderer, OrderedText text,
							  int x, int y, int color, boolean shadow) {
		Layout layout = layout(textRenderer, text);
		if (layout == null) {
			return;
		}

		JapanDictConfig config = JapanDictClient.config();
		double guiScale = guiScale();
		float scale = layout.scale();
		float rubyY = config.position == JapanDictConfig.Position.ABOVE
				? y - textRenderer.fontHeight * scale + config.verticalOffset
				: y + textRenderer.fontHeight - 1 + config.verticalOffset;
		rubyY = snap(rubyY, guiScale);

		Matrix3x2fStack matrices = context.getMatrices();
		drawing = true;
		try {
			for (int i = 0; i < layout.rubies().size(); i++) {
				matrices.pushMatrix();
				matrices.translate(snap(x + layout.x()[i], guiScale), rubyY);
				matrices.scale(scale, scale);
				context.drawText(textRenderer, layout.rubies().get(i), 0, 0, color, shadow);
				matrices.popMatrix();
			}
		} finally {
			drawing = false;
		}
	}

	private static double guiScale() {
		return MinecraftClient.getInstance().getWindow().getScaleFactor();
	}

	private static float scale() {
		JapanDictConfig config = JapanDictClient.config();
		return config.autoScale ? autoScale(guiScale()) : config.scale;
	}

	/**
	 * Escala con la que cada píxel de la fuente japonesa (16 px de alto dibujados en 8 unidades de
	 * interfaz) ocupa un número entero de píxeles de pantalla, con un tamaño cercano al 60 %.
	 * Con escalas fraccionarias los trazos de los kana se pierden y el texto queda ilegible.
	 * Escala de interfaz 2 → 1.0, 3 → 0.67, 4 → 0.5, 6 → 0.67.
	 */
	static float autoScale(double guiScale) {
		long screenPixelsPerFontPixel = Math.max(1, Math.round(guiScale * 0.5 * 0.6));
		return (float) Math.min(1.0, 2.0 * screenPixelsPerFontPixel / guiScale);
	}

	/** Alinea una coordenada de interfaz a la cuadrícula de píxeles reales de la pantalla. */
	private static float snap(float value, double guiScale) {
		return (float) (Math.round(value * guiScale) / guiScale);
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
