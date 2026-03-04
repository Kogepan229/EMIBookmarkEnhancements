package net.kogepan.emi_bookmark_enhancements.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.registry.EmiDragDropHandlers;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerFavoritePagingMixin {
    @Shadow
    private static List<EmiScreenManager.SidebarPanel> panels;

    @Shadow
    public static EmiIngredient draggedStack;

    @Redirect(
            method = "getHoveredStack(IIZZ)Ldev/emi/emi/api/stack/EmiStackInteraction;",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/emi/emi/screen/EmiScreenManager$ScreenSpace;getRawOffset(II)I"
            )
    )
    private static int emiBookmarkEnhancements$redirectHoveredFavoriteOffset(
            EmiScreenManager.ScreenSpace space,
            int x,
            int y,
            int mouseX,
            int mouseY,
            boolean notClick,
            boolean ignoreLastHoveredCraftable
    ) {
        int localIndex = space.getRawOffset(x, y);
        EmiScreenManager.SidebarPanel panel = emiBookmarkEnhancements$findHoveredPanel(mouseX, mouseY);
        if (!emiBookmarkEnhancements$isCustomFavoriteMainSpace(panel, space) || localIndex < 0) {
            return localIndex;
        }
        return EmiRuntimeAccess.toFavoriteDisplayGlobalIndex(
                panel,
                panel.page,
                localIndex,
                localIndex + space.pageSize * panel.page);
    }

    @Redirect(
            method = "getHoveredStack(IIZZ)Ldev/emi/emi/api/stack/EmiStackInteraction;",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/emi/emi/screen/EmiScreenManager$ScreenSpace;pageSize:I",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 1
            )
    )
    private static int emiBookmarkEnhancements$skipHoveredFavoritePageOffset(
            EmiScreenManager.ScreenSpace space,
            int mouseX,
            int mouseY,
            boolean notClick,
            boolean ignoreLastHoveredCraftable
    ) {
        EmiScreenManager.SidebarPanel panel = emiBookmarkEnhancements$findHoveredPanel(mouseX, mouseY);
        if (emiBookmarkEnhancements$isCustomFavoriteMainSpace(panel, space)) {
            return 0;
        }
        return space.pageSize;
    }

    @Inject(method = "renderDraggedStack", at = @At("HEAD"), cancellable = true)
    private static void emiBookmarkEnhancements$renderDraggedFavoriteInsertionMarker(
            EmiDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            EmiScreenBase base,
            CallbackInfo ci
    ) {
        if (draggedStack.isEmpty()) {
            return;
        }

        EmiScreenManager.SidebarPanel panel = emiBookmarkEnhancements$findHoveredPanel(mouseX, mouseY);
        if (panel == null) {
            return;
        }
        EmiScreenManager.ScreenSpace space = panel.getHoveredSpace(mouseX, mouseY);
        if (!emiBookmarkEnhancements$isCustomFavoriteMainSpace(panel, space)) {
            return;
        }

        int localIndex = space.getClosestEdge(mouseX, mouseY);
        int globalIndex = EmiRuntimeAccess.toFavoriteDisplayGlobalIndex(
                panel,
                panel.page,
                localIndex,
                localIndex + space.pageSize * panel.page);
        int maxGlobalIndex = Math.min(EmiFavorites.favorites.size(), space.getStacks().size());
        globalIndex = Math.max(0, Math.min(globalIndex, maxGlobalIndex));

        int startIndex = EmiRuntimeAccess.getFavoriteDisplayStartIndex(
                panel,
                panel.page,
                Math.max(0, panel.page * Math.max(1, space.pageSize)));
        int displayLocalIndex = Math.max(0, globalIndex - startIndex);

        context.push();
        context.matrices().translate(0, 0, 200);
        int dx = space.getEdgeX(displayLocalIndex);
        int dy = space.getEdgeY(displayLocalIndex);
        context.fill(dx - 1, dy, 2, 18, 0xFF00FFFF);
        context.pop();

        context.push();
        context.matrices().translate(0, 0, 400);
        EmiDragDropHandlers.render(base.screen(), draggedStack, context.raw(), mouseX, mouseY, delta);
        draggedStack.render(context.raw(), mouseX - 8, mouseY - 8, delta, EmiIngredient.RENDER_ICON);
        context.pop();
        ci.cancel();
    }

    @Redirect(
            method = "mouseReleased",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/emi/emi/runtime/EmiFavorites;addFavoriteAt(Ldev/emi/emi/api/stack/EmiIngredient;I)V"
            )
    )
    private static void emiBookmarkEnhancements$redirectFavoriteDropOffset(
            EmiIngredient stack,
            int offset,
            double mouseX,
            double mouseY,
            int button
    ) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        EmiScreenManager.SidebarPanel panel = emiBookmarkEnhancements$findHoveredPanel(mx, my);
        if (panel == null) {
            EmiFavorites.addFavoriteAt(stack, offset);
            return;
        }

        EmiScreenManager.ScreenSpace space = panel.getHoveredSpace(mx, my);
        if (!emiBookmarkEnhancements$isCustomFavoriteMainSpace(panel, space)) {
            EmiFavorites.addFavoriteAt(stack, offset);
            return;
        }

        int localIndex = space.getClosestEdge(mx, my);
        int globalIndex = EmiRuntimeAccess.toFavoriteDisplayGlobalIndex(
                panel,
                panel.page,
                localIndex,
                offset);
        globalIndex = Math.max(0, Math.min(globalIndex, EmiFavorites.favorites.size()));
        EmiFavorites.addFavoriteAt(stack, globalIndex);
    }

    private static EmiScreenManager.SidebarPanel emiBookmarkEnhancements$findHoveredPanel(int mouseX, int mouseY) {
        if (panels == null) {
            return null;
        }
        for (EmiScreenManager.SidebarPanel panel : panels) {
            if (panel != null && panel.isVisible() && panel.getHoveredSpace(mouseX, mouseY) != null) {
                return panel;
            }
        }
        return null;
    }

    private static boolean emiBookmarkEnhancements$isCustomFavoriteMainSpace(
            EmiScreenManager.SidebarPanel panel,
            EmiScreenManager.ScreenSpace space
    ) {
        return panel != null
                && space != null
                && space == panel.space
                && space.getType() == SidebarType.FAVORITES
                && EmiRuntimeAccess.hasCustomFavoriteDisplayPlan(panel);
    }
}
