package net.kogepan.emi_bookmark_enhancements.mixin;

import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import net.kogepan.emi_bookmark_enhancements.overlay.LayoutModeController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.SidebarPanel.class, remap = false)
public abstract class EmiSidebarPanelPagingMixin {
    @Shadow
    public EmiScreenManager.ScreenSpace space;

    @Shadow
    public int page;

    @Shadow
    public abstract SidebarType getType();

    @Inject(method = "wrapPage", at = @At("HEAD"), cancellable = true)
    private void emiBookmarkEnhancements$wrapPageCustom(CallbackInfo ci) {
        if (!emiBookmarkEnhancements$useCustomFavoritePaging()) {
            return;
        }
        int totalPages = emiBookmarkEnhancements$totalPagesFallback();
        totalPages = EmiRuntimeAccess.getFavoriteDisplayTotalPages(this, totalPages);
        if (totalPages <= 0) {
            totalPages = 1;
        }
        int nextPage = page;
        if (nextPage >= totalPages) {
            nextPage = 0;
        } else if (nextPage < 0) {
            nextPage = totalPages - 1;
        }
        if (nextPage != page) {
            page = nextPage;
            if (space != null) {
                space.batcher.repopulate();
            }
        }
        ci.cancel();
    }

    @Inject(method = "hasMultiplePages", at = @At("HEAD"), cancellable = true)
    private void emiBookmarkEnhancements$hasMultiplePages(CallbackInfoReturnable<Boolean> cir) {
        if (!emiBookmarkEnhancements$useCustomFavoritePaging()) {
            return;
        }
        boolean fallback = space != null && space.getStacks().size() > space.pageSize;
        cir.setReturnValue(EmiRuntimeAccess.hasFavoriteDisplayMultiplePages(this, fallback));
    }

    @Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
    private void emiBookmarkEnhancements$scrollCustom(int delta, CallbackInfo ci) {
        if (!emiBookmarkEnhancements$useCustomFavoritePaging()) {
            return;
        }
        if (space == null || space.pageSize == 0) {
            ci.cancel();
            return;
        }
        int totalPages = EmiRuntimeAccess.getFavoriteDisplayTotalPages(this, emiBookmarkEnhancements$totalPagesFallback());
        if (totalPages <= 1) {
            ci.cancel();
            return;
        }
        int nextPage = page + delta;
        if (nextPage >= totalPages) {
            nextPage = 0;
        } else if (nextPage < 0) {
            nextPage = totalPages - 1;
        }
        if (nextPage != page) {
            page = nextPage;
            space.batcher.repopulate();
        }
        ci.cancel();
    }

    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Ldev/emi/emi/screen/EmiScreenManager$SidebarPanel;drawHeader(Ldev/emi/emi/runtime/EmiDrawContext;IIFII)V"),
            index = 5
    )
    private int emiBookmarkEnhancements$overrideTotalPages(int originalTotalPages) {
        if (!emiBookmarkEnhancements$useCustomFavoritePaging()) {
            return originalTotalPages;
        }
        return EmiRuntimeAccess.getFavoriteDisplayTotalPages(this, originalTotalPages);
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Ldev/emi/emi/screen/EmiScreenManager$ScreenSpace;render(Ldev/emi/emi/runtime/EmiDrawContext;IIFI)V")
    )
    private void emiBookmarkEnhancements$redirectScreenSpaceRender(
            EmiScreenManager.ScreenSpace screenSpace,
            EmiDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            int startIndex
    ) {
        int resolvedStartIndex = startIndex;
        if (emiBookmarkEnhancements$useCustomFavoritePaging() && screenSpace == this.space) {
            int clampedPage = EmiRuntimeAccess.clampFavoriteDisplayPage(this, page);
            if (clampedPage != page) {
                page = clampedPage;
            }
            resolvedStartIndex = EmiRuntimeAccess.getFavoriteDisplayStartIndex(this, page, startIndex);
        }
        screenSpace.render(context, mouseX, mouseY, delta, resolvedStartIndex);
    }

    private boolean emiBookmarkEnhancements$useCustomFavoritePaging() {
        return LayoutModeController.isVerticalMode()
                && space != null
                && getType() == SidebarType.FAVORITES;
    }

    private int emiBookmarkEnhancements$totalPagesFallback() {
        if (space == null || space.pageSize <= 0) {
            return 1;
        }
        int stackCount = space.getStacks().size();
        return Math.max(1, (stackCount - 1) / space.pageSize + 1);
    }
}
