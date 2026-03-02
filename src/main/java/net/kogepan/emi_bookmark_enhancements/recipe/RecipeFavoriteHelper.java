package net.kogepan.emi_bookmark_enhancements.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.kogepan.emi_bookmark_enhancements.EmiBookmarkEnhancements;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiIngredientKeyHelper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecipeFavoriteHelper {
    private static Field favoritesField;
    private static Constructor<?> favoriteConstructor;
    private static Method repopulatePanelsMethod;
    private static Method persistentSaveMethod;
    private static Object favoritesSidebarType;
    private static boolean reflectionLookupFailed;

    private RecipeFavoriteHelper() {
    }

    public static boolean addRecipeToFavorites(EmiRecipe recipe,
                                               EmiIngredient preferredResult,
                                               boolean createNewGroup,
                                               EmiBookmarkManager bookmarkManager) {
        if (recipe == null || bookmarkManager == null || recipe.getOutputs().isEmpty()) {
            return false;
        }
        if (!resolveReflectionHandles()) {
            return false;
        }

        int groupId = createNewGroup ? bookmarkManager.createGroup() : EmiBookmarkManager.DEFAULT_GROUP_ID;
        List<PlannedFavorite> plannedFavorites = buildPlannedFavorites(recipe, preferredResult, groupId);
        if (plannedFavorites.isEmpty()) {
            if (createNewGroup) {
                bookmarkManager.removeGroup(groupId);
            }
            return false;
        }

        List<Object> createdHandles = new ArrayList<>(plannedFavorites.size());
        List<Object> favorites = getRuntimeFavorites();
        if (favorites == null) {
            if (createNewGroup) {
                bookmarkManager.removeGroup(groupId);
            }
            return false;
        }
        try {
            for (PlannedFavorite favorite : plannedFavorites) {
                Object handle = favoriteConstructor.newInstance(favorite.favoriteIngredient(), favorite.recipeContext());
                favorites.add(handle);
                createdHandles.add(handle);
            }
        } catch (Exception e) {
            rollbackCreatedFavorites(favorites, createdHandles);
            EmiBookmarkEnhancements.LOGGER.error("Failed to add EMI favorite from recipe", e);
            if (createNewGroup) {
                bookmarkManager.removeGroup(groupId);
            }
            return false;
        }

        for (int i = 0; i < plannedFavorites.size(); i++) {
            PlannedFavorite favorite = plannedFavorites.get(i);
            Object handle = createdHandles.get(i);
            bookmarkManager.addEntry(
                    favorite.groupId(),
                    favorite.itemKey(),
                    favorite.factor(),
                    favorite.type(),
                    handle);
        }

        saveRuntimeFavorites();

        try {
            repopulatePanelsMethod.invoke(null, favoritesSidebarType);
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to refresh EMI favorites sidebar", e);
        }
        return true;
    }

    private static List<PlannedFavorite> buildPlannedFavorites(EmiRecipe recipe,
                                                               EmiIngredient preferredResult,
                                                               int groupId) {
        List<PlannedFavorite> planned = new ArrayList<>();
        List<EmiStack> outputs = recipe.getOutputs();
        if (outputs.isEmpty()) {
            return planned;
        }

        EmiStack selectedOutput = selectResultOutput(outputs, preferredResult);
        if (selectedOutput != null && !selectedOutput.isEmpty()) {
            String outputKey = EmiIngredientKeyHelper.toItemKey(selectedOutput);
            if (!outputKey.isBlank()) {
                planned.add(new PlannedFavorite(
                        groupId,
                        outputKey,
                        EmiIngredientKeyHelper.toBaseAmount(selectedOutput),
                        EmiBookmarkEntry.EntryType.RESULT,
                        selectedOutput.copy(),
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

    private static EmiStack selectResultOutput(List<EmiStack> outputs, EmiIngredient preferredResult) {
        if (outputs == null || outputs.isEmpty()) {
            return EmiStack.EMPTY;
        }
        if (preferredResult != null && !preferredResult.isEmpty()) {
            String preferredKey = EmiIngredientKeyHelper.toItemKey(preferredResult);
            for (EmiStack output : outputs) {
                if (matchesPreferredOutput(output, preferredResult, preferredKey)) {
                    return output;
                }
            }
        }
        for (EmiStack output : outputs) {
            if (output != null && !output.isEmpty()) {
                return output;
            }
        }
        return EmiStack.EMPTY;
    }

    private static boolean matchesPreferredOutput(EmiStack output, EmiIngredient preferredResult, String preferredKey) {
        if (output == null || output.isEmpty() || preferredResult == null || preferredResult.isEmpty()) {
            return false;
        }
        if (EmiIngredient.areEqual(output, preferredResult)) {
            return true;
        }
        for (EmiStack preferredStack : preferredResult.getEmiStacks()) {
            if (preferredStack != null && !preferredStack.isEmpty() && output.isEqual(preferredStack)) {
                return true;
            }
        }
        return !preferredKey.isBlank() && preferredKey.equals(EmiIngredientKeyHelper.toItemKey(output));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean resolveReflectionHandles() {
        if (favoritesField != null
                && favoriteConstructor != null
                && repopulatePanelsMethod != null
                && persistentSaveMethod != null
                && favoritesSidebarType != null) {
            return true;
        }
        if (reflectionLookupFailed) {
            return false;
        }
        try {
            Class<?> favoritesClass = Class.forName("dev.emi.emi.runtime.EmiFavorites");
            Class<?> favoriteClass = Class.forName("dev.emi.emi.runtime.EmiFavorite");
            Class<?> screenManagerClass = Class.forName("dev.emi.emi.screen.EmiScreenManager");
            Class<?> sidebarTypeClass = Class.forName("dev.emi.emi.config.SidebarType");
            Class<?> persistentDataClass = Class.forName("dev.emi.emi.runtime.EmiPersistentData");

            favoritesField = favoritesClass.getField("favorites");
            favoriteConstructor = favoriteClass.getConstructor(EmiIngredient.class, EmiRecipe.class);
            repopulatePanelsMethod = screenManagerClass.getMethod("repopulatePanels", sidebarTypeClass);
            persistentSaveMethod = persistentDataClass.getMethod("save");
            favoritesSidebarType = Enum.valueOf((Class<? extends Enum>) sidebarTypeClass, "FAVORITES");
            return true;
        } catch (Exception e) {
            reflectionLookupFailed = true;
            EmiBookmarkEnhancements.LOGGER.warn("Failed to resolve EMI runtime methods for recipe shortcuts.");
            EmiBookmarkEnhancements.LOGGER.debug("Reflection lookup failure", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getRuntimeFavorites() {
        try {
            Object value = favoritesField.get(null);
            if (value instanceof List<?> list) {
                return (List<Object>) list;
            }
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to access EMI favorites list", e);
        }
        return null;
    }

    private static void rollbackCreatedFavorites(List<Object> favorites, List<Object> createdHandles) {
        if (favorites == null || createdHandles == null || createdHandles.isEmpty()) {
            return;
        }
        Set<Object> toRemove = Collections.newSetFromMap(new IdentityHashMap<>());
        toRemove.addAll(createdHandles);
        favorites.removeIf(toRemove::contains);
    }

    private static void saveRuntimeFavorites() {
        try {
            persistentSaveMethod.invoke(null);
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.debug("Failed to save EMI favorites", e);
        }
    }

    private record PlannedFavorite(int groupId, String itemKey, long factor, EmiBookmarkEntry.EntryType type,
                                   EmiIngredient favoriteIngredient, EmiRecipe recipeContext) {
    }

    private record AggregatedIngredient(EmiIngredient ingredient, long amount) {
    }
}
