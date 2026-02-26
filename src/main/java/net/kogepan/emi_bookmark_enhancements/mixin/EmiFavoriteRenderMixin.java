package net.kogepan.emi_bookmark_enhancements.mixin;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiFavorite;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmiFavorite.class, remap = false)
public abstract class EmiFavoriteRenderMixin {
    @Shadow
    @Final
    protected EmiIngredient stack;

    @Shadow
    @Final
    protected EmiRecipe recipe;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void emiBookmarkEnhancements$renderWithoutAmount(
            GuiGraphics raw,
            int x,
            int y,
            float delta,
            int flags,
            CallbackInfo ci
    ) {
        EmiDrawContext context = EmiDrawContext.wrap(raw);
        int filteredFlags = flags & (~EmiIngredient.RENDER_AMOUNT);
        stack.render(context.raw(), x, y, delta, filteredFlags);
        if ((flags & EmiIngredient.RENDER_INGREDIENT) > 0 && recipe != null) {
            EmiRenderHelper.renderRecipeFavorite(stack, context, x, y);
        }
        ci.cancel();
    }
}
