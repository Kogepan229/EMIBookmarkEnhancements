package net.kogepan.emi_bookmark_enhancements.input;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiIngredientKeyHelper;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.concurrent.atomic.AtomicBoolean;

public final class FavoriteScrollHandler {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static EmiBookmarkManager bookmarkManager;

    private FavoriteScrollHandler() {
    }

    public static void register(EmiBookmarkManager bookmarkManager) {
        FavoriteScrollHandler.bookmarkManager = bookmarkManager;
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.addListener(FavoriteScrollHandler::onMouseScrolledPre);
        }
    }

    private static void onMouseScrolledPre(ScreenEvent.MouseScrolled.Pre event) {
        if (bookmarkManager == null || !Screen.hasControlDown()) {
            return;
        }

        int mouseX = (int) event.getMouseX();
        int mouseY = (int) event.getMouseY();
        EmiStackInteraction hovered = EmiRuntimeAccess.getHoveredStack(mouseX, mouseY, true);
        if (hovered.isEmpty()) {
            return;
        }

        EmiIngredient ingredient = hovered.getStack();
        if (!EmiRuntimeAccess.isFavoriteIngredient(ingredient)) {
            return;
        }

        String itemKey = EmiIngredientKeyHelper.toItemKey(ingredient);
        if (itemKey.isBlank()) {
            return;
        }

        EmiBookmarkEntry.EntryType type = hovered.getRecipeContext() == null
                ? EmiBookmarkEntry.EntryType.ITEM
                : EmiBookmarkEntry.EntryType.RESULT;
        EmiBookmarkEntry entry = bookmarkManager.findEntry(ingredient);
        if (entry == null) {
            entry = bookmarkManager.findEntry(EmiBookmarkManager.DEFAULT_GROUP_ID, itemKey, type);
            if (entry == null) {
                entry = bookmarkManager.addEntry(
                        EmiBookmarkManager.DEFAULT_GROUP_ID,
                        itemKey,
                        EmiIngredientKeyHelper.toBaseAmount(ingredient),
                        type,
                        ingredient);
            } else {
                bookmarkManager.linkFavorite(ingredient, entry);
            }
        }

        double delta = event.getScrollDelta();
        if (delta == 0D) {
            return;
        }

        long step = Screen.hasAltDown() ? 64L : 1L;
        long notches = Math.max(1L, Math.round(Math.abs(delta)));
        long shift = delta > 0D ? step * notches : -step * notches;
        bookmarkManager.shiftEntryAmount(entry, shift);
        event.setCanceled(true);
    }
}
