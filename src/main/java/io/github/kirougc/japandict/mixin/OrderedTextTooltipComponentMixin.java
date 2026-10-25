package io.github.kirougc.japandict.mixin;

import io.github.kirougc.japandict.JapanDictClient;
import io.github.kirougc.japandict.JapanDictConfig;
import io.github.kirougc.japandict.furigana.FuriganaRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.tooltip.OrderedTextTooltipComponent;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cada línea de un tooltip con furigana se hace más alta (y más ancha si la lectura sobresale),
 * así el recuadro la contiene y las líneas no se pisan entre sí.
 */
@Mixin(OrderedTextTooltipComponent.class)
public abstract class OrderedTextTooltipComponentMixin {
	@Shadow
	@Final
	private OrderedText text;

	@Inject(method = "getHeight", at = @At("RETURN"), cancellable = true)
	private void japandict$roomForFurigana(TextRenderer textRenderer, CallbackInfoReturnable<Integer> cir) {
		int extra = FuriganaRenderer.extraLineHeight(textRenderer, text);
		if (extra > 0) {
			cir.setReturnValue(cir.getReturnValue() + extra);
		}
	}

	@Inject(method = "getWidth", at = @At("RETURN"), cancellable = true)
	private void japandict$widthForFurigana(TextRenderer textRenderer, CallbackInfoReturnable<Integer> cir) {
		int rubyWidth = FuriganaRenderer.rubyWidth(textRenderer, text);
		if (rubyWidth > cir.getReturnValue()) {
			cir.setReturnValue(rubyWidth);
		}
	}

	/** Con el furigana arriba, el texto baja para dejarle lugar dentro de su línea. */
	@ModifyVariable(method = "drawText", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int japandict$shiftBelowFurigana(int y) {
		if (JapanDictClient.config().position != JapanDictConfig.Position.ABOVE) {
			return y;
		}
		return y + FuriganaRenderer.extraLineHeight(MinecraftClient.getInstance().textRenderer, text);
	}
}
