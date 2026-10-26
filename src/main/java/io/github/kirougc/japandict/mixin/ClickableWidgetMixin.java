package io.github.kirougc.japandict.mixin;

import io.github.kirougc.japandict.JapanDictClient;
import io.github.kirougc.japandict.JapanDictConfig;
import io.github.kirougc.japandict.furigana.FuriganaRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Los botones centran el texto con solo 5 píxeles libres arriba, y el furigana pisa el borde.
 * Si hace falta, bajamos el texto un poco (sin pasar del borde de abajo) para que entre.
 */
@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetMixin {
	@Unique
	private static boolean japandict$shifting;

	@Shadow
	protected static void drawScrollableText(DrawContext context, TextRenderer textRenderer, Text text, int centerX,
											 int startX, int startY, int endX, int endY, int color) {
		throw new AssertionError();
	}

	@Inject(method = "drawScrollableText(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIIII)V",
			at = @At("HEAD"), cancellable = true)
	private static void japandict$makeRoomForFurigana(DrawContext context, TextRenderer textRenderer, Text text, int centerX,
													  int startX, int startY, int endX, int endY, int color, CallbackInfo ci) {
		if (japandict$shifting || JapanDictClient.config().position != JapanDictConfig.Position.ABOVE) {
			return;
		}
		int ruby = FuriganaRenderer.extraLineHeight(textRenderer, text.asOrderedText());
		if (ruby == 0) {
			return;
		}
		int textY = (startY + endY - textRenderer.fontHeight) / 2 + 1;
		int missingAbove = ruby + 1 - (textY - startY - 1);
		int roomBelow = endY - 2 - (textY + textRenderer.fontHeight - 1);
		int shift = Math.min(missingAbove, roomBelow);
		if (shift <= 0) {
			return;
		}
		japandict$shifting = true;
		try {
			drawScrollableText(context, textRenderer, text, centerX, startX, startY + shift, endX, endY + shift, color);
		} finally {
			japandict$shifting = false;
		}
		ci.cancel();
	}
}
