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

        Set<Object> activeHandles = Collections.newSetFromMap(new IdentityHashMap<>());
        List<EmiBookmarkEntry> orderedEntries = new ArrayList<>(safeFavorites.size());

        for (FavoriteHandleData favorite : safeFavorites) {
            if (favorite == null || favorite.handle() == null || favorite.itemKey() == null || favorite.itemKey().isBlank()) {
                continue;
            }
            activeHandles.add(favorite.handle());
            EmiBookmarkEntry entry = favoriteBindings.get(favorite.handle());
            if (!isCompatible(entry, favorite)) {
                int targetGroupId = DEFAULT_GROUP_ID;
                EmiBookmarkEntry.EntryType targetType = favorite.type();
                if (entry != null) {
                    targetGroupId = entry.getGroupId();
                    targetType = entry.getType();
                    entries.remove(entry);
                }
                entry = createManagedEntry(targetGroupId, favorite.itemKey(), favorite.factor(), targetType);
                entries.add(entry);
                favoriteBindings.put(favorite.handle(), entry);
                dirty = true;
            }
            orderedEntries.add(entry);
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
        }

        reorderEntries(orderedEntries);
        return new FavoriteSyncResult(new ArrayList<>(handlesToPrune), removedEntries.size());
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

    public record FavoriteHandleData(Object handle, String itemKey, long factor, EmiBookmarkEntry.EntryType type) {
        public FavoriteHandleData {
            factor = Math.max(1L, factor);
            type = type == null ? EmiBookmarkEntry.EntryType.ITEM : type;
        }
    }

    public record FavoriteSyncResult(List<Object> handlesToPrune, int removedEntryCount) {
        public FavoriteSyncResult {
            handlesToPrune = handlesToPrune == null ? List.of() : List.copyOf(handlesToPrune);
        }
    }
}
