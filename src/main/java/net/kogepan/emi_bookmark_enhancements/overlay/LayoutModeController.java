package net.kogepan.emi_bookmark_enhancements.overlay;

import net.kogepan.emi_bookmark_enhancements.bookmark.persistence.LayoutStore;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LayoutModeController {
    public enum LayoutMode {
        HORIZONTAL,
        VERTICAL;

        public LayoutMode toggled() {
            return this == HORIZONTAL ? VERTICAL : HORIZONTAL;
        }
    }

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private static LayoutStore store;
    private static LayoutMode currentMode = LayoutMode.HORIZONTAL;
    private static boolean loaded;
    private static boolean dirty;

    private LayoutModeController() {
    }

    public static void register(LayoutStore layoutStore) {
        store = layoutStore;
        ensureLoaded();
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.addListener(LayoutModeController::onMouseButtonPressedPre);
        }
    }

    public static LayoutMode getCurrentMode() {
        ensureLoaded();
        return currentMode;
    }

    public static boolean isVerticalMode() {
        return getCurrentMode() == LayoutMode.VERTICAL;
    }

    public static void setCurrentMode(LayoutMode mode) {
        ensureLoaded();
        LayoutMode safeMode = mode == null ? LayoutMode.HORIZONTAL : mode;
        if (safeMode == currentMode) {
            EmiRuntimeAccess.applyVerticalFavoritesRowPolicy(safeMode == LayoutMode.VERTICAL);
            return;
        }
        currentMode = safeMode;
        dirty = true;
        EmiRuntimeAccess.applyVerticalFavoritesRowPolicy(safeMode == LayoutMode.VERTICAL);
        EmiRuntimeAccess.refreshFavoritesSidebar();
    }

    public static void toggleMode() {
        setCurrentMode(getCurrentMode().toggled());
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        LayoutMode mode = LayoutMode.HORIZONTAL;
        if (store != null) {
            mode = parseMode(store.loadModeName());
        }
        currentMode = mode;
        loaded = true;
        dirty = false;
        EmiRuntimeAccess.applyVerticalFavoritesRowPolicy(mode == LayoutMode.VERTICAL);
    }

    public static void save() {
        ensureLoaded();
        if (!dirty || store == null) {
            return;
        }
        if (store.saveModeName(currentMode.name())) {
            dirty = false;
        }
    }

    public static boolean isDirty() {
        return dirty;
    }

    private static void onMouseButtonPressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getScreen() == null) {
            return;
        }
        if (!EmiRuntimeAccess.isFavoriteHeaderLayoutToggleTarget((int) event.getMouseX(), (int) event.getMouseY())) {
            return;
        }

        toggleMode();
        save();
        event.setCanceled(true);
    }

    private static LayoutMode parseMode(String modeName) {
        if (modeName == null || modeName.isBlank()) {
            return LayoutMode.HORIZONTAL;
        }
        try {
            return LayoutMode.valueOf(modeName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LayoutMode.HORIZONTAL;
        }
    }
}
