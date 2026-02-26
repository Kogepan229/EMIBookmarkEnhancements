package net.kogepan.emi_bookmark_enhancements.integration.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.kogepan.emi_bookmark_enhancements.EmiBookmarkEnhancements;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class EmiRuntimeAccess {
    private static final String EMI_FAVORITES_CLASS = "dev.emi.emi.runtime.EmiFavorites";
    private static final String EMI_SCREEN_MANAGER_CLASS = "dev.emi.emi.screen.EmiScreenManager";
    private static final String EMI_FAVORITE_CLASS = "dev.emi.emi.runtime.EmiFavorite";
    private static final String EMI_RECIPE_SCREEN_CLASS = "dev.emi.emi.screen.RecipeScreen";
    private static final String EMI_WIDGET_GROUP_CLASS = "dev.emi.emi.screen.WidgetGroup";

    private static Field favoritesField;
    private static Field lastMouseXField;
    private static Field lastMouseYField;
    private static Method hoveredStackMethod;
    private static boolean lookupFailed;
    private static boolean unavailableLogged;
    private static boolean recipeLookupFailed;

    private static Class<?> recipeScreenClass;
    private static Class<?> widgetGroupClass;
    private static Field recipeScreenCurrentPageField;
    private static Field widgetGroupRecipeField;
    private static Method widgetGroupXMethod;
    private static Method widgetGroupYMethod;
    private static Method widgetGroupWidthMethod;
    private static Method widgetGroupHeightMethod;

    private EmiRuntimeAccess() {
    }

    public static List<Object> getFavoriteHandles() {
        if (!resolveRuntimeHandles()) {
            return List.of();
        }
        try {
            Object value = favoritesField.get(null);
            if (value instanceof List<?> list) {
                return new ArrayList<>(list);
            }
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to read EMI favorites", e);
        }
        return List.of();
    }

    public static EmiStackInteraction getHoveredStack(int mouseX, int mouseY, boolean notClick) {
        if (!resolveRuntimeHandles()) {
            return EmiStackInteraction.EMPTY;
        }
        try {
            Object value = hoveredStackMethod.invoke(null, mouseX, mouseY, notClick);
            if (value instanceof EmiStackInteraction interaction) {
                return interaction;
            }
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to read hovered EMI stack", e);
        }
        return EmiStackInteraction.EMPTY;
    }

    public static boolean isFavoriteIngredient(EmiIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        Class<?> current = ingredient.getClass();
        while (current != null) {
            if (EMI_FAVORITE_CLASS.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    public static boolean hasRecipeContext(Object favoriteHandle) {
        if (favoriteHandle == null) {
            return false;
        }
        try {
            Method getRecipe = favoriteHandle.getClass().getMethod("getRecipe");
            return getRecipe.invoke(favoriteHandle) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static int getLastMouseX() {
        if (!resolveRuntimeHandles()) {
            return 0;
        }
        try {
            return lastMouseXField.getInt(null);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static int getLastMouseY() {
        if (!resolveRuntimeHandles()) {
            return 0;
        }
        try {
            return lastMouseYField.getInt(null);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static EmiRecipe getHoveredRecipe(Object screen, int mouseX, int mouseY) {
        if (screen == null || !resolveRecipeHandles()) {
            return null;
        }
        if (!recipeScreenClass.isInstance(screen)) {
            return null;
        }
        try {
            Object value = recipeScreenCurrentPageField.get(screen);
            if (!(value instanceof List<?> groups)) {
                return null;
            }
            for (Object group : groups) {
                if (group == null || !widgetGroupClass.isInstance(group)) {
                    continue;
                }
                int x = (int) widgetGroupXMethod.invoke(group);
                int y = (int) widgetGroupYMethod.invoke(group);
                int width = (int) widgetGroupWidthMethod.invoke(group);
                int height = (int) widgetGroupHeightMethod.invoke(group);
                if (mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height) {
                    Object recipe = widgetGroupRecipeField.get(group);
                    if (recipe instanceof EmiRecipe emiRecipe) {
                        return emiRecipe;
                    }
                }
            }
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to resolve hovered recipe", e);
        }
        return null;
    }

    private static boolean resolveRuntimeHandles() {
        if (favoritesField != null && hoveredStackMethod != null && lastMouseXField != null && lastMouseYField != null) {
            return true;
        }
        if (lookupFailed) {
            return false;
        }
        try {
            Class<?> favoritesClass = Class.forName(EMI_FAVORITES_CLASS);
            Class<?> screenManagerClass = Class.forName(EMI_SCREEN_MANAGER_CLASS);
            favoritesField = favoritesClass.getField("favorites");
            hoveredStackMethod = screenManagerClass.getMethod("getHoveredStack", int.class, int.class, boolean.class);
            lastMouseXField = screenManagerClass.getField("lastMouseX");
            lastMouseYField = screenManagerClass.getField("lastMouseY");
            return true;
        } catch (Exception e) {
            lookupFailed = true;
            if (!unavailableLogged) {
                unavailableLogged = true;
                EmiBookmarkEnhancements.LOGGER.warn("EMI runtime access is unavailable; bookmark sync features are disabled.");
            }
            EmiBookmarkEnhancements.LOGGER.debug("Failed to resolve EMI runtime classes", e);
            return false;
        }
    }

    private static boolean resolveRecipeHandles() {
        if (recipeScreenClass != null
                && widgetGroupClass != null
                && recipeScreenCurrentPageField != null
                && widgetGroupRecipeField != null
                && widgetGroupXMethod != null
                && widgetGroupYMethod != null
                && widgetGroupWidthMethod != null
                && widgetGroupHeightMethod != null) {
            return true;
        }
        if (recipeLookupFailed) {
            return false;
        }
        try {
            recipeScreenClass = Class.forName(EMI_RECIPE_SCREEN_CLASS);
            widgetGroupClass = Class.forName(EMI_WIDGET_GROUP_CLASS);

            recipeScreenCurrentPageField = recipeScreenClass.getDeclaredField("currentPage");
            recipeScreenCurrentPageField.setAccessible(true);

            widgetGroupRecipeField = widgetGroupClass.getDeclaredField("recipe");
            widgetGroupRecipeField.setAccessible(true);

            widgetGroupXMethod = widgetGroupClass.getMethod("x");
            widgetGroupYMethod = widgetGroupClass.getMethod("y");
            widgetGroupWidthMethod = widgetGroupClass.getMethod("getWidth");
            widgetGroupHeightMethod = widgetGroupClass.getMethod("getHeight");
            return true;
        } catch (Exception e) {
            recipeLookupFailed = true;
            EmiBookmarkEnhancements.LOGGER.debug("Failed to resolve EMI recipe screen reflection handles", e);
            return false;
        }
    }
}
