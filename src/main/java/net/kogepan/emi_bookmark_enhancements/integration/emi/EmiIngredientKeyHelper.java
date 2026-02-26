package net.kogepan.emi_bookmark_enhancements.integration.emi;

import com.google.gson.JsonElement;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;

import java.util.List;

public final class EmiIngredientKeyHelper {
    private EmiIngredientKeyHelper() {
    }

    public static String toItemKey(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return "";
        }

        JsonElement serialized = EmiIngredientSerializer.getSerialized(ingredient);
        if (serialized != null) {
            return serialized.toString();
        }

        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (!stacks.isEmpty()) {
            JsonElement stackSerialized = EmiIngredientSerializer.getSerialized(stacks.get(0));
            if (stackSerialized != null) {
                return stackSerialized.toString();
            }
            return stacks.get(0).toString();
        }
        return ingredient.toString();
    }

    public static long toBaseAmount(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return 1L;
        }
        long amount = ingredient.getAmount();
        if (amount > 0L) {
            return amount;
        }
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (!stacks.isEmpty() && stacks.get(0).getAmount() > 0L) {
            return stacks.get(0).getAmount();
        }
        return 1L;
    }
}
