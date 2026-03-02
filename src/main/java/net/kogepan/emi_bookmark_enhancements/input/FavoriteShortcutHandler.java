package net.kogepan.emi_bookmark_enhancements.input;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.config.EmiConfig;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiFavoritesBridge;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FavoriteShortcutHandler {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private FavoriteShortcutHandler() {
    }

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.addListener(FavoriteShortcutHandler::onKeyPressedPre);
        }
    }

    private static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() == null || !EmiConfig.favorite.matchesKey(event.getKeyCode(), event.getScanCode())) {
            return;
        }

        int mouseX = EmiRuntimeAccess.getLastMouseX();
        int mouseY = EmiRuntimeAccess.getLastMouseY();
        EmiStackInteraction hovered = EmiRuntimeAccess.getHoveredStack(mouseX, mouseY, true);
        EmiIngredient ingredient = hovered.getStack();
        if (!EmiRuntimeAccess.isRuntimeFavoriteHandle(ingredient)) {
            return;
        }

        if (EmiRuntimeAccess.removeFavoriteHandles(List.of(ingredient)) > 0) {
            EmiFavoritesBridge.synchronizeNow();
            event.setCanceled(true);
        }
    }
}
