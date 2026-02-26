package net.kogepan.emi_bookmark_enhancements.integration.emi;

import com.google.gson.JsonElement;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;

import java.lang.reflect.Method;
import java.util.List;

public final class EmiIngredientKeyHelper {
    private static final String EMI_FAVORITE_CLASS = "dev.emi.emi.runtime.EmiFavorite";
    private static Class<?> emiFavoriteClass;
    private static Method emiFavoriteGetStackMethod;
    private static boolean favoriteLookupFailed;

    private EmiIngredientKeyHelper() {
    }

    public static String toItemKey(EmiIngredient ingredient) {
        EmiIngredient normalized = normalize(ingredient);
        if (normalized == null || normalized.isEmpty()) {
            return "";
        }

        JsonElement serialized = EmiIngredientSerializer.getSerialized(normalized);
        if (serialized != null) {
            return serialized.toString();
        }

        List<EmiStack> stacks = normalized.getEmiStacks();
        if (!stacks.isEmpty()) {
            JsonElement stackSerialized = EmiIngredientSerializer.getSerialized(stacks.get(0));
            if (stackSerialized != null) {
                return stackSerialized.toString();
            }
            return stacks.get(0).toString();
        }
        return normalized.toString();
    }

    public static long toBaseAmount(EmiIngredient ingredient) {
        EmiIngredient normalized = normalize(ingredient);
        if (normalized == null || normalized.isEmpty()) {
            return 1L;
        }
        long amount = normalized.getAmount();
        if (amount > 0L) {
            return amount;
        }
        List<EmiStack> stacks = normalized.getEmiStacks();
        if (!stacks.isEmpty() && stacks.get(0).getAmount() > 0L) {
            return stacks.get(0).getAmount();
        }
        return 1L;
    }

    private static EmiIngredient normalize(EmiIngredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        EmiIngredient unwrapped = unwrapFavoriteIngredient(ingredient);
        return unwrapped == null ? ingredient : unwrapped;
    }

    private static EmiIngredient unwrapFavoriteIngredient(EmiIngredient ingredient) {
        if (!resolveFavoriteHandles() || !emiFavoriteClass.isInstance(ingredient)) {
            return ingredient;
        }
        try {
            Object value = emiFavoriteGetStackMethod.invoke(ingredient);
            if (value instanceof EmiIngredient wrapped) {
                return wrapped;
            }
        } catch (Exception ignored) {
        }
        return ingredient;
    }

    private static boolean resolveFavoriteHandles() {
        if (emiFavoriteClass != null && emiFavoriteGetStackMethod != null) {
            return true;
        }
        if (favoriteLookupFailed) {
            return false;
        }
        try {
            emiFavoriteClass = Class.forName(EMI_FAVORITE_CLASS);
            emiFavoriteGetStackMethod = emiFavoriteClass.getMethod("getStack");
            return true;
        } catch (Exception e) {
            favoriteLookupFailed = true;
            return false;
        }
    }
}
