package io.github.kirougc.japandict.gui;

import io.github.kirougc.japandict.JapanDictClient;
import io.github.kirougc.japandict.JapanDictConfig;
import io.github.kirougc.japandict.dictionary.DictionaryDatabase;
import io.github.kirougc.japandict.dictionary.DictionaryManager;
import io.github.kirougc.japandict.dictionary.DictionaryModel;
import io.github.kirougc.japandict.dictionary.PartOfSpeech;
import io.github.kirougc.japandict.dictionary.ScreenTextCollector;
import io.github.kirougc.japandict.dictionary.WordCollector;
import io.github.kirougc.japandict.furigana.FuriganaRenderer;
import io.github.kirougc.japandict.furigana.Romaji;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Ventana flotante del Modo Diccionario: una pestaña por palabra que había en pantalla al abrirla.
 * Se dibuja encima de cualquier pantalla (inventario, cofre, aldeano...) sin cerrarla, o dentro de
 * {@link DictionaryScreen} cuando se abre jugando. Imita el panel gris de los inventarios.
 */
public final class DictionaryOverlay {
	public static final DictionaryOverlay INSTANCE = new DictionaryOverlay();

	private static final int MAX_WIDTH = 220;
	private static final int MAX_HEIGHT = 236;
	private static final int MARGIN = 4;
	private static final int PADDING = 6;
	private static final int TAB_HEIGHT = 20;
	private static final int ARROW_WIDTH = 10;
	private static final int SCROLL_STEP = 12;

	private static final int PANEL = 0xFFC6C6C6;
	private static final int TAB_OFF = 0xFFA8A8A8;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int OUTLINE = 0xFF000000;
	private static final int TEXT = 0xFF404040;
	private static final int TEXT_STRONG = 0xFF202020;
	private static final int TEXT_SOFT = 0xFF6A6A6A;
	private static final int HEADER = 0xFF2A3F7A;
	private static final int COMMON = 0xFF2E7D32;

	private boolean visible;
	private List<String> texts = List.of();
	private volatile List<WordCollector.Entry> entries;
	private volatile String collectError;
	private CompletableFuture<?> collecting;

	private int selected;
	private int firstTab;
	private int scroll;
	private int maxScroll;

	private List<Line> lines;
	private int linesFor = -1;
	private int linesWidth;
	private int linesTranslations = -1;

	// Geometría del último dibujo, para los clics.
	private int panelX, panelY, panelW, panelH;
	private int contentY, contentH;
	private final List<int[]> tabBoxes = new ArrayList<>();
	private int[] leftArrow, rightArrow;
	private int lastVisibleTab;

	// Arrastre de la ventana con el clic izquierdo.
	private boolean dragging;
	private double dragOffsetX, dragOffsetY;

	private record Line(OrderedText text, int gapBefore) {
	}

	private DictionaryOverlay() {
	}

	public boolean isVisible() {
		return visible;
	}

	public void open() {
		texts = ScreenTextCollector.snapshot();
		entries = null;
		collectError = null;
		collecting = null;
		selected = 0;
		firstTab = 0;
		scroll = 0;
		lines = null;
		linesFor = -1;
		visible = true;
	}

	public void close() {
		visible = false;
		stopDragging();
	}

	public void toggle() {
		if (visible) {
			close();
		} else {
			open();
		}
	}

	private void startCollecting() {
		DictionaryDatabase db = DictionaryManager.database();
		if (collecting != null || db == null) {
			return;
		}
		List<String> snapshot = texts;
		collecting = CompletableFuture.runAsync(() -> {
			try {
				entries = WordCollector.collect(snapshot, JapanDictClient.READINGS, db);
			} catch (Exception e) {
				collectError = e.getMessage();
				JapanDictClient.LOGGER.error("No se pudieron buscar las palabras", e);
			}
		});
	}

	// ---------------------------------------------------------------- dibujo

	public void render(DrawContext context, int screenWidth, int screenHeight) {
		if (!visible) {
			return;
		}
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		panelW = Math.min(MAX_WIDTH, screenWidth - 2 * MARGIN);
		panelH = Math.min(MAX_HEIGHT, screenHeight - 2 * MARGIN);
		placePanel(screenWidth, screenHeight);

		ScreenTextCollector.setPaused(true);
		context.createNewRootLayer();
		try {
			drawPanel(context, panelX, panelY, panelW, panelH);
			if (DictionaryManager.status() == DictionaryManager.Status.READY) {
				startCollecting();
			}
			List<WordCollector.Entry> current = entries;
			if (current == null || current.isEmpty()) {
				tabBoxes.clear();
				leftArrow = rightArrow = null;
				drawMessage(context, tr, statusMessage(current));
			} else {
				selected = Math.min(selected, current.size() - 1);
				drawTabs(context, tr, current);
				drawContent(context, tr, current.get(selected));
			}
		} finally {
			ScreenTextCollector.setPaused(false);
		}
	}

