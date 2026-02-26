package net.kogepan.emi_bookmark_enhancements.overlay;

import dev.emi.emi.api.stack.EmiStackInteraction;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiIngredientKeyHelper;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FavoriteTooltipAugmenter {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static EmiBookmarkManager bookmarkManager;

    private FavoriteTooltipAugmenter() {
    }

    public static void register(EmiBookmarkManager bookmarkManager) {
        FavoriteTooltipAugmenter.bookmarkManager = bookmarkManager;
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.addListener(FavoriteTooltipAugmenter::onRenderPost);
        }
    }

    private static void onRenderPost(ScreenEvent.Render.Post event) {
        if (bookmarkManager == null) {
            return;
        }

        EmiStackInteraction hovered = EmiRuntimeAccess.getHoveredStack(event.getMouseX(), event.getMouseY(), true);
        if (hovered.isEmpty() || !EmiRuntimeAccess.isFavoriteIngredient(hovered.getStack())) {
            return;
        }

        EmiBookmarkEntry entry = bookmarkManager.findEntry(hovered.getStack());
        if (entry == null) {
            String itemKey = EmiIngredientKeyHelper.toItemKey(hovered.getStack());
            if (!itemKey.isBlank()) {
                EmiBookmarkEntry.EntryType type = hovered.getRecipeContext() == null
                        ? EmiBookmarkEntry.EntryType.ITEM
                        : EmiBookmarkEntry.EntryType.RESULT;
                entry = bookmarkManager.findEntry(EmiBookmarkManager.DEFAULT_GROUP_ID, itemKey, type);
            }
        }
        if (entry == null) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        String quantity = Screen.hasShiftDown()
                ? QuantityTextHelper.formatFull(entry.getAmount())
                : QuantityTextHelper.formatCompact(entry.getAmount());
        lines.add(Component.translatable("tooltip.emi_bookmark_enhancements.quantity", quantity)
                .withStyle(ChatFormatting.GRAY));

        if (Screen.hasShiftDown()) {
            lines.add(Component.translatable("tooltip.emi_bookmark_enhancements.quantity_breakdown",
                            QuantityTextHelper.formatStackBreakdown(entry.getAmount()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        if (Screen.hasAltDown()) {
            lines.add(Component.translatable("tooltip.emi_bookmark_enhancements.action.ctrl_scroll")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_AQUA)));
            lines.add(Component.translatable("tooltip.emi_bookmark_enhancements.action.ctrl_alt_scroll")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_AQUA)));
        } else {
            lines.add(Component.translatable("tooltip.emi_bookmark_enhancements.hold_alt")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        event.getGuiGraphics().renderTooltip(
                Minecraft.getInstance().font,
                lines,
                Optional.empty(),
                event.getMouseX() + 12,
                event.getMouseY() + 12);
    }
}
