package io.github.kirougc.japandict.mixin;

import io.github.kirougc.japandict.furigana.FuriganaRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Todo el texto de la interfaz (menús, inventarios, tooltips, HUD, chat) termina en este drawText.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
	@Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V", at = @At("HEAD"))
	private void japandict$drawFurigana(TextRenderer textRenderer, OrderedText text, int x, int y, int color,
										boolean shadow, CallbackInfo ci) {
		FuriganaRenderer.render((DrawContext) (Object) this, textRenderer, text, x, y, color, shadow);
	}
}
