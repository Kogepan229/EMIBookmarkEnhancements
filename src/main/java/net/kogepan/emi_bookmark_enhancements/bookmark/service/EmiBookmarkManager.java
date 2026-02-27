package net.kogepan.emi_bookmark_enhancements.bookmark.service;

import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkGroup;
import net.kogepan.emi_bookmark_enhancements.bookmark.persistence.BookmarkStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class EmiBookmarkManager {
    public static final int DEFAULT_GROUP_ID = 0;

    private final List<EmiBookmarkEntry> entries = new ArrayList<>();
    private final Map<Integer, EmiBookmarkGroup> groups = new LinkedHashMap<>();
    private final Map<Object, EmiBookmarkEntry> favoriteBindings = new IdentityHashMap<>();
    private final BookmarkStore store;
    private final CraftingChainCalculator craftingChainCalculator = new CraftingChainCalculator();

    private int nextGroupId = DEFAULT_GROUP_ID + 1;
    private int currentAddingGroupId = DEFAULT_GROUP_ID;
    private boolean allowDuplicates;
    private boolean loaded;
    private boolean dirty;

    public EmiBookmarkManager() {
        this(new BookmarkStore());
    }

    public EmiBookmarkManager(BookmarkStore store) {
        this.store = Objects.requireNonNull(store, "store");
        resetToDefaults();
    }

    public synchronized void ensureLoaded() {
        if (!loaded) {
            applySnapshot(store.load());
        }
    }

    public synchronized void load() {
        applySnapshot(store.load());
    }

    public synchronized void save() {
        if (!dirty) {
            return;
        }
        if (store.save(createSnapshot())) {
            dirty = false;
        }
    }

    public synchronized void clearAll() {
        resetToDefaults();
        loaded = true;
        dirty = true;
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized boolean isAllowDuplicates() {
        return allowDuplicates;
    }

    public synchronized void setAllowDuplicates(boolean allowDuplicates) {
        this.allowDuplicates = allowDuplicates;
    }

    public synchronized int getCurrentAddingGroupId() {
        return currentAddingGroupId;
    }

    public synchronized void setCurrentAddingGroupId(int groupId) {
        ensureLoaded();
        this.currentAddingGroupId = groups.containsKey(groupId) ? groupId : DEFAULT_GROUP_ID;
    }

    public synchronized int getNextGroupId() {
        ensureLoaded();
        return nextGroupId;
    }

    public synchronized int createGroup() {
        ensureLoaded();
        if (nextGroupId == Integer.MAX_VALUE) {
            throw new IllegalStateException("No more group ids are available");
        }
        int groupId = nextGroupId;
        nextGroupId++;
        groups.putIfAbsent(groupId, new EmiBookmarkGroup(groupId));
        dirty = true;
        return groupId;
    }

    public synchronized EmiBookmarkGroup getGroup(int groupId) {
        ensureLoaded();
        return groups.get(groupId);
    }

    public synchronized Collection<EmiBookmarkGroup> getAllGroups() {
        ensureLoaded();
        return List.copyOf(groups.values());
    }

    public synchronized Set<Integer> getActiveGroupIds() {
        ensureLoaded();
        Set<Integer> groupIds = new LinkedHashSet<>();
        for (EmiBookmarkEntry entry : entries) {
            groupIds.add(entry.getGroupId());
        }
        return groupIds;
    }

    public synchronized List<EmiBookmarkEntry> getAllEntries() {
        ensureLoaded();
        return List.copyOf(entries);
    }

    public synchronized List<EmiBookmarkEntry> getGroupEntries(int groupId) {
        ensureLoaded();
        List<EmiBookmarkEntry> groupEntries = new ArrayList<>();
        for (EmiBookmarkEntry entry : entries) {
            if (entry.getGroupId() == groupId) {
                groupEntries.add(entry);
            }
        }
        return groupEntries;
    }

    public synchronized EmiBookmarkEntry addEntry(int groupId, String itemKey, long factor, EmiBookmarkEntry.EntryType type) {
        return addEntry(groupId, itemKey, factor, type, null);
    }

    public synchronized EmiBookmarkEntry addEntry(int groupId, String itemKey, long factor,
                                                  EmiBookmarkEntry.EntryType type, Object favoriteHandle) {
        ensureLoaded();
        ensureGroupExists(groupId);

        if (favoriteHandle != null) {
            EmiBookmarkEntry bound = favoriteBindings.get(favoriteHandle);
            if (bound != null && entries.contains(bound)) {
                return bound;
            }
        } else if (!allowDuplicates) {
            EmiBookmarkEntry existing = findEntry(groupId, itemKey, type);
            if (existing != null) {
                return existing;
            }
        }

        EmiBookmarkEntry entry = createManagedEntry(groupId, itemKey, factor, type);
        entries.add(entry);
        if (favoriteHandle != null) {
            favoriteBindings.put(favoriteHandle, entry);
        }
        dirty = true;
        return entry;
    }

    public synchronized EmiBookmarkEntry findEntry(int groupId, String itemKey, EmiBookmarkEntry.EntryType type) {
        ensureLoaded();
        for (EmiBookmarkEntry entry : entries) {
            if (entry.getGroupId() == groupId && entry.getItemKey().equals(itemKey) && entry.getType() == type) {
                return entry;
            }
        }
        return null;
    }

    public synchronized EmiBookmarkEntry findEntry(Object favoriteHandle) {
        ensureLoaded();
        return favoriteBindings.get(favoriteHandle);
    }

    public synchronized void linkFavorite(Object favoriteHandle, EmiBookmarkEntry entry) {
        if (favoriteHandle != null && entry != null) {
            favoriteBindings.put(favoriteHandle, entry);
        }
    }

    public synchronized void unlinkFavorite(Object favoriteHandle) {
        if (favoriteHandle != null) {
            favoriteBindings.remove(favoriteHandle);
        }
    }

    public synchronized void clearFavoriteBindings() {
        favoriteBindings.clear();
    }

    public synchronized FavoriteSyncResult synchronizeFavorites(List<FavoriteHandleData> favorites) {
        ensureLoaded();
        List<FavoriteHandleData> safeFavorites = favorites == null ? List.of() : favorites;
        List<EmiBookmarkEntry> previousEntries = new ArrayList<>(entries);

        Set<Object> activeHandles = Collections.newSetFromMap(new IdentityHashMap<>());
        List<EmiBookmarkEntry> orderedEntries = new ArrayList<>(safeFavorites.size());
        Map<EmiBookmarkEntry, Object> entryToHandle = new IdentityHashMap<>();
        List<EmiBookmarkEntry> availableEntries = new ArrayList<>(entries);

        for (FavoriteHandleData favorite : safeFavorites) {
            if (favorite == null || favorite.handle() == null || favorite.itemKey() == null || favorite.itemKey().isBlank()) {
                continue;
            }
            activeHandles.add(favorite.handle());
            EmiBookmarkEntry entry = favoriteBindings.get(favorite.handle());
            boolean reusedBoundEntry = isCompatible(entry, favorite) && availableEntries.remove(entry);
            if (!reusedBoundEntry) {
                int targetGroupId = DEFAULT_GROUP_ID;
                EmiBookmarkEntry.EntryType targetType = favorite.type();
                if (entry != null) {
                    targetGroupId = entry.getGroupId();
                    targetType = entry.getType();
                    entries.remove(entry);
                    availableEntries.remove(entry);
                }
                EmiBookmarkEntry reboundEntry = findRebindCandidate(availableEntries, favorite);
                if (reboundEntry != null) {
                    entry = reboundEntry;
                    availableEntries.remove(reboundEntry);
                } else {
                    entry = createManagedEntry(targetGroupId, favorite.itemKey(), favorite.factor(), targetType);
                    entries.add(entry);
                    dirty = true;
                }
                favoriteBindings.put(favorite.handle(), entry);
            }
            orderedEntries.add(entry);
            entryToHandle.put(entry, favorite.handle());
        }

        Set<EmiBookmarkEntry> removedEntries = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<EmiBookmarkEntry> removedResults = Collections.newSetFromMap(new IdentityHashMap<>());
        favoriteBindings.entrySet().removeIf(binding -> {
            if (!activeHandles.contains(binding.getKey())) {
                EmiBookmarkEntry removedEntry = binding.getValue();
                removedEntries.add(removedEntry);
                if (removedEntry != null && removedEntry.isResult()) {
                    removedResults.add(removedEntry);
                }
                return true;
            }
            return false;
        });

        Set<Object> handlesToPrune = Collections.newSetFromMap(new IdentityHashMap<>());
        if (!removedResults.isEmpty()) {
            Set<EmiBookmarkEntry> additionalEntries = Collections.newSetFromMap(new IdentityHashMap<>());
            for (EmiBookmarkEntry removedResult : removedResults) {
                for (EmiBookmarkEntry recipeEntry : getRecipeUnitEntries(removedResult)) {
                    if (recipeEntry != removedResult) {
                        additionalEntries.add(recipeEntry);
                    }
                }
            }
            additionalEntries.removeAll(removedEntries);
            if (!additionalEntries.isEmpty()) {
                favoriteBindings.entrySet().removeIf(binding -> {
                    if (additionalEntries.contains(binding.getValue())) {
                        handlesToPrune.add(binding.getKey());
                        removedEntries.add(binding.getValue());
                        return true;
                    }
                    return false;
                });
                removedEntries.addAll(additionalEntries);
            }
        }

        if (!removedEntries.isEmpty()) {
            entries.removeIf(removedEntries::contains);
            for (EmiBookmarkEntry entry : removedEntries) {
                cleanupEmptyGroup(entry.getGroupId());
            }
            dirty = true;
            orderedEntries.removeIf(removedEntries::contains);
        }

        List<Object> orderedHandles = new ArrayList<>(orderedEntries.size());
        for (EmiBookmarkEntry entry : orderedEntries) {
            Object handle = entryToHandle.get(entry);
            if (handle == null) {
                continue;
            }
            orderedHandles.add(handle);
        }

        OrderNormalizationResult normalization = normalizeRecipeUnitDragOrder(previousEntries, orderedEntries, orderedHandles);
        List<EmiBookmarkEntry> finalOrderedEntries = normalization.orderedEntries();
        List<Object> finalOrderedHandles = normalization.orderedHandles();

        if (normalization.changed()) {
            orderedEntries = new ArrayList<>(finalOrderedEntries);
        }

        reorderEntries(orderedEntries);
        List<Object> handlesToReorder = normalization.changed() ? finalOrderedHandles : List.of();
        return new FavoriteSyncResult(new ArrayList<>(handlesToPrune), removedEntries.size(), handlesToReorder);
    }

    public synchronized boolean removeEntry(EmiBookmarkEntry entry) {
        ensureLoaded();
        if (entry == null || !entries.remove(entry)) {
            return false;
        }
        int affectedGroupId = entry.getGroupId();
        favoriteBindings.entrySet().removeIf(e -> e.getValue() == entry);
        recalculateCraftingChainInGroup(affectedGroupId);
        cleanupEmptyGroup(entry.getGroupId());
        dirty = true;
        return true;
    }

    public synchronized void removeGroup(int groupId) {
        ensureLoaded();
        List<EmiBookmarkEntry> removedEntries = new ArrayList<>();
        entries.removeIf(entry -> {
            boolean remove = entry.getGroupId() == groupId;
            if (remove) {
                removedEntries.add(entry);
            }
            return remove;
        });
        if (groupId != DEFAULT_GROUP_ID) {
            groups.remove(groupId);
        }
        if (!removedEntries.isEmpty()) {
            favoriteBindings.entrySet().removeIf(e -> removedEntries.contains(e.getValue()));
            dirty = true;
        }
        if (!groups.containsKey(DEFAULT_GROUP_ID)) {
            groups.put(DEFAULT_GROUP_ID, new EmiBookmarkGroup(DEFAULT_GROUP_ID));
        }
    }

    public synchronized void shiftEntryAmount(EmiBookmarkEntry entry, long shift) {
        ensureLoaded();
        if (entry == null || shift == 0L) {
            return;
        }
        if (entry.isResult() && entry.getGroupId() != DEFAULT_GROUP_ID) {
            if (shiftRecipeAmount(entry, shift)) {
                recalculateCraftingChainInGroup(entry.getGroupId());
                dirty = true;
            }
            return;
        }

        long before = entry.getMultiplier();
        entry.shiftMultiplier(shift);
        if (entry.getMultiplier() != before) {
            dirty = true;
        }
    }

    public synchronized void shiftGroupAmount(int groupId, long shift) {
        ensureLoaded();
        if (shift == 0L) {
            return;
        }
        List<EmiBookmarkEntry> groupEntries = getGroupEntries(groupId);
        if (groupEntries.isEmpty()) {
            return;
        }

        long currentMinMultiplier = Long.MAX_VALUE;
        for (EmiBookmarkEntry entry : groupEntries) {
            currentMinMultiplier = Math.min(currentMinMultiplier, entry.getMultiplier());
        }
        if (currentMinMultiplier == Long.MAX_VALUE) {
            currentMinMultiplier = 1L;
        }
        long nextMultiplier = Math.max(1L, currentMinMultiplier + shift);
        boolean changed = false;
        for (EmiBookmarkEntry entry : groupEntries) {
            if (entry.getMultiplier() != nextMultiplier) {
                entry.setMultiplier(nextMultiplier);
                changed = true;
            }
        }
        if (changed) {
            dirty = true;
        }
        recalculateCraftingChainInGroup(groupId);
    }

    public synchronized boolean setGroupExpanded(int groupId, boolean expanded) {
        ensureLoaded();
        EmiBookmarkGroup group = groups.get(groupId);
        if (group == null || group.isExpanded() == expanded) {
            return false;
        }
        group.setExpanded(expanded);
        dirty = true;
        return true;
    }

    public synchronized boolean setCraftingChainEnabled(int groupId, boolean enabled) {
        ensureLoaded();
        EmiBookmarkGroup group = groups.get(groupId);
        if (group == null || group.isCraftingChainEnabled() == enabled) {
            return false;
        }
        group.setCraftingChainEnabled(enabled);
        dirty = true;
        if (enabled) {
            recalculateCraftingChainInGroup(groupId);
        }
        return true;
    }

    public synchronized boolean moveEntriesToGroup(Collection<EmiBookmarkEntry> movingEntries, int targetGroupId) {
        ensureLoaded();
        if (movingEntries == null || movingEntries.isEmpty()) {
            return false;
        }

        int safeTargetGroupId = targetGroupId < 0 ? DEFAULT_GROUP_ID : targetGroupId;
        ensureGroupExists(safeTargetGroupId);

        Set<EmiBookmarkEntry> movingSet = Collections.newSetFromMap(new IdentityHashMap<>());
        movingSet.addAll(movingEntries);

        Set<Integer> touchedGroups = new LinkedHashSet<>();
        boolean changed = false;
        for (EmiBookmarkEntry entry : entries) {
            if (!movingSet.contains(entry)) {
                continue;
            }
            int oldGroupId = entry.getGroupId();
            touchedGroups.add(oldGroupId);
            if (oldGroupId != safeTargetGroupId) {
                entry.setGroupId(safeTargetGroupId);
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        for (int groupId : touchedGroups) {
            if (groupId != safeTargetGroupId) {
                cleanupEmptyGroup(groupId);
            }
        }
        Set<Integer> recalcTargets = new LinkedHashSet<>(touchedGroups);
        recalcTargets.add(safeTargetGroupId);
        for (int recalcGroupId : recalcTargets) {
            recalculateCraftingChainInGroup(recalcGroupId);
        }
        dirty = true;
        return true;
    }

    private boolean shiftRecipeAmount(EmiBookmarkEntry resultEntry, long shift) {
        if (resultEntry == null || !resultEntry.isResult()) {
            return false;
        }
        List<EmiBookmarkEntry> groupEntries = getGroupEntries(resultEntry.getGroupId());
        if (groupEntries.isEmpty()) {
            return false;
        }

        long currentMultiplier = resultEntry.getMultiplier();
        long nextMultiplier = shiftMultiplier(currentMultiplier, shift);
        if (nextMultiplier == currentMultiplier) {
            return false;
        }

        int resultCount = 0;
        for (EmiBookmarkEntry entry : groupEntries) {
            if (entry.isResult()) {
                resultCount++;
            }
        }

        boolean changed = false;
        if (resultCount <= 1) {
            for (EmiBookmarkEntry entry : groupEntries) {
                if (entry.getMultiplier() != nextMultiplier) {
                    entry.setMultiplier(nextMultiplier);
                    changed = true;
                }
            }
            return changed;
        }

        int index = groupEntries.indexOf(resultEntry);
        if (index < 0) {
            return false;
        }
        if (resultEntry.getMultiplier() != nextMultiplier) {
            resultEntry.setMultiplier(nextMultiplier);
            changed = true;
        }
        for (int i = index + 1; i < groupEntries.size(); i++) {
            EmiBookmarkEntry next = groupEntries.get(i);
            if (next.isResult() || next.getType() == EmiBookmarkEntry.EntryType.ITEM) {
                break;
            }
            if (next.isIngredient() && next.getMultiplier() != nextMultiplier) {
                next.setMultiplier(nextMultiplier);
                changed = true;
            }
        }
        return changed;
    }

    private static long shiftMultiplier(long multiplier, long shift) {
        long next = multiplier + shift;
        if (shift > 0L && next < multiplier) {
            next = Long.MAX_VALUE;
        } else if (shift < 0L && next > multiplier) {
            next = 1L;
        }
        return Math.max(1L, next);
    }

    private void recalculateCraftingChainInGroup(int groupId) {
        EmiBookmarkGroup group = groups.get(groupId);
        if (group == null || !group.isCraftingChainEnabled()) {
            return;
        }
        if (craftingChainCalculator.recalculateGroup(entries, groupId)) {
            dirty = true;
        }
    }

    public synchronized BookmarkStore.BookmarkSnapshot createSnapshot() {
        ensureLoaded();

        Map<Integer, EmiBookmarkGroup> groupCopies = new LinkedHashMap<>();
        for (Map.Entry<Integer, EmiBookmarkGroup> entry : groups.entrySet()) {
            groupCopies.put(entry.getKey(), entry.getValue().copy());
        }

        List<EmiBookmarkEntry> itemCopies = new ArrayList<>(entries.size());
        for (EmiBookmarkEntry entry : entries) {
            itemCopies.add(entry.copy());
        }

        int computedNextGroupId = Math.max(nextGroupId, maxGroupId(groupCopies) + 1);
        return new BookmarkStore.BookmarkSnapshot(computedNextGroupId, groupCopies, itemCopies);
    }

    public synchronized void applySnapshot(BookmarkStore.BookmarkSnapshot snapshot) {
        BookmarkStore.BookmarkSnapshot safeSnapshot = snapshot == null
                ? BookmarkStore.BookmarkSnapshot.empty()
                : snapshot;

        entries.clear();
        groups.clear();
        favoriteBindings.clear();

        groups.put(DEFAULT_GROUP_ID, new EmiBookmarkGroup(DEFAULT_GROUP_ID));
        for (Map.Entry<Integer, EmiBookmarkGroup> entry : safeSnapshot.groups().entrySet()) {
            groups.put(entry.getKey(), entry.getValue().copy());
        }

        for (EmiBookmarkEntry entry : safeSnapshot.items()) {
            EmiBookmarkEntry item = entry.copy();
            groups.putIfAbsent(item.getGroupId(), new EmiBookmarkGroup(item.getGroupId()));
            entries.add(item);
        }

        nextGroupId = Math.max(safeSnapshot.nextGroupId(), maxGroupId(groups) + 1);
        currentAddingGroupId = groups.containsKey(currentAddingGroupId) ? currentAddingGroupId : DEFAULT_GROUP_ID;
        loaded = true;
        dirty = false;
    }

    private void cleanupEmptyGroup(int groupId) {
        if (groupId == DEFAULT_GROUP_ID) {
            return;
        }
        for (EmiBookmarkEntry other : entries) {
            if (other.getGroupId() == groupId) {
                return;
            }
        }
        groups.remove(groupId);
    }

    private void ensureGroupExists(int groupId) {
        if (!groups.containsKey(groupId)) {
            groups.put(groupId, new EmiBookmarkGroup(groupId));
            if (groupId < Integer.MAX_VALUE) {
                nextGroupId = Math.max(nextGroupId, groupId + 1);
            }
        }
    }

    private EmiBookmarkEntry createManagedEntry(int groupId, String itemKey, long factor, EmiBookmarkEntry.EntryType type) {
        ensureGroupExists(groupId);
        return new EmiBookmarkEntry(groupId, itemKey, factor, type);
    }

    private boolean isCompatible(EmiBookmarkEntry existing, FavoriteHandleData favorite) {
        if (existing == null) {
            return false;
        }
        return entries.contains(existing)
                && existing.getItemKey().equals(favorite.itemKey());
    }

    private EmiBookmarkEntry findRebindCandidate(List<EmiBookmarkEntry> availableEntries, FavoriteHandleData favorite) {
        EmiBookmarkEntry strict = findRebindCandidate(availableEntries, favorite, true);
        if (strict != null) {
            return strict;
        }
        if (favorite.hasRecipeContext() && favorite.type() == EmiBookmarkEntry.EntryType.ITEM) {
            EmiBookmarkEntry strictResult = findRebindCandidate(availableEntries, favorite,
                    EmiBookmarkEntry.EntryType.RESULT, true);
            if (strictResult != null) {
                return strictResult;
            }
        }
        EmiBookmarkEntry loose = findRebindCandidate(availableEntries, favorite, false);
        if (loose != null) {
            return loose;
        }
        if (favorite.hasRecipeContext() && favorite.type() == EmiBookmarkEntry.EntryType.ITEM) {
            return findRebindCandidate(availableEntries, favorite, EmiBookmarkEntry.EntryType.RESULT, false);
        }
        return null;
    }

    private static EmiBookmarkEntry findRebindCandidate(List<EmiBookmarkEntry> availableEntries,
                                                        FavoriteHandleData favorite,
                                                        EmiBookmarkEntry.EntryType expectedType,
                                                        boolean strictFactor) {
        for (EmiBookmarkEntry candidate : availableEntries) {
            if (isRebindCompatible(candidate, favorite, expectedType, strictFactor)) {
                return candidate;
            }
        }
        return null;
    }

    private static EmiBookmarkEntry findRebindCandidate(List<EmiBookmarkEntry> availableEntries,
                                                        FavoriteHandleData favorite,
                                                        boolean strictFactor) {
        return findRebindCandidate(availableEntries, favorite, favorite.type(), strictFactor);
    }

    private static boolean isRebindCompatible(EmiBookmarkEntry candidate,
                                              FavoriteHandleData favorite,
                                              EmiBookmarkEntry.EntryType expectedType,
                                              boolean strictFactor) {
        if (candidate == null) {
            return false;
        }
        if (!candidate.getItemKey().equals(favorite.itemKey())) {
            return false;
        }
        if (strictFactor && candidate.getFactor() != favorite.factor()) {
            return false;
        }
        return switch (expectedType) {
            case RESULT -> candidate.isResult();
            case INGREDIENT -> candidate.isIngredient();
            case ITEM -> !candidate.isResult();
        };
    }

    private static boolean isRebindCompatible(EmiBookmarkEntry candidate,
                                              FavoriteHandleData favorite,
                                              boolean strictFactor) {
        return isRebindCompatible(candidate, favorite, favorite.type(), strictFactor);
    }

    private List<EmiBookmarkEntry> getRecipeUnitEntries(EmiBookmarkEntry resultEntry) {
        if (resultEntry == null || !resultEntry.isResult()) {
            return List.of();
        }
        int index = entries.indexOf(resultEntry);
        if (index < 0) {
            return List.of(resultEntry);
        }
        int groupId = resultEntry.getGroupId();
        List<EmiBookmarkEntry> unitEntries = new ArrayList<>();
        unitEntries.add(resultEntry);
        for (int i = index + 1; i < entries.size(); i++) {
            EmiBookmarkEntry entry = entries.get(i);
            if (entry.getGroupId() != groupId) {
                break;
            }
            if (entry.isResult() || entry.getType() == EmiBookmarkEntry.EntryType.ITEM) {
                break;
            }
            if (entry.isIngredient()) {
                unitEntries.add(entry);
                continue;
            }
            break;
        }
        return unitEntries;
    }

    private OrderNormalizationResult normalizeRecipeUnitDragOrder(List<EmiBookmarkEntry> previousEntries,
                                                                  List<EmiBookmarkEntry> orderedEntries,
                                                                  List<Object> orderedHandles) {
        if (orderedEntries == null
                || orderedEntries.isEmpty()
                || orderedHandles == null
                || orderedEntries.size() != orderedHandles.size()) {
            return OrderNormalizationResult.unchanged(orderedEntries, orderedHandles);
        }

        Set<EmiBookmarkEntry> orderedSet = Collections.newSetFromMap(new IdentityHashMap<>());
        orderedSet.addAll(orderedEntries);
        List<EmiBookmarkEntry> filteredPrevious = new ArrayList<>(orderedEntries.size());
        for (EmiBookmarkEntry entry : previousEntries) {
            if (orderedSet.contains(entry)) {
                filteredPrevious.add(entry);
            }
        }
        if (filteredPrevious.size() != orderedEntries.size() || !sameIdentitySet(filteredPrevious, orderedEntries)) {
            return OrderNormalizationResult.unchanged(orderedEntries, orderedHandles);
        }

        EntryMove detectedMove = detectSingleEntryMove(filteredPrevious, orderedEntries);
        if (detectedMove == null || detectedMove.entry() == null) {
            return OrderNormalizationResult.unchanged(orderedEntries, orderedHandles);
        }

        RecipeUnitLayout unitLayout = buildRecipeUnitLayout(filteredPrevious);
        List<EmiBookmarkEntry> movingUnit = unitLayout.unitByEntry().get(detectedMove.entry());
        if (movingUnit == null || movingUnit.size() <= 1) {
            return OrderNormalizationResult.unchanged(orderedEntries, orderedHandles);
        }

        Set<EmiBookmarkEntry> movingSet = Collections.newSetFromMap(new IdentityHashMap<>());
        movingSet.addAll(movingUnit);

        int movedIndex = orderedEntries.indexOf(detectedMove.entry());
        if (movedIndex < 0) {
            return OrderNormalizationResult.unchanged(orderedEntries, orderedHandles);
        }

        EmiBookmarkEntry previousExternal = null;
        for (int index = movedIndex - 1; index >= 0; index--) {
            EmiBookmarkEntry candidate = orderedEntries.get(index);
            if (!movingSet.contains(candidate)) {
                previousExternal = candidate;
                break;
            }
        }

        EmiBookmarkEntry nextExternal = null;
        for (int index = movedIndex + 1; index < orderedEntries.size(); index++) {
            EmiBookmarkEntry candidate = orderedEntries.get(index);
            if (!movingSet.contains(candidate)) {
                nextExternal = candidate;
                break;
            }
        }

        List<List<EmiBookmarkEntry>> normalizedUnits = new ArrayList<>(unitLayout.units().size());
        for (List<EmiBookmarkEntry> unit : unitLayout.units()) {
            normalizedUnits.add(new ArrayList<>(unit));
        }

        int sourceUnitIndex = findUnitIndexContaining(normalizedUnits, detectedMove.entry());
        if (sourceUnitIndex < 0) {
            return OrderNormalizationResult.unchanged(orderedEntries, orderedHandles);
        }
        List<EmiBookmarkEntry> detachedUnit = normalizedUnits.remove(sourceUnitIndex);

        int insertIndex = normalizedUnits.size();
        if (previousExternal != null) {
            int previousUnitIndex = findUnitIndexContaining(normalizedUnits, previousExternal);
            if (previousUnitIndex >= 0) {
                insertIndex = previousUnitIndex + 1;
            }
        } else if (nextExternal != null) {
            int nextUnitIndex = findUnitIndexContaining(normalizedUnits, nextExternal);
            if (nextUnitIndex >= 0) {
                insertIndex = nextUnitIndex;
            }
        } else {
            insertIndex = 0;
        }
        insertIndex = Math.max(0, Math.min(insertIndex, normalizedUnits.size()));
        normalizedUnits.add(insertIndex, detachedUnit);

        List<EmiBookmarkEntry> normalizedEntries = flattenUnits(normalizedUnits);
        if (normalizedEntries.size() != orderedEntries.size() || sameOrder(orderedEntries, normalizedEntries)) {
            return OrderNormalizationResult.unchanged(orderedEntries, orderedHandles);
        }

        Map<EmiBookmarkEntry, Object> handlesByEntry = new IdentityHashMap<>();
        for (int index = 0; index < orderedEntries.size(); index++) {
            handlesByEntry.put(orderedEntries.get(index), orderedHandles.get(index));
        }

        List<Object> normalizedHandles = new ArrayList<>(normalizedEntries.size());
        for (EmiBookmarkEntry entry : normalizedEntries) {
            Object handle = handlesByEntry.get(entry);
            if (handle == null) {
                return OrderNormalizationResult.unchanged(orderedEntries, orderedHandles);
            }
            normalizedHandles.add(handle);
        }

        if (sameIdentityOrder(orderedHandles, normalizedHandles)) {
            return OrderNormalizationResult.unchanged(orderedEntries, orderedHandles);
        }
        return OrderNormalizationResult.changed(normalizedEntries, normalizedHandles);
    }

    private static EntryMove detectSingleEntryMove(List<EmiBookmarkEntry> before, List<EmiBookmarkEntry> after) {
        if (before == null || after == null || before.size() != after.size()) {
            return null;
        }
        int firstMismatch = -1;
        int lastMismatch = -1;
        for (int index = 0; index < before.size(); index++) {
            if (before.get(index) != after.get(index)) {
                if (firstMismatch < 0) {
                    firstMismatch = index;
                }
                lastMismatch = index;
            }
        }

        if (firstMismatch < 0 || lastMismatch < 0) {
            return null;
        }
        if (before.get(firstMismatch) == after.get(lastMismatch)) {
            boolean validForwardMove = true;
            for (int index = firstMismatch; index < lastMismatch; index++) {
                if (before.get(index + 1) != after.get(index)) {
                    validForwardMove = false;
                    break;
                }
            }
            if (validForwardMove) {
                return new EntryMove(before.get(firstMismatch), lastMismatch);
            }
        }
        if (before.get(lastMismatch) == after.get(firstMismatch)) {
            boolean validBackwardMove = true;
            for (int index = firstMismatch; index < lastMismatch; index++) {
                if (before.get(index) != after.get(index + 1)) {
                    validBackwardMove = false;
                    break;
                }
            }
            if (validBackwardMove) {
                return new EntryMove(before.get(lastMismatch), firstMismatch);
            }
        }

        Map<EmiBookmarkEntry, Integer> beforeIndex = new IdentityHashMap<>();
        for (int index = 0; index < before.size(); index++) {
            beforeIndex.put(before.get(index), index);
        }
        EntryMove best = null;
        int bestDistance = 0;
        for (int index = 0; index < after.size(); index++) {
            EmiBookmarkEntry entry = after.get(index);
            Integer previousIndex = beforeIndex.get(entry);
            if (previousIndex == null) {
                return null;
            }
            int distance = Math.abs(previousIndex - index);
            if (distance > bestDistance) {
                bestDistance = distance;
                best = new EntryMove(entry, index);
            }
        }
        if (bestDistance <= 0) {
            return null;
        }
        return best;
    }

    private static RecipeUnitLayout buildRecipeUnitLayout(List<EmiBookmarkEntry> orderedEntries) {
        List<List<EmiBookmarkEntry>> units = new ArrayList<>();
        Map<EmiBookmarkEntry, List<EmiBookmarkEntry>> unitByEntry = new IdentityHashMap<>();

        List<EmiBookmarkEntry> currentRecipeUnit = null;
        int currentRecipeGroupId = DEFAULT_GROUP_ID;
        for (EmiBookmarkEntry entry : orderedEntries) {
            if (entry.isResult()) {
                currentRecipeUnit = new ArrayList<>();
                currentRecipeUnit.add(entry);
                units.add(currentRecipeUnit);
                currentRecipeGroupId = entry.getGroupId();
                continue;
            }
            if (entry.isIngredient() && currentRecipeUnit != null && entry.getGroupId() == currentRecipeGroupId) {
                currentRecipeUnit.add(entry);
                continue;
            }

            currentRecipeUnit = null;
            currentRecipeGroupId = DEFAULT_GROUP_ID;
            List<EmiBookmarkEntry> singleton = new ArrayList<>(1);
            singleton.add(entry);
            units.add(singleton);
        }

        for (List<EmiBookmarkEntry> unit : units) {
            for (EmiBookmarkEntry entry : unit) {
                unitByEntry.put(entry, unit);
            }
        }
        return new RecipeUnitLayout(units, unitByEntry);
    }

    private static int findUnitIndexContaining(List<List<EmiBookmarkEntry>> units, EmiBookmarkEntry entry) {
        if (entry == null) {
            return -1;
        }
        for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
            for (EmiBookmarkEntry member : units.get(unitIndex)) {
                if (member == entry) {
                    return unitIndex;
                }
            }
        }
        return -1;
    }

    private static List<EmiBookmarkEntry> flattenUnits(List<List<EmiBookmarkEntry>> units) {
        List<EmiBookmarkEntry> flattened = new ArrayList<>();
        for (List<EmiBookmarkEntry> unit : units) {
            flattened.addAll(unit);
        }
        return flattened;
    }

    private void reorderEntries(List<EmiBookmarkEntry> orderedEntries) {
        Set<EmiBookmarkEntry> orderedSet = Collections.newSetFromMap(new IdentityHashMap<>());
        orderedSet.addAll(orderedEntries);

        List<EmiBookmarkEntry> merged = new ArrayList<>(entries.size());
        merged.addAll(orderedEntries);
        for (EmiBookmarkEntry entry : entries) {
            if (!orderedSet.contains(entry)) {
                merged.add(entry);
            }
        }

        if (!sameOrder(entries, merged)) {
            entries.clear();
            entries.addAll(merged);
            dirty = true;
        }
    }

    private static boolean sameOrder(List<EmiBookmarkEntry> left, List<EmiBookmarkEntry> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i) != right.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameIdentitySet(List<EmiBookmarkEntry> left, List<EmiBookmarkEntry> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        Set<EmiBookmarkEntry> uniqueLeft = Collections.newSetFromMap(new IdentityHashMap<>());
        uniqueLeft.addAll(left);
        if (uniqueLeft.size() != left.size()) {
            return false;
        }
        for (EmiBookmarkEntry entry : right) {
            if (!uniqueLeft.remove(entry)) {
                return false;
            }
        }
        return uniqueLeft.isEmpty();
    }

    private static boolean sameIdentityOrder(List<?> left, List<?> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (left.get(index) != right.get(index)) {
                return false;
            }
        }
        return true;
    }

    private void resetToDefaults() {
        entries.clear();
        groups.clear();
        favoriteBindings.clear();
        groups.put(DEFAULT_GROUP_ID, new EmiBookmarkGroup(DEFAULT_GROUP_ID));
        nextGroupId = DEFAULT_GROUP_ID + 1;
        currentAddingGroupId = DEFAULT_GROUP_ID;
        allowDuplicates = false;
        loaded = false;
        dirty = false;
    }

    private static int maxGroupId(Map<Integer, EmiBookmarkGroup> groups) {
        int max = DEFAULT_GROUP_ID;
        for (int groupId : groups.keySet()) {
            if (groupId > max) {
                max = groupId;
            }
        }
        return max;
    }

    public record FavoriteHandleData(Object handle,
                                     String itemKey,
                                     long factor,
                                     EmiBookmarkEntry.EntryType type,
                                     boolean hasRecipeContext) {
        public FavoriteHandleData {
            factor = Math.max(1L, factor);
            type = type == null ? EmiBookmarkEntry.EntryType.ITEM : type;
        }
    }

    public record FavoriteSyncResult(List<Object> handlesToPrune, int removedEntryCount, List<Object> reorderedHandles) {
        public FavoriteSyncResult {
            handlesToPrune = handlesToPrune == null ? List.of() : List.copyOf(handlesToPrune);
            reorderedHandles = reorderedHandles == null ? List.of() : List.copyOf(reorderedHandles);
        }
    }

    private record EntryMove(EmiBookmarkEntry entry, int targetIndex) {
    }

    private record RecipeUnitLayout(List<List<EmiBookmarkEntry>> units,
                                    Map<EmiBookmarkEntry, List<EmiBookmarkEntry>> unitByEntry) {
        private RecipeUnitLayout {
            units = units == null ? List.of() : List.copyOf(units);
            unitByEntry = unitByEntry == null ? Map.of() : Map.copyOf(unitByEntry);
        }
    }

    private record OrderNormalizationResult(List<EmiBookmarkEntry> orderedEntries,
                                            List<Object> orderedHandles,
                                            boolean changed) {
        private OrderNormalizationResult {
            orderedEntries = orderedEntries == null ? List.of() : List.copyOf(orderedEntries);
            orderedHandles = orderedHandles == null ? List.of() : List.copyOf(orderedHandles);
        }

        private static OrderNormalizationResult unchanged(List<EmiBookmarkEntry> orderedEntries,
                                                          List<Object> orderedHandles) {
            return new OrderNormalizationResult(orderedEntries, orderedHandles, false);
        }

        private static OrderNormalizationResult changed(List<EmiBookmarkEntry> orderedEntries,
                                                        List<Object> orderedHandles) {
            return new OrderNormalizationResult(orderedEntries, orderedHandles, true);
        }
    }
}
