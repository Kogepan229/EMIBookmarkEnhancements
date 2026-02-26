package net.kogepan.emi_bookmark_enhancements.integration.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.kogepan.emi_bookmark_enhancements.EmiBookmarkEnhancements;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EmiRuntimeAccess {
    private static final String EMI_FAVORITES_CLASS = "dev.emi.emi.runtime.EmiFavorites";
    private static final String EMI_SCREEN_MANAGER_CLASS = "dev.emi.emi.screen.EmiScreenManager";
    private static final String EMI_FAVORITE_CLASS = "dev.emi.emi.runtime.EmiFavorite";
    private static final String EMI_RECIPE_SCREEN_CLASS = "dev.emi.emi.screen.RecipeScreen";
    private static final String EMI_WIDGET_GROUP_CLASS = "dev.emi.emi.screen.WidgetGroup";
    private static final String EMI_SIDEBAR_PANEL_CLASS = "dev.emi.emi.screen.EmiScreenManager$SidebarPanel";
    private static final String EMI_SCREEN_SPACE_CLASS = "dev.emi.emi.screen.EmiScreenManager$ScreenSpace";
    private static final String EMI_SIDEBAR_TYPE_CLASS = "dev.emi.emi.config.SidebarType";
    private static final String EMI_PERSISTENT_DATA_CLASS = "dev.emi.emi.runtime.EmiPersistentData";

    private static Field favoritesField;
    private static Field lastMouseXField;
    private static Field lastMouseYField;
    private static Field panelsField;
    private static Method hoveredStackMethod;
    private static boolean lookupFailed;
    private static boolean unavailableLogged;
    private static boolean recipeLookupFailed;
    private static boolean sidebarLookupFailed;
    private static boolean mutationLookupFailed;

    private static Class<?> recipeScreenClass;
    private static Class<?> widgetGroupClass;
    private static Field recipeScreenCurrentPageField;
    private static Field widgetGroupRecipeField;
    private static Method widgetGroupXMethod;
    private static Method widgetGroupYMethod;
    private static Method widgetGroupWidthMethod;
    private static Method widgetGroupHeightMethod;

    private static Class<?> sidebarPanelClass;
    private static Class<?> screenSpaceClass;
    private static Field sidebarPanelPageField;
    private static Field sidebarPanelSpaceField;
    private static Method sidebarPanelIsVisibleMethod;
    private static Method sidebarPanelGetSpacesMethod;
    private static Field screenSpacePageSizeField;
    private static Method screenSpaceGetTypeMethod;
    private static Method screenSpaceGetStacksMethod;
    private static Method screenSpaceGetRawXMethod;
    private static Method screenSpaceGetRawYMethod;
    private static Method repopulatePanelsMethod;
    private static Method persistentSaveMethod;
    private static Object favoritesSidebarType;

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

    public static List<FavoriteSlot> getVisibleFavoriteSlots() {
        if (!resolveSidebarHandles()) {
            return List.of();
        }
        try {
            Object value = panelsField.get(null);
            if (!(value instanceof List<?> panels)) {
                return List.of();
            }
            List<FavoriteSlot> slots = new ArrayList<>();
            for (Object panel : panels) {
                if (panel == null || !sidebarPanelClass.isInstance(panel)) {
                    continue;
                }
                boolean isVisible = (boolean) sidebarPanelIsVisibleMethod.invoke(panel);
                if (!isVisible) {
                    continue;
                }

                int page = sidebarPanelPageField.getInt(panel);
                Object mainSpace = sidebarPanelSpaceField.get(panel);
                Object spacesValue = sidebarPanelGetSpacesMethod.invoke(panel);
                if (!(spacesValue instanceof List<?> spaces)) {
                    continue;
                }

                for (Object space : spaces) {
                    if (space == null || !screenSpaceClass.isInstance(space)) {
                        continue;
                    }
                    Object type = screenSpaceGetTypeMethod.invoke(space);
                    if (type == null || !Objects.equals(type.toString(), "FAVORITES")) {
                        continue;
                    }
                    int pageSize = screenSpacePageSizeField.getInt(space);
                    if (pageSize <= 0) {
                        continue;
                    }

                    Object stacksValue = screenSpaceGetStacksMethod.invoke(space);
                    if (!(stacksValue instanceof List<?> stacks) || stacks.isEmpty()) {
                        continue;
                    }

                    int startIndex = space == mainSpace ? Math.max(0, page) * pageSize : 0;
                    if (startIndex >= stacks.size()) {
                        continue;
                    }
                    int endIndex = Math.min(startIndex + pageSize, stacks.size());
                    for (int i = startIndex; i < endIndex; i++) {
                        int localIndex = i - startIndex;
                        int x = (int) screenSpaceGetRawXMethod.invoke(space, localIndex);
                        int y = (int) screenSpaceGetRawYMethod.invoke(space, localIndex);
                        slots.add(new FavoriteSlot(stacks.get(i), x + 1, y + 1));
                    }
                }
            }
            return slots;
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to resolve visible favorite slots", e);
            return List.of();
        }
    }

    public static int removeFavoriteHandles(List<Object> handles) {
        if (handles == null || handles.isEmpty() || !resolveFavoriteMutationHandles()) {
            return 0;
        }
        SetByIdentity<Object> toRemove = SetByIdentity.of(handles);
        int removedCount = 0;
        try {
            Object value = favoritesField.get(null);
            if (!(value instanceof List<?> list)) {
                return 0;
            }
            @SuppressWarnings("unchecked")
            List<Object> favorites = (List<Object>) list;
            for (int i = favorites.size() - 1; i >= 0; i--) {
                if (toRemove.contains(favorites.get(i))) {
                    favorites.remove(i);
                    removedCount++;
                }
            }
            if (removedCount > 0) {
                try {
                    persistentSaveMethod.invoke(null);
                } catch (Exception e) {
                    EmiBookmarkEnhancements.LOGGER.debug("Failed to save EMI persistent data after favorite prune", e);
                }
                try {
                    repopulatePanelsMethod.invoke(null, favoritesSidebarType);
                } catch (Exception e) {
                    EmiBookmarkEnhancements.LOGGER.debug("Failed to refresh EMI favorites after favorite prune", e);
                }
            }
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to remove EMI favorite handles", e);
            return 0;
        }
        return removedCount;
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

    private static boolean resolveSidebarHandles() {
        if (!resolveRuntimeHandles()) {
            return false;
        }
        if (sidebarPanelClass != null
                && screenSpaceClass != null
                && panelsField != null
                && sidebarPanelPageField != null
                && sidebarPanelSpaceField != null
                && sidebarPanelIsVisibleMethod != null
                && sidebarPanelGetSpacesMethod != null
                && screenSpacePageSizeField != null
                && screenSpaceGetTypeMethod != null
                && screenSpaceGetStacksMethod != null
                && screenSpaceGetRawXMethod != null
                && screenSpaceGetRawYMethod != null) {
            return true;
        }
        if (sidebarLookupFailed) {
            return false;
        }
        try {
            Class<?> screenManagerClass = Class.forName(EMI_SCREEN_MANAGER_CLASS);
            sidebarPanelClass = Class.forName(EMI_SIDEBAR_PANEL_CLASS);
            screenSpaceClass = Class.forName(EMI_SCREEN_SPACE_CLASS);

            panelsField = screenManagerClass.getDeclaredField("panels");
            panelsField.setAccessible(true);

            sidebarPanelPageField = sidebarPanelClass.getDeclaredField("page");
            sidebarPanelPageField.setAccessible(true);
            sidebarPanelSpaceField = sidebarPanelClass.getDeclaredField("space");
            sidebarPanelSpaceField.setAccessible(true);
            sidebarPanelIsVisibleMethod = sidebarPanelClass.getMethod("isVisible");
            sidebarPanelGetSpacesMethod = sidebarPanelClass.getMethod("getSpaces");

            screenSpacePageSizeField = screenSpaceClass.getDeclaredField("pageSize");
            screenSpacePageSizeField.setAccessible(true);
            screenSpaceGetTypeMethod = screenSpaceClass.getMethod("getType");
            screenSpaceGetStacksMethod = screenSpaceClass.getMethod("getStacks");
            screenSpaceGetRawXMethod = screenSpaceClass.getMethod("getRawX", int.class);
            screenSpaceGetRawYMethod = screenSpaceClass.getMethod("getRawY", int.class);
            return true;
        } catch (Exception e) {
            sidebarLookupFailed = true;
            EmiBookmarkEnhancements.LOGGER.debug("Failed to resolve EMI sidebar reflection handles", e);
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean resolveFavoriteMutationHandles() {
        if (!resolveRuntimeHandles()) {
            return false;
        }
        if (repopulatePanelsMethod != null && persistentSaveMethod != null && favoritesSidebarType != null) {
            return true;
        }
        if (mutationLookupFailed) {
            return false;
        }
        try {
            Class<?> screenManagerClass = Class.forName(EMI_SCREEN_MANAGER_CLASS);
            Class<?> sidebarTypeClass = Class.forName(EMI_SIDEBAR_TYPE_CLASS);
            Class<?> persistentDataClass = Class.forName(EMI_PERSISTENT_DATA_CLASS);

            repopulatePanelsMethod = screenManagerClass.getMethod("repopulatePanels", sidebarTypeClass);
            persistentSaveMethod = persistentDataClass.getMethod("save");
            favoritesSidebarType = Enum.valueOf((Class<? extends Enum>) sidebarTypeClass, "FAVORITES");
            return true;
        } catch (Exception e) {
            mutationLookupFailed = true;
            EmiBookmarkEnhancements.LOGGER.debug("Failed to resolve EMI mutation reflection handles", e);
            return false;
        }
    }

    private static final class SetByIdentity<T> {
        private final java.util.Set<T> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        static <T> SetByIdentity<T> of(List<T> values) {
            SetByIdentity<T> out = new SetByIdentity<>();
            out.set.addAll(values);
            return out;
        }

        boolean contains(T value) {
            return set.contains(value);
        }
    }

    public record FavoriteSlot(Object handle, int x, int y) {
    }
}
