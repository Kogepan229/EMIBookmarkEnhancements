package net.kogepan.emi_bookmark_enhancements.mixin;

import dev.emi.emi.EmiRenderHelper;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import net.kogepan.emi_bookmark_enhancements.overlay.FavoriteTooltipAugmenter;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(value = EmiRenderHelper.class, remap = false)
public abstract class EmiRenderHelperTooltipMixin {
    @ModifyVariable(
            method = "drawTooltip(Lnet/minecraft/client/gui/screens/Screen;Ldev/emi/emi/runtime/EmiDrawContext;Ljava/util/List;IIILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static List<ClientTooltipComponent> emiBookmarkEnhancements$appendFavoriteTooltip(
            List<ClientTooltipComponent> components
    ) {
        return FavoriteTooltipAugmenter.appendToEmiTooltip(
                components,
                EmiRuntimeAccess.getLastMouseX(),
                EmiRuntimeAccess.getLastMouseY());
    }
}
