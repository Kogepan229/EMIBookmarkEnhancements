package net.kogepan.emi_bookmark_enhancements.bookmark.service;

import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CraftingChainCalculator {
    boolean recalculateGroup(List<EmiBookmarkEntry> allEntries, int groupId) {
        List<EmiBookmarkEntry> groupEntries = collectGroupEntries(allEntries, groupId);
        if (groupEntries.isEmpty()) {
            return false;
        }

        List<EmiBookmarkEntry> results = new ArrayList<>();
        for (EmiBookmarkEntry entry : groupEntries) {
            if (entry.isResult()) {
                results.add(entry);
            }
        }
        if (results.isEmpty()) {
            return false;
        }

        Map<EmiBookmarkEntry, EmiBookmarkEntry> preferredItems = new IdentityHashMap<>();
        for (EmiBookmarkEntry result : results) {
            collectPreferredItems(result, groupEntries, results, preferredItems, newIdentitySet());
        }

        EmiBookmarkEntry firstResult = results.get(0);
        Map<EmiBookmarkEntry, Long> requiredAmount = new IdentityHashMap<>();
        Map<EmiBookmarkEntry, Long> currentAmount = new IdentityHashMap<>();
        for (EmiBookmarkEntry result : results) {
            if (result == firstResult) {
                currentAmount.put(result, result.getAmount());
            } else {
                currentAmount.put(result, 0L);
            }
        }

        long topMultiplier = firstResult.getMultiplier();
        calculateChainRequirements(firstResult, topMultiplier, groupEntries, preferredItems,
                requiredAmount, currentAmount, newIdentitySet());

        boolean changed = false;
        for (EmiBookmarkEntry result : results) {
            if (result == firstResult) {
                continue;
            }
            long amount = currentAmount.getOrDefault(result, 0L);
            if (amount <= 0L) {
                continue;
            }
            long multiplier = ceilDiv(amount, result.getFactor());
            changed |= setMultiplierIfChanged(result, multiplier);

            for (EmiBookmarkEntry ingredient : findRecipeIngredients(result, groupEntries)) {
                changed |= setMultiplierIfChanged(ingredient, multiplier);
            }
        }
        return changed;
    }

    private void calculateChainRequirements(EmiBookmarkEntry resultEntry,
                                            long multiplier,
                                            List<EmiBookmarkEntry> groupEntries,
                                            Map<EmiBookmarkEntry, EmiBookmarkEntry> preferredItems,
                                            Map<EmiBookmarkEntry, Long> requiredAmount,
                                            Map<EmiBookmarkEntry, Long> currentAmount,
                                            Set<EmiBookmarkEntry> visited) {
        if (!visited.add(resultEntry)) {
            return;
        }

        for (EmiBookmarkEntry ingredient : findRecipeIngredients(resultEntry, groupEntries)) {
            EmiBookmarkEntry preferredResult = preferredItems.get(ingredient);
            if (preferredResult == null) {
                continue;
            }

            long ingredientNeeded = saturatingMultiply(ingredient.getFactor(), multiplier);
            long previousRequired = requiredAmount.getOrDefault(preferredResult, 0L);
            long newRequired = saturatingAdd(previousRequired, ingredientNeeded);
            requiredAmount.put(preferredResult, newRequired);

            long previousAmount = currentAmount.getOrDefault(preferredResult, 0L);
            long missingAmount = newRequired - previousAmount;
            long shift = missingAmount > 0L
                    ? ceilDiv(missingAmount, preferredResult.getFactor())
                    : 0L;
            if (shift > 0L) {
                long produced = saturatingMultiply(shift, preferredResult.getFactor());
                long nextAmount = saturatingAdd(previousAmount, produced);
                currentAmount.put(preferredResult, nextAmount);
                calculateChainRequirements(preferredResult, shift, groupEntries, preferredItems,
                        requiredAmount, currentAmount, visited);
            }
        }

        visited.remove(resultEntry);
    }

    private void collectPreferredItems(EmiBookmarkEntry sourceResult,
                                       List<EmiBookmarkEntry> groupEntries,
                                       List<EmiBookmarkEntry> results,
                                       Map<EmiBookmarkEntry, EmiBookmarkEntry> preferredItems,
                                       Set<EmiBookmarkEntry> visited) {
        if (!visited.add(sourceResult)) {
            return;
        }

        for (EmiBookmarkEntry ingredient : findRecipeIngredients(sourceResult, groupEntries)) {
            if (preferredItems.containsKey(ingredient)) {
                continue;
            }
            for (EmiBookmarkEntry candidateResult : results) {
                if (candidateResult == sourceResult || visited.contains(candidateResult)) {
                    continue;
                }
                if (candidateResult.getItemKey().equals(ingredient.getItemKey())) {
                    preferredItems.put(ingredient, candidateResult);
                    collectPreferredItems(candidateResult, groupEntries, results, preferredItems, visited);
                    break;
                }
            }
        }

        visited.remove(sourceResult);
    }

    private static List<EmiBookmarkEntry> findRecipeIngredients(EmiBookmarkEntry result,
                                                                List<EmiBookmarkEntry> groupEntries) {
        int index = groupEntries.indexOf(result);
        if (index < 0) {
            return List.of();
        }

        List<EmiBookmarkEntry> ingredients = new ArrayList<>();
        for (int i = index + 1; i < groupEntries.size(); i++) {
            EmiBookmarkEntry entry = groupEntries.get(i);
            if (entry.isResult() || entry.getType() == EmiBookmarkEntry.EntryType.ITEM) {
                break;
            }
            if (entry.isIngredient()) {
                ingredients.add(entry);
                continue;
            }
            break;
        }
        return ingredients;
    }

    private static List<EmiBookmarkEntry> collectGroupEntries(List<EmiBookmarkEntry> allEntries, int groupId) {
        List<EmiBookmarkEntry> groupEntries = new ArrayList<>();
        for (EmiBookmarkEntry entry : allEntries) {
            if (entry.getGroupId() == groupId) {
                groupEntries.add(entry);
            }
        }
        return groupEntries;
    }

    private static long ceilDiv(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0L;
        }
        if (numerator <= 0L) {
            return 0L;
        }
        long quotient = numerator / denominator;
        return numerator % denominator == 0L ? quotient : quotient + 1L;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static boolean setMultiplierIfChanged(EmiBookmarkEntry entry, long multiplier) {
        long safeMultiplier = Math.max(1L, multiplier);
        if (entry.getMultiplier() == safeMultiplier) {
            return false;
        }
        entry.setMultiplier(safeMultiplier);
        return true;
    }

    private static Set<EmiBookmarkEntry> newIdentitySet() {
        return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
