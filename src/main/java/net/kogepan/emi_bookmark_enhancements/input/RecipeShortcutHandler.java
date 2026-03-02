package net.kogepan.emi_bookmark_enhancements.input;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import net.kogepan.emi_bookmark_enhancements.recipe.RecipeFavoriteHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RecipeShortcutHandler {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static EmiBookmarkManager bookmarkManager;

    private RecipeShortcutHandler() {
    }

    public static void register(EmiBookmarkManager manager) {
        bookmarkManager = manager;
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.addListener(RecipeShortcutHandler::onKeyPressedPre);
        }
    }

    private static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (bookmarkManager == null || event.getKeyCode() != GLFW.GLFW_KEY_A || !Screen.hasShiftDown()) {
            return;
        }
        if (event.getScreen() == null || !event.getScreen().getClass().getName().equals("dev.emi.emi.screen.RecipeScreen")) {
            return;
        }

        int mouseX = EmiRuntimeAccess.getLastMouseX();
        int mouseY = EmiRuntimeAccess.getLastMouseY();
        EmiIngredient hoveredResultCandidate = EmiRuntimeAccess.getHoveredRecipeStack(event.getScreen());
        EmiStackInteraction hovered = EmiRuntimeAccess.getHoveredStack(mouseX, mouseY, true);
        EmiRecipe recipe = hovered.getRecipeContext();
        if (hoveredResultCandidate.isEmpty()) {
            hoveredResultCandidate = hovered.getStack();
        }
        if (recipe == null) {
            recipe = EmiRuntimeAccess.getHoveredRecipe(event.getScreen(), mouseX, mouseY);
        }
        EmiStackInteraction clickHovered = EmiStackInteraction.EMPTY;
        if (recipe == null || hoveredResultCandidate.isEmpty()) {
            clickHovered = EmiRuntimeAccess.getHoveredStack(mouseX, mouseY, false);
            if (recipe == null) {
                recipe = clickHovered.getRecipeContext();
            }
            if (hoveredResultCandidate.isEmpty()) {
                hoveredResultCandidate = clickHovered.getStack();
            }
        }
        if (recipe == null) {
            return;
        }

        boolean createNewGroup = Screen.hasControlDown();
        if (RecipeFavoriteHelper.addRecipeToFavorites(recipe, hoveredResultCandidate, createNewGroup, bookmarkManager)) {
            bookmarkManager.save();
            event.setCanceled(true);
        }
    }
}
