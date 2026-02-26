package net.kogepan.emi_bookmark_enhancements.integration.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EmiFavoritesBridge {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private static EmiBookmarkManager bookmarkManager;
    private static long lastSignature = Long.MIN_VALUE;

    private EmiFavoritesBridge() {
    }

    public static void initialize(EmiBookmarkManager manager) {
        bookmarkManager = manager;
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.addListener(EmiFavoritesBridge::onClientTick);
        }
        synchronizeFromEmi();
    }

    public static EmiBookmarkManager getBookmarkManager() {
        return bookmarkManager;
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        synchronizeFromEmi();
    }

    private static void synchronizeFromEmi() {
        if (bookmarkManager == null) {
            return;
        }

        List<Object> favorites = EmiRuntimeAccess.getFavoriteHandles();
        long signature = computeSignature(favorites);
        if (signature == lastSignature) {
            return;
        }
        lastSignature = signature;

        List<EmiBookmarkManager.FavoriteHandleData> synchronizedFavorites = toFavoriteHandleData(favorites);
        EmiBookmarkManager.FavoriteSyncResult syncResult = bookmarkManager.synchronizeFavorites(synchronizedFavorites);
        if (!syncResult.handlesToPrune().isEmpty()) {
            int removed = EmiRuntimeAccess.removeFavoriteHandles(syncResult.handlesToPrune());
            if (removed > 0) {
                List<Object> refreshedFavorites = EmiRuntimeAccess.getFavoriteHandles();
                lastSignature = computeSignature(refreshedFavorites);
                bookmarkManager.synchronizeFavorites(toFavoriteHandleData(refreshedFavorites));
            }
        }
    }

    private static List<EmiBookmarkManager.FavoriteHandleData> toFavoriteHandleData(List<Object> favorites) {
        List<EmiBookmarkManager.FavoriteHandleData> synchronizedFavorites = new ArrayList<>(favorites.size());
        for (Object favoriteHandle : favorites) {
            if (!(favoriteHandle instanceof EmiIngredient ingredient) || ingredient.isEmpty()) {
                continue;
            }

            String itemKey = EmiIngredientKeyHelper.toItemKey(ingredient);
            if (itemKey.isBlank()) {
                continue;
            }

            long factor = EmiIngredientKeyHelper.toBaseAmount(ingredient);
            EmiBookmarkEntry.EntryType type = EmiRuntimeAccess.hasRecipeContext(favoriteHandle)
                    ? EmiBookmarkEntry.EntryType.RESULT
                    : EmiBookmarkEntry.EntryType.ITEM;
            synchronizedFavorites.add(new EmiBookmarkManager.FavoriteHandleData(
                    favoriteHandle, itemKey, factor, type));
        }
        return synchronizedFavorites;
    }

    private static long computeSignature(List<Object> favorites) {
        long signature = favorites.size();
        for (Object favorite : favorites) {
            signature = signature * 31L + System.identityHashCode(favorite);
        }
        return signature;
    }
}
