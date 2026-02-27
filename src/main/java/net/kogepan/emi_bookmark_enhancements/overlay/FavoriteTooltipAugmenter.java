package net.kogepan.emi_bookmark_enhancements.overlay;

import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiIngredientKeyHelper;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

public final class FavoriteTooltipAugmenter {
    private static EmiBookmarkManager bookmarkManager;

    private FavoriteTooltipAugmenter() {
    }

    public static void register(EmiBookmarkManager bookmarkManager) {
        FavoriteTooltipAugmenter.bookmarkManager = bookmarkManager;
    }

    public static List<ClientTooltipComponent> appendToEmiTooltip(List<ClientTooltipComponent> components, int mouseX, int mouseY) {
        if (components == null) {
            return List.of();
        }
        if (bookmarkManager == null) {
            return components;
        }

        EmiStackInteraction hovered = EmiRuntimeAccess.getHoveredStack(mouseX, mouseY, true);
        if (hovered.isEmpty() || !EmiRuntimeAccess.isFavoriteIngredient(hovered.getStack())) {
            return components;
        }

        EmiBookmarkEntry entry = bookmarkManager.findEntry(hovered.getStack());
        if (entry == null) {
            String itemKey = EmiIngredientKeyHelper.toItemKey(hovered.getStack());
            if (!itemKey.isBlank()) {
                EmiBookmarkEntry.EntryType type = EmiBookmarkEntry.EntryType.ITEM;
                entry = bookmarkManager.findEntry(EmiBookmarkManager.DEFAULT_GROUP_ID, itemKey, type);
            }
        }
        if (entry == null) {
            return components;
        }

        List<ClientTooltipComponent> augmented = new ArrayList<>(components);
        String quantity = Screen.hasShiftDown()
                ? QuantityTextHelper.formatFull(entry.getAmount())
                : QuantityTextHelper.formatCompact(entry.getAmount());
        augmented.add(EmiTooltipComponents.of(
                Component.translatable("tooltip.emi_bookmark_enhancements.quantity", quantity)
                        .withStyle(ChatFormatting.GRAY)));

        if (Screen.hasShiftDown()) {
            augmented.add(EmiTooltipComponents.of(
                    Component.translatable("tooltip.emi_bookmark_enhancements.quantity_breakdown",
                                    QuantityTextHelper.formatStackBreakdown(entry.getAmount()))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (Screen.hasAltDown()) {
            augmented.add(EmiTooltipComponents.of(
                    Component.translatable("tooltip.emi_bookmark_enhancements.action.ctrl_scroll")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_AQUA))));
            augmented.add(EmiTooltipComponents.of(
                    Component.translatable("tooltip.emi_bookmark_enhancements.action.ctrl_alt_scroll")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_AQUA))));
        } else {
            augmented.add(EmiTooltipComponents.of(
                    Component.translatable("tooltip.emi_bookmark_enhancements.hold_alt")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        return augmented;
    }
}
