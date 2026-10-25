package io.github.kirougc.japandict;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.kirougc.japandict.dictionary.DictionaryManager;
import io.github.kirougc.japandict.furigana.ReadingEngine;
import io.github.kirougc.japandict.gui.DictionaryOverlay;
import io.github.kirougc.japandict.gui.DictionaryScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class JapanDictClient implements ClientModInitializer {
	public static final String MOD_ID = "japandict";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ReadingEngine READINGS = new ReadingEngine();

	private static JapanDictConfig config = new JapanDictConfig();

	public static JapanDictConfig config() {
		return config;
	}

	/** El mod solo hace algo con el juego en japonés. */
	public static boolean isActive() {
		if (!config.enabled || config.position == JapanDictConfig.Position.HIDDEN) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.getLanguageManager() != null
				&& "ja_jp".equals(client.getLanguageManager().getLanguage());
	}

	private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
	private static KeyBinding cycleFurigana;
	private static KeyBinding dictionaryKey;

	public static KeyBinding dictionaryKey() {
		return dictionaryKey;
	}

	@Override
	public void onInitializeClient() {
		config = JapanDictConfig.load();
		cycleFurigana = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.japandict.cycle_furigana", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
		// Por defecto la tecla a la derecha de la Ñ y la {, que en el teclado latinoamericano es "}".
		dictionaryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.japandict.dictionary", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY));
		registerDictionaryHooks();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (dictionaryKey.wasPressed()) {
				if (isActive() && client.currentScreen == null) {
					DictionaryOverlay.INSTANCE.open();
					client.setScreen(new DictionaryScreen());
				}
			}
			while (cycleFurigana.wasPressed()) {
				config.position = config.position.next();
				config.save();
				if (client.player != null) {
					client.player.sendMessage(Text.translatable("message.japandict.furigana."
							+ config.position.name().toLowerCase(java.util.Locale.ROOT)), true);
				}
			}
		});
		READINGS.setOverrides(loadOverrides());
		DictionaryManager.start(config.dictionaryUrl, config.downloadDictionary);
		long start = System.nanoTime();
		READINGS.initAsync().whenComplete((ignored, error) -> {
			if (error != null) {
				LOGGER.error("No se pudo cargar el diccionario de Kuromoji", error);
			} else {
				LOGGER.info("Kuromoji listo en {} ms", (System.nanoTime() - start) / 1_000_000);
			}
		});
	}

	/** El diccionario también se abre encima de inventarios, cofres, aldeanos, etc., sin cerrarlos. */
	private static void registerDictionaryHooks() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof DictionaryScreen) {
				return;
			}
			DictionaryOverlay overlay = DictionaryOverlay.INSTANCE;
			ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) ->
					overlay.render(context, s.width, s.height));
			ScreenEvents.remove(screen).register(s -> overlay.close());
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, input) -> {
				if (s.getFocused() instanceof TextFieldWidget) {
					return true; // escribiendo en el chat o en un buscador
				}
				if (isActive() && dictionaryKey.matchesKey(input)) {
					overlay.toggle();
					return false;
				}
				if (overlay.isVisible() && input.key() == GLFW.GLFW_KEY_ESCAPE) {
					overlay.close();
					return false;
				}
				return !overlay.keyPressed(input.key());
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !overlay.mouseClicked(click.x(), click.y()));
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) ->
					!overlay.mouseScrolled(mouseX, mouseY, vertical));
		});
	}

	/**
	 * Correcciones de lecturas: las del mod y, encima, las del jugador en
	 * config/japandict-overrides.json ({"鍛冶": "かじ"}).
	 */
	private static Map<String, String> loadOverrides() {
		Gson gson = new Gson();
		var type = new TypeToken<Map<String, String>>() {}.getType();
		Map<String, String> result = new HashMap<>();
		try (InputStream in = JapanDictClient.class.getResourceAsStream("/assets/japandict/furigana_overrides.json")) {
			if (in != null) {
				Map<String, String> builtIn = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
				result.putAll(builtIn);
			}
		} catch (Exception e) {
			LOGGER.warn("No se pudieron leer las correcciones de furigana del mod", e);
		}
		Path user = FabricLoader.getInstance().getConfigDir().resolve("japandict-overrides.json");
		if (Files.exists(user)) {
			try (Reader reader = Files.newBufferedReader(user, StandardCharsets.UTF_8)) {
				Map<String, String> custom = gson.fromJson(reader, type);
				if (custom != null) {
					result.putAll(custom);
				}
			} catch (Exception e) {
				LOGGER.warn("No se pudo leer {}", user, e);
			}
		}
		return result;
	}
}
