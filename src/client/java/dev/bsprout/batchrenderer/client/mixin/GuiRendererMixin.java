package dev.bsprout.batchrenderer.client.mixin;

import dev.bsprout.batchrenderer.client.BRender;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;draw()V", shift = At.Shift.AFTER))
    private void batchrenderer$afterDraw(CallbackInfo ci) {
        BRender.flushAll();
    }
}
