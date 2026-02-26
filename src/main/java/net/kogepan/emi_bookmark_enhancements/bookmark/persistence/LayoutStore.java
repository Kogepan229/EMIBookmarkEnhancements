package net.kogepan.emi_bookmark_enhancements.bookmark.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kogepan.emi_bookmark_enhancements.EmiBookmarkEnhancements;
import net.kogepan.emi_bookmark_enhancements.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LayoutStore {
    private static final String DEFAULT_LAYOUT_MODE = "HORIZONTAL";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String loadModeName() {
        Path path = getSaveFilePath();
        if (!Files.exists(path)) {
            return DEFAULT_LAYOUT_MODE;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("layoutMode")) {
                String modeName = root.get("layoutMode").getAsString();
                if (!modeName.isBlank()) {
                    return modeName;
                }
            }
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.error("Failed to load layout store", e);
        }
        return DEFAULT_LAYOUT_MODE;
    }

    public boolean saveModeName(String modeName) {
        String safeMode = modeName == null || modeName.isBlank()
                ? DEFAULT_LAYOUT_MODE
                : modeName;
        Path path = getSaveFilePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("layoutMode", safeMode);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            EmiBookmarkEnhancements.LOGGER.error("Failed to save layout store", e);
            return false;
        }
    }

    private Path getSaveFilePath() {
        return FMLPaths.CONFIGDIR.get().resolve(ModConfig.LAYOUT_STORE_FILE);
    }
}
