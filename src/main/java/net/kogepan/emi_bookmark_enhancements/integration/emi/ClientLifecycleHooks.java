package net.kogepan.emi_bookmark_enhancements.integration.emi;

import net.kogepan.emi_bookmark_enhancements.bookmark.persistence.BookmarkStore;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.kogepan.emi_bookmark_enhancements.input.FavoriteScrollHandler;
import net.kogepan.emi_bookmark_enhancements.input.RecipeShortcutHandler;
import net.kogepan.emi_bookmark_enhancements.overlay.FavoriteQuantityOverlay;
import net.kogepan.emi_bookmark_enhancements.overlay.FavoriteTooltipAugmenter;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientLifecycleHooks {
    private static final EmiBookmarkManager BOOKMARK_MANAGER = new EmiBookmarkManager(new BookmarkStore());
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean RUNTIME_EVENTS_REGISTERED = new AtomicBoolean(false);
    private static final int AUTOSAVE_TICK_INTERVAL = 100;
    private static int autosaveTickCounter;

    private ClientLifecycleHooks() {
    }

    public static EmiBookmarkManager getBookmarkManager() {
        return BOOKMARK_MANAGER;
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BOOKMARK_MANAGER.ensureLoaded();
            EmiFavoritesBridge.initialize(BOOKMARK_MANAGER);
            FavoriteScrollHandler.register(BOOKMARK_MANAGER);
            RecipeShortcutHandler.register(BOOKMARK_MANAGER);
            FavoriteQuantityOverlay.register(BOOKMARK_MANAGER);
            FavoriteTooltipAugmenter.register(BOOKMARK_MANAGER);
            registerRuntimeEvents();
        });
        registerShutdownHook();
    }

    private static void registerShutdownHook() {
        if (!SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(BOOKMARK_MANAGER::save,
                "emi-bookmark-enhancements-save"));
    }

    private static void registerRuntimeEvents() {
        if (!RUNTIME_EVENTS_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(ClientLifecycleHooks::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientLifecycleHooks::onClientLoggingOut);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        autosaveTickCounter++;
        if (autosaveTickCounter < AUTOSAVE_TICK_INTERVAL) {
            return;
        }
        autosaveTickCounter = 0;

        if (BOOKMARK_MANAGER.isDirty()) {
            BOOKMARK_MANAGER.save();
        }
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BOOKMARK_MANAGER.save();
    }
}
