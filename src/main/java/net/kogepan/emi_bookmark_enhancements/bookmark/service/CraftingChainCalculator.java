package net.kogepan.emi_bookmark_enhancements.bookmark.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

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

        List<EmiBookmarkEntry> rootResults = collectRootResults(results, groupEntries, preferredItems);
        if (rootResults.isEmpty()) {
            rootResults = List.of(results.get(0));
        }

        Map<EmiBookmarkEntry, Long> requiredAmount = new IdentityHashMap<>();
        Map<EmiBookmarkEntry, Long> currentAmount = new IdentityHashMap<>();
        for (EmiBookmarkEntry result : results) {
            currentAmount.put(result, 0L);
        }

        for (EmiBookmarkEntry root : rootResults) {
            long explicitAmount = root.getAmount();
            currentAmount.put(root, explicitAmount);
            long rootMultiplier = ceilDiv(explicitAmount, root.getFactor());
            calculateChainRequirements(root, rootMultiplier, groupEntries, preferredItems,
                    requiredAmount, currentAmount, newIdentitySet());
        }

        boolean changed = false;
        for (EmiBookmarkEntry result : results) {
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
                if (sameItemIdentity(candidateResult.getItemKey(), ingredient.getItemKey())) {
                    preferredItems.put(ingredient, candidateResult);
                    collectPreferredItems(candidateResult, groupEntries, results, preferredItems, visited);
                    break;
                }
            }
        }

        visited.remove(sourceResult);
    }

    private static List<EmiBookmarkEntry> collectRootResults(List<EmiBookmarkEntry> results,
                                                             List<EmiBookmarkEntry> groupEntries,
                                                             Map<EmiBookmarkEntry, EmiBookmarkEntry> preferredItems) {
        Set<EmiBookmarkEntry> dependentResults = newIdentitySet();
        for (EmiBookmarkEntry result : results) {
            for (EmiBookmarkEntry ingredient : findRecipeIngredients(result, groupEntries)) {
                EmiBookmarkEntry mappedResult = preferredItems.get(ingredient);
                if (mappedResult != null) {
                    dependentResults.add(mappedResult);
                }
            }
        }

        List<EmiBookmarkEntry> roots = new ArrayList<>();
        for (EmiBookmarkEntry result : results) {
            if (!dependentResults.contains(result)) {
                roots.add(result);
            }
        }
        return roots;
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

    private static boolean sameItemIdentity(String left, String right) {
        ItemIdentity leftIdentity = parseItemIdentity(left);
        ItemIdentity rightIdentity = parseItemIdentity(right);

        if (!leftIdentity.id().isBlank() && !rightIdentity.id().isBlank()) {
            if (leftIdentity.kind() == rightIdentity.kind()) {
                return leftIdentity.id().equals(rightIdentity.id());
            }
            if (leftIdentity.kind() == ItemIdentityKind.TAG
                    && rightIdentity.kind() == ItemIdentityKind.ITEM) {
                return tagContainsItem(leftIdentity.id(), rightIdentity.id());
            }
            if (leftIdentity.kind() == ItemIdentityKind.ITEM
                    && rightIdentity.kind() == ItemIdentityKind.TAG) {
                return tagContainsItem(rightIdentity.id(), leftIdentity.id());
            }
            return leftIdentity.id().equals(rightIdentity.id());
        }
        return left != null && left.equals(right);
    }

    private static ItemIdentity parseItemIdentity(String key) {
        if (key == null) {
            return ItemIdentity.empty();
        }
        String trimmed = key.trim();
        if (trimmed.isBlank()) {
            return ItemIdentity.empty();
        }
        try {
            JsonElement parsed = JsonParser.parseString(trimmed);
            ItemIdentity identity = parseIdentityFromJson(parsed);
            if (!identity.id().isBlank()) {
                return identity;
            }
        } catch (Exception ignored) {
        }
        return parseIdentityFromLiteral(trimmed);
    }

    private static ItemIdentity parseIdentityFromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return ItemIdentity.empty();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return parseIdentityFromLiteral(element.getAsString());
        }
        if (!element.isJsonObject()) {
            return ItemIdentity.empty();
        }

        JsonObject object = element.getAsJsonObject();
        String type = readString(object, "type");
        String id = readString(object, "id");
        if ("tag".equals(type) && !id.isBlank()) {
            return new ItemIdentity(ItemIdentityKind.TAG, id);
        }
        if ("item".equals(type) && !id.isBlank()) {
            return new ItemIdentity(ItemIdentityKind.ITEM, id);
        }
        if (!id.isBlank()) {
            return new ItemIdentity(ItemIdentityKind.ITEM, id);
        }
        String item = readString(object, "item");
        if (!item.isBlank()) {
            return new ItemIdentity(ItemIdentityKind.ITEM, item);
        }
        String fluid = readString(object, "fluid");
        if (!fluid.isBlank()) {
            return new ItemIdentity(ItemIdentityKind.OTHER, fluid);
        }
        String stack = readString(object, "stack");
        if (!stack.isBlank()) {
            return parseIdentityFromLiteral(stack);
        }
        return ItemIdentity.empty();
    }

    private static String readString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return "";
        }
        try {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                return value.getAsString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static ItemIdentity parseIdentityFromLiteral(String value) {
        if (value == null) {
            return ItemIdentity.empty();
        }
        String normalized = value.trim();
        while (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.startsWith("#")) {
            return new ItemIdentity(ItemIdentityKind.TAG, normalized.substring(1).trim());
        }
        if (normalized.startsWith("item:")) {
            return new ItemIdentity(ItemIdentityKind.ITEM, normalized.substring("item:".length()).trim());
        }
        if (normalized.startsWith("fluid:")) {
            return new ItemIdentity(ItemIdentityKind.OTHER, normalized.substring("fluid:".length()).trim());
        }
        if (normalized.startsWith("tag:")) {
            return new ItemIdentity(ItemIdentityKind.TAG, normalized.substring("tag:".length()).trim());
        }
        return new ItemIdentity(ItemIdentityKind.ITEM, normalized);
    }

    private static boolean tagContainsItem(String tagId, String itemId) {
        if (tagId == null || itemId == null || tagId.isBlank() || itemId.isBlank()) {
            return false;
        }
        ResourceLocation tagLocation = ResourceLocation.tryParse(tagId);
        ResourceLocation itemLocation = ResourceLocation.tryParse(itemId);
        if (tagLocation == null || itemLocation == null) {
            return false;
        }
        if (!BuiltInRegistries.ITEM.containsKey(itemLocation)) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(itemLocation);
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLocation);
        return item.builtInRegistryHolder().is(tagKey);
    }

    private enum ItemIdentityKind {
        ITEM,
        TAG,
        OTHER
    }

    private record ItemIdentity(ItemIdentityKind kind, String id) {
        private static ItemIdentity empty() {
            return new ItemIdentity(ItemIdentityKind.OTHER, "");
        }
    }
}