	/** Coloca la ventana donde la dejó el jugador (o arriba a la derecha), siempre dentro de la pantalla. */
	private void placePanel(int screenWidth, int screenHeight) {
		JapanDictConfig config = JapanDictClient.config();
		if (dragging && GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(),
				GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
			stopDragging(); // se soltó el botón fuera de la ventana del juego
		}
		int x = config.dictionaryX < 0 ? screenWidth - panelW - MARGIN : config.dictionaryX;
		int y = config.dictionaryY < 0 ? MARGIN : config.dictionaryY;
		panelX = Math.max(0, Math.min(x, screenWidth - panelW));
		panelY = Math.max(0, Math.min(y, screenHeight - panelH));
	}

	private Text statusMessage(List<WordCollector.Entry> current) {
		return switch (DictionaryManager.status()) {
			case NOT_STARTED, LOADING -> Text.literal("Cargando el diccionario…");
			case DOWNLOADING -> {
				float progress = DictionaryManager.downloadProgress();
				yield Text.literal(progress < 0 ? "Descargando el diccionario…"
						: String.format("Descargando el diccionario… %d %%", (int) (progress * 100)));
			}
			case FAILED -> Text.literal("No se pudo cargar el diccionario: " + DictionaryManager.error());
			case READY -> collectError != null ? Text.literal("Error al buscar las palabras: " + collectError)
					: current == null ? Text.literal("Buscando palabras…")
					: Text.literal("No hay palabras japonesas en pantalla.");
		};
	}

	private void drawMessage(DrawContext context, TextRenderer tr, Text message) {
		int y = panelY + PADDING + 4;
		for (OrderedText line : tr.wrapLines(message, panelW - 2 * PADDING)) {
			context.drawText(tr, line, panelX + PADDING, y, TEXT, false);
			y += tr.fontHeight + 1;
		}
	}

