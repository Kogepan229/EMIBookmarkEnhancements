package net.kogepan.emi_bookmark_enhancements.bookmark.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kogepan.emi_bookmark_enhancements.EmiBookmarkEnhancements;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkGroup;
import net.kogepan.emi_bookmark_enhancements.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BookmarkStore {
    private static final int DEFAULT_GROUP_ID = 0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public BookmarkSnapshot load() {
        Path path = getSaveFilePath();
        if (!Files.exists(path)) {
            return BookmarkSnapshot.empty();
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            int nextGroupId = readInt(root, "nextGroupId", DEFAULT_GROUP_ID + 1);

            Map<Integer, EmiBookmarkGroup> groups = new LinkedHashMap<>();
            groups.put(DEFAULT_GROUP_ID, new EmiBookmarkGroup(DEFAULT_GROUP_ID));

            JsonObject groupsJson = readObject(root, "groups");
            if (groupsJson != null) {
                for (Map.Entry<String, JsonElement> entry : groupsJson.entrySet()) {
                    JsonObject groupJson = asObject(entry.getValue());
                    if (groupJson == null) {
                        continue;
                    }
                    int groupId;
                    try {
                        groupId = Integer.parseInt(entry.getKey());
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    EmiBookmarkGroup group = new EmiBookmarkGroup(groupId);
                    group.setExpanded(readBoolean(groupJson, "expanded", true));
                    group.setCraftingChainEnabled(readBoolean(groupJson, "craftingChain", false));
                    group.setLinkedGroupId(readInt(groupJson, "linkedGroupId", -1));
                    groups.put(groupId, group);
                }
            }

            List<EmiBookmarkEntry> items = new ArrayList<>();
            JsonArray itemsJson = readArray(root, "items");
            if (itemsJson != null) {
                for (JsonElement element : itemsJson) {
                    JsonObject itemJson = asObject(element);
                    if (itemJson == null) {
                        continue;
                    }
                    String itemKey = readString(itemJson, "itemKey", "");
                    if (itemKey.isBlank()) {
                        continue;
                    }
                    int groupId = readInt(itemJson, "groupId", DEFAULT_GROUP_ID);
                    long factor = readLong(itemJson, "factor",
                            readLong(itemJson, "baseQuantity", 1L));
                    long amount = readLong(itemJson, "amount", factor);
                    EmiBookmarkEntry.EntryType type = EmiBookmarkEntry.EntryType.fromOrdinal(
                            readInt(itemJson, "type", EmiBookmarkEntry.EntryType.ITEM.ordinal()));

                    EmiBookmarkEntry item = new EmiBookmarkEntry(groupId, itemKey, factor, type);
                    item.setAmount(amount);
                    items.add(item);
                    groups.putIfAbsent(groupId, new EmiBookmarkGroup(groupId));
                }
            }

            int maxGroupId = DEFAULT_GROUP_ID;
            for (int groupId : groups.keySet()) {
                if (groupId > maxGroupId) {
                    maxGroupId = groupId;
                }
            }
            nextGroupId = Math.max(nextGroupId, maxGroupId + 1);
            return new BookmarkSnapshot(nextGroupId, groups, items);
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.error("Failed to load bookmark store", e);
            return BookmarkSnapshot.empty();
        }
    }

    public boolean save(BookmarkSnapshot snapshot) {
        Path path = getSaveFilePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("nextGroupId", snapshot.nextGroupId());

            JsonObject groupsJson = new JsonObject();
            for (EmiBookmarkGroup group : snapshot.groups().values()) {
                JsonObject groupJson = new JsonObject();
                groupJson.addProperty("expanded", group.isExpanded());
                groupJson.addProperty("craftingChain", group.isCraftingChainEnabled());
                groupJson.addProperty("linkedGroupId", group.getLinkedGroupId());
                groupsJson.add(String.valueOf(group.getGroupId()), groupJson);
            }
            root.add("groups", groupsJson);

            JsonArray itemsJson = new JsonArray();
            for (EmiBookmarkEntry item : snapshot.items()) {
                JsonObject itemJson = new JsonObject();
                itemJson.addProperty("groupId", item.getGroupId());
                itemJson.addProperty("itemKey", item.getItemKey());
                itemJson.addProperty("factor", item.getFactor());
                itemJson.addProperty("amount", item.getAmount());
                itemJson.addProperty("type", item.getType().ordinal());
                itemsJson.add(itemJson);
            }
            root.add("items", itemsJson);

            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.error("Failed to save bookmark store", e);
            return false;
        }
    }

    private Path getSaveFilePath() {
        return FMLPaths.CONFIGDIR.get().resolve(ModConfig.BOOKMARK_STORE_FILE);
    }

    private static JsonObject readObject(JsonObject object, String key) {
        if (!object.has(key)) {
            return null;
        }
        return asObject(object.get(key));
    }

    private static JsonArray readArray(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(key);
    }

    private static JsonObject asObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        try {
            if (object.has(key)) {
                return object.get(key).getAsInt();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static long readLong(JsonObject object, String key, long fallback) {
        try {
            if (object.has(key)) {
                return object.get(key).getAsLong();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        try {
            if (object.has(key)) {
                return object.get(key).getAsBoolean();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static String readString(JsonObject object, String key, String fallback) {
        try {
            if (object.has(key)) {
                return object.get(key).getAsString();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public record BookmarkSnapshot(int nextGroupId, Map<Integer, EmiBookmarkGroup> groups, List<EmiBookmarkEntry> items) {
        public BookmarkSnapshot {
            nextGroupId = Math.max(DEFAULT_GROUP_ID + 1, nextGroupId);
            groups = groups == null ? new LinkedHashMap<>() : new LinkedHashMap<>(groups);
            items = items == null ? new ArrayList<>() : new ArrayList<>(items);
            groups.putIfAbsent(DEFAULT_GROUP_ID, new EmiBookmarkGroup(DEFAULT_GROUP_ID));
        }

        public static BookmarkSnapshot empty() {
            Map<Integer, EmiBookmarkGroup> groups = new LinkedHashMap<>();
            groups.put(DEFAULT_GROUP_ID, new EmiBookmarkGroup(DEFAULT_GROUP_ID));
            return new BookmarkSnapshot(DEFAULT_GROUP_ID + 1, groups, List.of());
        }
    }
}
