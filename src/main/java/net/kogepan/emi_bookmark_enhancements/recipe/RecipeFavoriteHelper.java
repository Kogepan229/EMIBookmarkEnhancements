package net.kogepan.emi_bookmark_enhancements.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.kogepan.emi_bookmark_enhancements.EmiBookmarkEnhancements;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiIngredientKeyHelper;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecipeFavoriteHelper {
    private static Method addFavoriteWithContextMethod;
    private static Method repopulatePanelsMethod;
    private static Object favoritesSidebarType;
    private static boolean reflectionLookupFailed;

    private RecipeFavoriteHelper() {
    }

    public static boolean addRecipeToFavorites(EmiRecipe recipe, boolean createNewGroup, EmiBookmarkManager bookmarkManager) {
        if (recipe == null || bookmarkManager == null || recipe.getOutputs().isEmpty()) {
            return false;
        }
        if (!resolveReflectionHandles()) {
            return false;
        }

        int groupId = createNewGroup ? bookmarkManager.createGroup() : EmiBookmarkManager.DEFAULT_GROUP_ID;
        List<PlannedFavorite> plannedFavorites = buildPlannedFavorites(recipe, groupId);
        if (plannedFavorites.isEmpty()) {
            if (createNewGroup) {
                bookmarkManager.removeGroup(groupId);
            }
            return false;
        }

        List<Object> before = EmiRuntimeAccess.getFavoriteHandles();
        Set<Object> beforeSet = Collections.newSetFromMap(new IdentityHashMap<>());
        beforeSet.addAll(before);

        for (PlannedFavorite favorite : plannedFavorites) {
            try {
                addFavoriteWithContextMethod.invoke(null, favorite.favoriteIngredient(), favorite.recipeContext());
            } catch (Exception e) {
                EmiBookmarkEnhancements.LOGGER.error("Failed to add EMI favorite from recipe", e);
                if (createNewGroup) {
                    bookmarkManager.removeGroup(groupId);
                }
                return false;
            }
        }

        List<Object> after = EmiRuntimeAccess.getFavoriteHandles();
        List<Object> addedHandles = new ArrayList<>();
        for (Object handle : after) {
            if (!beforeSet.contains(handle)) {
                addedHandles.add(handle);
            }
        }

        int bound = Math.min(addedHandles.size(), plannedFavorites.size());
        for (int i = 0; i < bound; i++) {
            PlannedFavorite favorite = plannedFavorites.get(i);
            Object handle = addedHandles.get(i);
            bookmarkManager.addEntry(
                    favorite.groupId(),
                    favorite.itemKey(),
                    favorite.factor(),
                    favorite.type(),
                    handle);
        }

        // Fallback in case EMI deduplicated favorites and fewer handles were created.
        for (int i = bound; i < plannedFavorites.size(); i++) {
            PlannedFavorite favorite = plannedFavorites.get(i);
            bookmarkManager.addEntry(
                    favorite.groupId(),
                    favorite.itemKey(),
                    favorite.factor(),
                    favorite.type());
        }

        try {
            repopulatePanelsMethod.invoke(null, favoritesSidebarType);
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to refresh EMI favorites sidebar", e);
        }
        return true;
    }

    private static List<PlannedFavorite> buildPlannedFavorites(EmiRecipe recipe, int groupId) {
        List<PlannedFavorite> planned = new ArrayList<>();
        List<EmiStack> outputs = recipe.getOutputs();
        if (outputs.isEmpty()) {
            return planned;
        }

        EmiStack primaryOutput = outputs.get(0);
        if (!primaryOutput.isEmpty()) {
            String outputKey = EmiIngredientKeyHelper.toItemKey(primaryOutput);
            if (!outputKey.isBlank()) {
                planned.add(new PlannedFavorite(
                        groupId,
                        outputKey,
                        EmiIngredientKeyHelper.toBaseAmount(primaryOutput),
                        EmiBookmarkEntry.EntryType.RESULT,
                        primaryOutput.copy(),
                        recipe));
            }
        }

        Map<String, AggregatedIngredient> aggregatedInputs = new LinkedHashMap<>();
        for (EmiIngredient ingredient : recipe.getInputs()) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            String itemKey = EmiIngredientKeyHelper.toItemKey(ingredient);
            if (itemKey.isBlank()) {
                continue;
            }
            long amount = EmiIngredientKeyHelper.toBaseAmount(ingredient);
            aggregatedInputs.compute(itemKey, (key, previous) -> {
                if (previous == null) {
                    EmiIngredient copy = ingredient.copy();
                    copy.setAmount(amount);
                    return new AggregatedIngredient(copy, amount);
                }
                long merged = previous.amount() + amount;
                EmiIngredient mergedIngredient = previous.ingredient().copy();
                mergedIngredient.setAmount(merged);
                return new AggregatedIngredient(mergedIngredient, merged);
            });
        }

        for (Map.Entry<String, AggregatedIngredient> input : aggregatedInputs.entrySet()) {
            planned.add(new PlannedFavorite(
                    groupId,
                    input.getKey(),
                    input.getValue().amount(),
                    EmiBookmarkEntry.EntryType.INGREDIENT,
                    input.getValue().ingredient(),
                    null));
        }
        return planned;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean resolveReflectionHandles() {
        if (addFavoriteWithContextMethod != null && repopulatePanelsMethod != null && favoritesSidebarType != null) {
            return true;
        }
        if (reflectionLookupFailed) {
            return false;
        }
        try {
            Class<?> favoritesClass = Class.forName("dev.emi.emi.runtime.EmiFavorites");
            Class<?> screenManagerClass = Class.forName("dev.emi.emi.screen.EmiScreenManager");
            Class<?> sidebarTypeClass = Class.forName("dev.emi.emi.config.SidebarType");

            addFavoriteWithContextMethod = favoritesClass.getMethod("addFavorite", EmiIngredient.class, EmiRecipe.class);
            repopulatePanelsMethod = screenManagerClass.getMethod("repopulatePanels", sidebarTypeClass);
            favoritesSidebarType = Enum.valueOf((Class<? extends Enum>) sidebarTypeClass, "FAVORITES");
            return true;
        } catch (Exception e) {
            reflectionLookupFailed = true;
            EmiBookmarkEnhancements.LOGGER.warn("Failed to resolve EMI runtime methods for recipe shortcuts.");
            EmiBookmarkEnhancements.LOGGER.debug("Reflection lookup failure", e);
            return false;
        }
    }

    private record PlannedFavorite(int groupId, String itemKey, long factor, EmiBookmarkEntry.EntryType type,
                                   EmiIngredient favoriteIngredient, EmiRecipe recipeContext) {
    }

    private record AggregatedIngredient(EmiIngredient ingredient, long amount) {
    }
}
