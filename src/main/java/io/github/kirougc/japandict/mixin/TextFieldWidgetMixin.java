package io.github.kirougc.japandict.mixin;

import io.github.kirougc.japandict.furigana.FuriganaRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** En el buscador del inventario creativo (検索…) el furigana pisaba el borde de la caja: lo subimos. */
@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetMixin {
	@Inject(method = "renderWidget", at = @At("HEAD"))
	private void japandict$raiseFurigana(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (MinecraftClient.getInstance().currentScreen instanceof CreativeInventoryScreen) {
			FuriganaRenderer.setExtraRaise(2);
		}
	}

	@Inject(method = "renderWidget", at = @At("RETURN"))
	private void japandict$resetFurigana(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		FuriganaRenderer.setExtraRaise(0);
	}
}
