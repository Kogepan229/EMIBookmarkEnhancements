package net.kogepan.emi_bookmark_enhancements.overlay;

import dev.emi.emi.api.stack.EmiIngredient;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.concurrent.atomic.AtomicBoolean;

public final class FavoriteQuantityOverlay {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static EmiBookmarkManager bookmarkManager;

    private FavoriteQuantityOverlay() {
    }

    public static void register(EmiBookmarkManager bookmarkManager) {
        FavoriteQuantityOverlay.bookmarkManager = bookmarkManager;
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.addListener(FavoriteQuantityOverlay::onRenderPost);
        }
    }

    private static void onRenderPost(ScreenEvent.Render.Post event) {
        if (bookmarkManager == null) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        for (EmiRuntimeAccess.FavoriteSlot slot : EmiRuntimeAccess.getVisibleFavoriteSlots()) {
            EmiBookmarkEntry entry = bookmarkManager.findEntry(slot.handle());
            if (entry == null) {
                continue;
            }

            boolean fluid = isFluid(slot.handle());
            String amountText = Screen.hasShiftDown()
                    ? QuantityTextHelper.formatFull(entry.getAmount())
                    : QuantityTextHelper.formatCompact(entry.getAmount());
            int x = fluid ? slot.x() : slot.x() + 16 - font.width(amountText);
            int y = slot.y() + 9;
            int color = switch (entry.getType()) {
                case RESULT -> 0xFFF4C255;
                case INGREDIENT -> 0xFF7ED3FF;
                case ITEM -> 0xFFFFFFFF;
            };
            event.getGuiGraphics().drawString(font, amountText, x, y, color, true);
        }
    }

    private static boolean isFluid(Object handle) {
        if (!(handle instanceof EmiIngredient ingredient) || ingredient.getEmiStacks().isEmpty()) {
            return false;
        }
        return ingredient.getEmiStacks().get(0).getClass().getName().endsWith("FluidEmiStack");
    }
}
