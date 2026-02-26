package net.kogepan.emi_bookmark_enhancements.integration.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.kogepan.emi_bookmark_enhancements.EmiBookmarkEnhancements;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private static final String EMI_CONFIG_CLASS = "dev.emi.emi.config.EmiConfig";

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
    private static Field screenSpaceTxField;
    private static Field screenSpaceTyField;
    private static Field screenSpaceTwField;
    private static Method screenSpaceGetTypeMethod;
    private static Method screenSpaceGetStacksMethod;
    private static Method screenSpaceGetRawXMethod;
    private static Method screenSpaceGetRawYMethod;
    private static Method repopulatePanelsMethod;
    private static Method persistentSaveMethod;
    private static Object favoritesSidebarType;
    private static Method forceRecalculateMethod;
    private static Field leftSidebarMarginsField;
    private static Field leftSidebarSizeField;
    private static Field rightSidebarSizeField;
    private static Field topSidebarSizeField;
    private static Field bottomSidebarSizeField;
    private static Field sidebarPanelSideField;
    private static Field intGroupValuesField;
    private static boolean sidebarInsetLookupFailed;
    private static boolean sidebarColumnsLookupFailed;
    private static int baseLeftSidebarMargin = Integer.MIN_VALUE;
    private static int appliedLeftSidebarInset;
    private static final Map<String, Integer> baseSidebarColumns = new HashMap<>();

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
                refreshFavoritesSidebar();
            }
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to remove EMI favorite handles", e);
            return 0;
        }
        return removedCount;
    }

    public static void refreshFavoritesSidebar() {
        if (!resolveFavoriteMutationHandles()) {
            return;
        }
        try {
            repopulatePanelsMethod.invoke(null, favoritesSidebarType);
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to refresh EMI favorites sidebar", e);
        }
    }

    public static boolean isFavoriteHeaderLayoutToggleTarget(int mouseX, int mouseY) {
        if (!resolveSidebarHandles()) {
            return false;
        }
        try {
            Object value = panelsField.get(null);
            if (!(value instanceof List<?> panels)) {
                return false;
            }
            for (Object panel : panels) {
                if (panel == null || !sidebarPanelClass.isInstance(panel)) {
                    continue;
                }
                boolean isVisible = (boolean) sidebarPanelIsVisibleMethod.invoke(panel);
                if (!isVisible) {
                    continue;
                }
                Object space = sidebarPanelSpaceField.get(panel);
                if (space == null || !screenSpaceClass.isInstance(space)) {
                    continue;
                }
                Object type = screenSpaceGetTypeMethod.invoke(space);
                if (type == null || !"FAVORITES".equals(type.toString())) {
                    continue;
                }

                int tx = screenSpaceTxField.getInt(space);
                int ty = screenSpaceTyField.getInt(space);
                int tw = screenSpaceTwField.getInt(space);
                if (tw <= 0) {
                    continue;
                }

                int headerTop = ty - 18;
                int headerBottom = ty - 1;
                int headerLeft = tx + 20;
                int headerRight = tx + tw * 18 - 20;
                if (headerRight <= headerLeft || mouseY < headerTop || mouseY > headerBottom) {
                    continue;
                }

                int centerX = headerLeft + (headerRight - headerLeft) / 2;
                int halfWidth = Math.min(32, Math.max(16, (headerRight - headerLeft) / 4));
                int toggleLeft = centerX - halfWidth;
                int toggleRight = centerX + halfWidth;
                if (mouseX >= toggleLeft && mouseX <= toggleRight) {
                    return true;
                }
            }
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to inspect favorites header bounds", e);
        }
        return false;
    }

    public static void ensureFavoritesSidebarInset(int requiredInsetPx, int observedGridLeft) {
        if (requiredInsetPx <= 0 || observedGridLeft >= requiredInsetPx || !resolveSidebarInsetHandles()) {
            return;
        }
        int requiredBoost = requiredInsetPx - observedGridLeft;
        if (requiredBoost <= appliedLeftSidebarInset) {
            return;
        }

        try {
            Object margins = leftSidebarMarginsField.get(null);
            if (margins == null) {
                return;
            }
            Object valuesObj = intGroupValuesField.get(margins);
            if (!(valuesObj instanceof List<?> values) || values.size() < 4) {
                return;
            }
            Object leftValue = values.get(3);
            if (!(leftValue instanceof Number currentLeftNumber)) {
                return;
            }
            int currentLeft = currentLeftNumber.intValue();
            if (baseLeftSidebarMargin == Integer.MIN_VALUE) {
                baseLeftSidebarMargin = Math.max(0, currentLeft - appliedLeftSidebarInset);
            }

            int targetLeft = Math.max(currentLeft, baseLeftSidebarMargin + requiredBoost);
            if (targetLeft == currentLeft) {
                return;
            }

            @SuppressWarnings("unchecked")
            List<Object> mutableValues = (List<Object>) values;
            mutableValues.set(3, targetLeft);
            appliedLeftSidebarInset = targetLeft - baseLeftSidebarMargin;
            forceRecalculateMethod.invoke(null);
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to increase favorites sidebar inset", e);
        }
    }

    public static boolean applyVerticalFavoritesRowPolicy(boolean verticalMode) {
        return applyVerticalFavoritesRowPolicy(verticalMode, Integer.MAX_VALUE);
    }

    public static boolean applyVerticalFavoritesRowPolicy(boolean verticalMode, int preferredColumns) {
        if (!resolveSidebarColumnsHandles()) {
            return false;
        }

        boolean changed = false;
        try {
            if (verticalMode) {
                Set<String> favoriteSides = detectVisibleFavoriteSides();
                if (favoriteSides.isEmpty()) {
                    favoriteSides = Set.of("LEFT");
                }

                for (String side : favoriteSides) {
                    int baseColumns = ensureBaseSidebarColumns(side);
                    int targetColumns = Math.max(1, Math.min(baseColumns, preferredColumns));
                    changed |= setSidebarColumns(side, targetColumns, true);
                }

                Set<String> knownSides = new LinkedHashSet<>(baseSidebarColumns.keySet());
                for (String side : knownSides) {
                    if (!favoriteSides.contains(side)) {
                        changed |= restoreSidebarColumns(side);
                    }
                }
            } else {
                Set<String> knownSides = new LinkedHashSet<>(baseSidebarColumns.keySet());
                for (String side : knownSides) {
                    changed |= restoreSidebarColumns(side);
                }
            }
            if (changed) {
                forceRecalculateMethod.invoke(null);
            }
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to apply favorites vertical row policy", e);
            return false;
        }
        return changed;
    }

    public static int getFavoritesSidebarBaseColumns() {
        if (!resolveSidebarColumnsHandles()) {
            return 1;
        }
        try {
            Set<String> favoriteSides = detectVisibleFavoriteSides();
            if (favoriteSides.isEmpty()) {
                if (baseSidebarColumns.isEmpty()) {
                    return 1;
                }
                int min = Integer.MAX_VALUE;
                for (int value : baseSidebarColumns.values()) {
                    min = Math.min(min, Math.max(1, value));
                }
                return min == Integer.MAX_VALUE ? 1 : min;
            }

            int min = Integer.MAX_VALUE;
            for (String side : favoriteSides) {
                int baseColumns = ensureBaseSidebarColumns(side);
                min = Math.min(min, Math.max(1, baseColumns));
            }
            return min == Integer.MAX_VALUE ? 1 : min;
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to resolve favorites sidebar base columns", e);
            return 1;
        }
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
                && screenSpaceTxField != null
                && screenSpaceTyField != null
                && screenSpaceTwField != null
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
            screenSpaceTxField = screenSpaceClass.getDeclaredField("tx");
            screenSpaceTxField.setAccessible(true);
            screenSpaceTyField = screenSpaceClass.getDeclaredField("ty");
            screenSpaceTyField.setAccessible(true);
            screenSpaceTwField = screenSpaceClass.getDeclaredField("tw");
            screenSpaceTwField.setAccessible(true);
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

    private static boolean resolveSidebarInsetHandles() {
        if (leftSidebarMarginsField != null && intGroupValuesField != null && forceRecalculateMethod != null) {
            return true;
        }
        if (sidebarInsetLookupFailed) {
            return false;
        }
        try {
            Class<?> configClass = Class.forName(EMI_CONFIG_CLASS);
            Class<?> screenManagerClass = Class.forName(EMI_SCREEN_MANAGER_CLASS);
            Class<?> intGroupClass = Class.forName("dev.emi.emi.config.IntGroup");

            leftSidebarMarginsField = configClass.getField("leftSidebarMargins");
            intGroupValuesField = intGroupClass.getField("values");
            forceRecalculateMethod = screenManagerClass.getMethod("forceRecalculate");
            return true;
        } catch (Exception e) {
            sidebarInsetLookupFailed = true;
            EmiBookmarkEnhancements.LOGGER.debug("Failed to resolve EMI sidebar inset handles", e);
            return false;
        }
    }

    private static boolean resolveSidebarColumnsHandles() {
        if (leftSidebarSizeField != null
                && rightSidebarSizeField != null
                && topSidebarSizeField != null
                && bottomSidebarSizeField != null
                && sidebarPanelSideField != null
                && intGroupValuesField != null
                && forceRecalculateMethod != null) {
            return true;
        }
        if (sidebarColumnsLookupFailed) {
            return false;
        }
        try {
            if (!resolveSidebarHandles()) {
                return false;
            }
            Class<?> configClass = Class.forName(EMI_CONFIG_CLASS);
            Class<?> intGroupClass = Class.forName("dev.emi.emi.config.IntGroup");
            Class<?> screenManagerClass = Class.forName(EMI_SCREEN_MANAGER_CLASS);

            leftSidebarSizeField = configClass.getField("leftSidebarSize");
            rightSidebarSizeField = configClass.getField("rightSidebarSize");
            topSidebarSizeField = configClass.getField("topSidebarSize");
            bottomSidebarSizeField = configClass.getField("bottomSidebarSize");
            sidebarPanelSideField = sidebarPanelClass.getField("side");
            if (intGroupValuesField == null) {
                intGroupValuesField = intGroupClass.getField("values");
            }
            if (forceRecalculateMethod == null) {
                forceRecalculateMethod = screenManagerClass.getMethod("forceRecalculate");
            }
            return true;
        } catch (Exception e) {
            sidebarColumnsLookupFailed = true;
            EmiBookmarkEnhancements.LOGGER.debug("Failed to resolve EMI sidebar column handles", e);
            return false;
        }
    }

    private static Set<String> detectVisibleFavoriteSides() {
        if (!resolveSidebarHandles()) {
            return Set.of();
        }
        try {
            Object value = panelsField.get(null);
            if (!(value instanceof List<?> panels)) {
                return Set.of();
            }
            Set<String> sides = new LinkedHashSet<>();
            for (Object panel : panels) {
                if (panel == null || !sidebarPanelClass.isInstance(panel)) {
                    continue;
                }
                boolean visible = (boolean) sidebarPanelIsVisibleMethod.invoke(panel);
                if (!visible) {
                    continue;
                }
                Object space = sidebarPanelSpaceField.get(panel);
                if (space == null || !screenSpaceClass.isInstance(space)) {
                    continue;
                }
                Object type = screenSpaceGetTypeMethod.invoke(space);
                if (type == null || !"FAVORITES".equals(type.toString())) {
                    continue;
                }
                Object side = sidebarPanelSideField.get(panel);
                if (side != null) {
                    sides.add(side.toString());
                }
            }
            return sides;
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to detect favorites sidebar side", e);
            return Set.of();
        }
    }

    private static boolean setSidebarColumns(String side, int columns, boolean rememberBase) throws IllegalAccessException {
        Field sizeField = sizeFieldForSide(side);
        if (sizeField == null) {
            return false;
        }
        Object sizeGroup = sizeField.get(null);
        if (sizeGroup == null) {
            return false;
        }
        Object valuesObj = intGroupValuesField.get(sizeGroup);
        if (!(valuesObj instanceof List<?> values) || values.isEmpty()) {
            return false;
        }
        Object columnValue = values.get(0);
        if (!(columnValue instanceof Number currentNumber)) {
            return false;
        }
        int current = currentNumber.intValue();
        if (rememberBase) {
            baseSidebarColumns.putIfAbsent(side, current);
        }
        int safeColumns = Math.max(1, columns);
        if (current == safeColumns) {
            return false;
        }
        @SuppressWarnings("unchecked")
        List<Object> mutableValues = (List<Object>) values;
        mutableValues.set(0, safeColumns);
        return true;
    }

    private static int ensureBaseSidebarColumns(String side) throws IllegalAccessException {
        Integer base = baseSidebarColumns.get(side);
        if (base != null) {
            return Math.max(1, base);
        }
        int current = readSidebarColumns(side);
        int safe = Math.max(1, current);
        baseSidebarColumns.put(side, safe);
        return safe;
    }

    private static int readSidebarColumns(String side) throws IllegalAccessException {
        Field sizeField = sizeFieldForSide(side);
        if (sizeField == null) {
            return 1;
        }
        Object sizeGroup = sizeField.get(null);
        if (sizeGroup == null) {
            return 1;
        }
        Object valuesObj = intGroupValuesField.get(sizeGroup);
        if (!(valuesObj instanceof List<?> values) || values.isEmpty()) {
            return 1;
        }
        Object columnValue = values.get(0);
        if (columnValue instanceof Number currentNumber) {
            return currentNumber.intValue();
        }
        return 1;
    }

    private static boolean restoreSidebarColumns(String side) throws IllegalAccessException {
        Integer base = baseSidebarColumns.get(side);
        if (base == null) {
            return false;
        }
        boolean changed = setSidebarColumns(side, base, false);
        if (changed) {
            baseSidebarColumns.remove(side);
            return true;
        }

        Field sizeField = sizeFieldForSide(side);
        if (sizeField == null) {
            baseSidebarColumns.remove(side);
            return false;
        }
        Object sizeGroup = sizeField.get(null);
        if (sizeGroup == null) {
            baseSidebarColumns.remove(side);
            return false;
        }
        Object valuesObj = intGroupValuesField.get(sizeGroup);
        if (!(valuesObj instanceof List<?> values) || values.isEmpty()) {
            baseSidebarColumns.remove(side);
            return false;
        }
        Object columnValue = values.get(0);
        if (columnValue instanceof Number currentNumber && currentNumber.intValue() == base) {
            baseSidebarColumns.remove(side);
        }
        return false;
    }

    private static Field sizeFieldForSide(String side) {
        if (side == null) {
            return null;
        }
        return switch (side) {
            case "LEFT" -> leftSidebarSizeField;
            case "RIGHT" -> rightSidebarSizeField;
            case "TOP" -> topSidebarSizeField;
            case "BOTTOM" -> bottomSidebarSizeField;
            default -> null;
        };
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