	private static void drawPanel(DrawContext context, int x, int y, int w, int h) {
		context.fill(x, y, x + w, y + h, OUTLINE);
		context.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
		context.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
		context.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
		context.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADOW);
		context.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADOW);
	}

	private void drawTabs(DrawContext context, TextRenderer tr, List<WordCollector.Entry> current) {
		tabBoxes.clear();
		// Las pestañas crecen para que el furigana de la palabra entre entero dentro de ellas.
		int ruby = 0;
		for (WordCollector.Entry entry : current) {
			ruby = Math.max(ruby, FuriganaRenderer.extraLineHeight(tr, Text.literal(entry.key()).asOrderedText()));
		}
		int tabHeight = Math.max(TAB_HEIGHT, tr.fontHeight + 4 + (ruby > 0 ? ruby + 3 : 0));
		int top = panelY + PADDING - 2;
		int left = panelX + PADDING - 2;
		int right = panelX + panelW - PADDING + 2;

		boolean overflow = totalTabsWidth(tr, current) > right - left;
		leftArrow = rightArrow = null;
		if (overflow) {
			leftArrow = new int[]{left, top, left + ARROW_WIDTH, top + tabHeight};
			rightArrow = new int[]{right - ARROW_WIDTH, top, right, top + tabHeight};
			drawArrow(context, tr, leftArrow, "<", firstTab > 0);
			left += ARROW_WIDTH + 2;
			right -= ARROW_WIDTH + 2;
		}

		firstTab = Math.max(0, Math.min(firstTab, current.size() - 1));
		int x = left;
		lastVisibleTab = firstTab;
		for (int i = firstTab; i < current.size(); i++) {
			String label = current.get(i).key();
			int labelWidth = tabLabelWidth(tr, label);
			int w = Math.max(20, labelWidth + 8);
			if (x + w > right) {
				break;
			}
			boolean on = i == selected;
			context.fill(x, top, x + w, top + tabHeight, OUTLINE);
			context.fill(x + 1, top + 1, x + w - 1, top + tabHeight, on ? PANEL : TAB_OFF);
			context.fill(x + 1, top + 1, x + w - 1, top + 2, on ? LIGHT : PANEL);
			context.drawText(tr, label, x + (w - labelWidth) / 2, top + tabHeight - tr.fontHeight - 2,
					on ? TEXT_STRONG : TEXT, false);
			tabBoxes.add(new int[]{x, top, x + w, top + tabHeight, i});
			lastVisibleTab = i;
			x += w + 1;
		}
		if (overflow) {
			drawArrow(context, tr, rightArrow, ">", lastVisibleTab < current.size() - 1);
		}
		context.fill(panelX + 3, top + tabHeight, panelX + panelW - 3, top + tabHeight + 1, SHADOW);
		contentY = top + tabHeight + 4;
		contentH = panelY + panelH - PADDING - contentY;
	}

	/** Ancho de la palabra o de su furigana, lo que sea más largo (けんさく es más ancho que 検索). */
	private static int tabLabelWidth(TextRenderer tr, String label) {
		return Math.max(tr.getWidth(label), FuriganaRenderer.rubyWidth(tr, Text.literal(label).asOrderedText()));
	}

	private static int totalTabsWidth(TextRenderer tr, List<WordCollector.Entry> current) {
		int total = 0;
		for (WordCollector.Entry entry : current) {
			total += Math.max(20, tabLabelWidth(tr, entry.key()) + 8) + 1;
		}
		return total;
	}

	private static void drawArrow(DrawContext context, TextRenderer tr, int[] box, String symbol, boolean enabled) {
		context.fill(box[0], box[1], box[2], box[3], OUTLINE);
		context.fill(box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1, enabled ? TAB_OFF : PANEL);
		context.drawText(tr, symbol, box[0] + (box[2] - box[0] - tr.getWidth(symbol)) / 2 + 1,
				box[1] + (box[3] - box[1] - tr.fontHeight) / 2 + 1, enabled ? TEXT_STRONG : TEXT_SOFT, false);
	}

	private void drawContent(DrawContext context, TextRenderer tr, WordCollector.Entry entry) {
		int x = panelX + PADDING;
		int width = panelW - 2 * PADDING - 4;
		int translations = JapanDictClient.translator().version();
		if (lines == null || linesFor != selected || linesWidth != width || linesTranslations != translations) {
			lines = buildLines(tr, entry, width);
			linesFor = selected;
			linesWidth = width;
			linesTranslations = translations;
		}

		int total = 0;
		for (Line line : lines) {
			total += lineHeight(tr, line);
		}
		maxScroll = Math.max(0, total - contentH);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		context.enableScissor(panelX + 2, contentY - 1, panelX + panelW - 2, contentY + contentH);
		int y = contentY - scroll;
		for (Line line : lines) {
			int height = lineHeight(tr, line);
			int textY = y + height - tr.fontHeight;
			if (textY + tr.fontHeight >= contentY - 8 && y <= contentY + contentH) {
				context.drawText(tr, line.text(), x, textY, TEXT, false);
			}
			y += height;
		}
		context.disableScissor();

		if (maxScroll > 0) {
			int trackX = panelX + panelW - PADDING - 2;
			int thumbH = Math.max(10, contentH * contentH / total);
			int thumbY = contentY + (contentH - thumbH) * scroll / maxScroll;
			context.fill(trackX, contentY, trackX + 3, contentY + contentH, 0xFF8B8B8B);
			context.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFE0E0E0);
			context.fill(trackX + 2, thumbY, trackX + 3, thumbY + thumbH, SHADOW);
		}
	}

	private static int lineHeight(TextRenderer tr, Line line) {
		int ruby = FuriganaRenderer.extraLineHeight(tr, line.text());
		return line.gapBefore() + tr.fontHeight + 1 + (ruby > 0 ? ruby + 1 : 0);
	}

	// ---------------------------------------------------------------- contenido

	private List<Line> buildLines(TextRenderer tr, WordCollector.Entry entry, int width) {
		List<Line> out = new ArrayList<>();
		DictionaryModel.Result result = entry.result();
		DictionaryModel.Word word = result.words().get(0);
		String head = entry.key();
		String reading = word.r.isEmpty() ? head : word.r.get(0);

		MutableText title = color(head, TEXT_STRONG);
		if (!reading.equals(head)) {
			title.append(color("  【" + reading + "】", TEXT));
		}
		title.append(color("  " + Romaji.fromKana(reading), TEXT_SOFT).formatted(Formatting.ITALIC));
		add(out, tr, title, width, 0);

		MutableText tags = Text.empty();
		if (word.isCommon()) {
			tags.append(color("común", COMMON)).append(color(" · ", TEXT_SOFT));
		}
		List<String> pos = word.s.isEmpty() ? List.of() : word.s.get(0).p;
		tags.append(color(pos.isEmpty() ? "—" : PartOfSpeech.names(pos), TEXT_SOFT));
		add(out, tr, tags, width, 0);

		// Traducción: español si hay, si no inglés.
		boolean spanish = !word.es.isEmpty();
		add(out, tr, header(spanish ? "Traducción" : "Traducción (en inglés)"), width, 5);
		if (spanish) {
			for (int i = 0; i < Math.min(5, word.es.size()); i++) {
				add(out, tr, color((i + 1) + ". " + String.join("; ", word.es.get(i)), TEXT), width, 0);
			}
		} else {
			for (int i = 0; i < Math.min(5, word.s.size()); i++) {
				DictionaryModel.Sense sense = word.s.get(i);
				MutableText line = color((i + 1) + ". " + String.join("; ", sense.en), TEXT);
				if (!sense.m.isEmpty()) {
					line.append(color("  (" + PartOfSpeech.names(sense.m) + ")", TEXT_SOFT));
				}
				add(out, tr, line, width, 0);
			}
		}

		if (result.words().size() > 1) {
			add(out, tr, header("Otras palabras que se escriben igual"), width, 5);
			for (DictionaryModel.Word other : result.words().subList(1, Math.min(4, result.words().size()))) {
				String meaning = !other.es.isEmpty() ? String.join("; ", other.es.get(0))
						: other.s.isEmpty() ? "" : String.join("; ", other.s.get(0).en);
				add(out, tr, color("【" + String.join("・", other.r) + "】 ", TEXT).append(color(meaning, TEXT_SOFT)), width, 0);
			}
		}

		if (!entry.kanji().isEmpty()) {
			add(out, tr, header("Kanji"), width, 5);
			for (Map.Entry<String, DictionaryModel.Kanji> k : entry.kanji().entrySet()) {
				DictionaryModel.Kanji info = k.getValue();
				List<String> meanings = info.es.isEmpty() ? info.en : info.es;
				add(out, tr, color(k.getKey() + "  ", TEXT_STRONG).append(color(String.join(", ", meanings), TEXT)), width, 2);
				MutableText readings = Text.empty();
				if (!info.on.isEmpty()) {
					readings.append(color("on ", TEXT_SOFT)).append(color(String.join("・", info.on) + "  ", TEXT));
				}
				if (!info.kun.isEmpty()) {
					readings.append(color("kun ", TEXT_SOFT)).append(color(String.join("・", info.kun), TEXT));
				}
				add(out, tr, readings, width, 0);
				List<String> facts = new ArrayList<>();
				if (info.st > 0) {
					facts.add(info.st + " trazos");
				}
				if (info.jlpt > 0) {
					facts.add("JLPT " + info.jlpt);
				}
				if (info.g > 0 && info.g <= 6) {
					facts.add("grado " + info.g);
				}
				if (!facts.isEmpty()) {
					add(out, tr, color(String.join(" · ", facts), TEXT_SOFT), width, 0);
				}
			}
		}

		if (!result.definitions().isEmpty()) {
			add(out, tr, header("Definición (国語辞典)"), width, 5);
			List<String> shown = new ArrayList<>();
			for (DictionaryModel.Definition definition : result.definitions()) {
				for (String d : definition.d) {
					if (shown.size() < 4) {
						shown.add(d);
					}
				}
			}
			// Traducción con DeepL debajo de cada definición, igual que en los ejemplos.
			var translator = JapanDictClient.translator();
			var config = JapanDictClient.config();
			boolean translating = !config.deeplApiKey.isBlank();
			if (translating) {
				translator.request(shown, config.deeplApiKey, config.translationLanguage);
			}
			for (int i = 0; i < shown.size(); i++) {
				String d = shown.get(i);
				add(out, tr, color("・" + d, TEXT_STRONG), width, i == 0 ? 0 : 2);
				String translation = translator.cached(d);
				if (translation != null) {
					add(out, tr, color("→ " + translation, TEXT_SOFT), width, 0);
				} else if (translating && translator.isPending(d)) {
					add(out, tr, color("→ traduciendo…", TEXT_SOFT).formatted(Formatting.ITALIC), width, 0);
				}
			}
			if (translating && translator.error() != null) {
				add(out, tr, color("Sin traducción: " + translator.error(), TEXT_SOFT), width, 2);
			}
		}

		if (!word.x.isEmpty()) {
			add(out, tr, header("Ejemplos"), width, 5);
			for (DictionaryModel.Example example : word.x) {
				add(out, tr, color(example.ja, TEXT_STRONG), width, 2);
				String translation = example.es != null ? example.es : example.en;
				if (translation != null) {
					add(out, tr, color("→ " + translation + (example.es == null ? " (en)" : ""), TEXT_SOFT), width, 0);
				}
			}
		}

		String credits = "JMdict, KANJIDIC2 (EDRDG, CC BY-SA 4.0) · Tatoeba (CC BY 2.0 FR) · Wiktionary (CC BY-SA)";
		if (!JapanDictClient.config().deeplApiKey.isBlank()) {
			credits += " · Traducción de definiciones: DeepL";
		}
		add(out, tr, color(credits, 0xFF8A8A8A),
				width, 8);
		return out;
	}

	private static void add(List<Line> out, TextRenderer tr, Text text, int width, int gapBefore) {
		boolean first = true;
		for (OrderedText line : tr.wrapLines(text, width)) {
			out.add(new Line(line, first ? gapBefore : 0));
			first = false;
		}
	}

	private static MutableText color(String text, int argb) {
		return Text.literal(text).withColor(argb & 0xFFFFFF);
	}

	private static MutableText header(String text) {
		return color(text, HEADER).formatted(Formatting.UNDERLINE);
	}

	// ---------------------------------------------------------------- entrada

	private boolean inside(double x, double y) {
		return x >= panelX && x < panelX + panelW && y >= panelY && y < panelY + panelH;
	}

	private static boolean in(int[] box, double x, double y) {
		return box != null && x >= box[0] && x < box[2] && y >= box[1] && y < box[3];
	}

	/** @return true si el clic fue dentro de la ventana (y no debe llegar a la pantalla de abajo). */
	public boolean mouseClicked(double x, double y, int button) {
		if (!visible || !inside(x, y)) {
			return false;
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return true;
		}
		if (in(leftArrow, x, y)) {
			firstTab = Math.max(0, firstTab - 1);
		} else if (in(rightArrow, x, y)) {
			List<WordCollector.Entry> current = entries;
			if (current != null && lastVisibleTab < current.size() - 1) {
				firstTab++;
			}
		} else {
			boolean onTab = false;
			for (int[] box : tabBoxes) {
				if (in(box, x, y)) {
					select(box[4]);
					onTab = true;
				}
			}
			if (!onTab) {
				dragging = true;
				dragOffsetX = x - panelX;
				dragOffsetY = y - panelY;
			}
		}
		return true;
	}

	/** Mueve la ventana mientras se arrastra; el límite de la pantalla se aplica al dibujarla. */
	public boolean mouseDragged(double x, double y) {
		if (!visible || !dragging) {
			return false;
		}
		JapanDictConfig config = JapanDictClient.config();
		config.dictionaryX = Math.max(0, (int) Math.round(x - dragOffsetX));
		config.dictionaryY = Math.max(0, (int) Math.round(y - dragOffsetY));
		return true;
	}

	/** @return true si se estaba arrastrando la ventana. */
	public boolean mouseReleased() {
		if (!dragging) {
			return false;
		}
		stopDragging();
		return true;
	}

	private void stopDragging() {
		if (dragging) {
			dragging = false;
			JapanDictConfig config = JapanDictClient.config();
			// Guardar la posición ya ajustada a la pantalla, no la que quedó fuera del borde.
			config.dictionaryX = panelX;
			config.dictionaryY = panelY;
			config.save();
		}
	}

	public boolean mouseScrolled(double x, double y, double amount) {
		if (!visible || !inside(x, y)) {
			return false;
		}
		if (y < contentY) {
			select(selected + (amount < 0 ? 1 : -1));
		} else {
			scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(amount * SCROLL_STEP)));
		}
		return true;
	}

	/** Flechas: ←/→ cambian de pestaña, ↑/↓ y RePág/AvPág desplazan. */
	public boolean keyPressed(int key) {
		if (!visible) {
			return false;
		}
		switch (key) {
			case GLFW.GLFW_KEY_LEFT -> select(selected - 1);
			case GLFW.GLFW_KEY_RIGHT -> select(selected + 1);
			case GLFW.GLFW_KEY_UP -> scroll = Math.max(0, scroll - SCROLL_STEP);
			case GLFW.GLFW_KEY_DOWN -> scroll = Math.min(maxScroll, scroll + SCROLL_STEP);
			case GLFW.GLFW_KEY_PAGE_UP -> scroll = Math.max(0, scroll - contentH);
			case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Math.min(maxScroll, scroll + contentH);
			default -> {
				return false;
			}
		}
		return true;
	}

	private void select(int index) {
		List<WordCollector.Entry> current = entries;
		if (current == null || current.isEmpty()) {
			return;
		}
		int clamped = Math.max(0, Math.min(current.size() - 1, index));
		if (clamped != selected) {
			selected = clamped;
			scroll = 0;
		}
		if (selected < firstTab) {
			firstTab = selected;
		} else if (selected > lastVisibleTab) {
			firstTab += selected - lastVisibleTab;
		}
	}
}
