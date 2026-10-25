package io.github.kirougc.japandict.gui;

import io.github.kirougc.japandict.JapanDictClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Pantalla transparente para usar el diccionario mientras se juega: el mundo y el HUD siguen
 * visibles detrás, y el juego no se pausa.
 */
public final class DictionaryScreen extends Screen {
	public DictionaryScreen() {
		super(Text.literal("Diccionario"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		// Sin desenfoque ni oscurecido: tiene que verse el juego.
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		DictionaryOverlay.INSTANCE.render(context, width, height);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == GLFW.GLFW_KEY_ESCAPE || JapanDictClient.dictionaryKey().matchesKey(input)) {
			close();
			return true;
		}
		return DictionaryOverlay.INSTANCE.keyPressed(input.key()) || super.keyPressed(input);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		return DictionaryOverlay.INSTANCE.mouseClicked(click.x(), click.y()) || super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		return DictionaryOverlay.INSTANCE.mouseScrolled(mouseX, mouseY, verticalAmount)
				|| super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void removed() {
		DictionaryOverlay.INSTANCE.close();
		super.removed();
	}
}
