package net.kogepan.emi_bookmark_enhancements.bookmark.model;

import java.util.Objects;

public final class EmiBookmarkEntry {
    public enum EntryType {
        ITEM,
        RESULT,
        INGREDIENT;

        public static EntryType fromOrdinal(int ordinal) {
            EntryType[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return ITEM;
            }
            return values[ordinal];
        }
    }

    private int groupId;
    private final String itemKey;
    private final long factor;
    private long amount;
    private EntryType type;

    public EmiBookmarkEntry(int groupId, String itemKey, long factor, EntryType type) {
        this.groupId = groupId;
        this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
        this.factor = Math.max(1L, factor);
        this.amount = this.factor;
        this.type = Objects.requireNonNull(type, "type");
    }

    public EmiBookmarkEntry copy() {
        EmiBookmarkEntry copy = new EmiBookmarkEntry(groupId, itemKey, factor, type);
        copy.setAmount(amount);
        return copy;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getItemKey() {
        return itemKey;
    }

    public long getFactor() {
        return factor;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = Math.max(factor, amount);
    }

    public EntryType getType() {
        return type;
    }

    public void setType(EntryType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public boolean isResult() {
        return type == EntryType.RESULT;
    }

    public boolean isIngredient() {
        return type == EntryType.INGREDIENT;
    }

    public long getMultiplier() {
        long quotient = amount / factor;
        return amount % factor == 0L ? quotient : quotient + 1L;
    }

    public void setMultiplier(long multiplier) {
        long safeMultiplier = Math.max(1L, multiplier);
        this.amount = saturatingMultiply(factor, safeMultiplier);
    }

    public long shiftMultiplier(long shift) {
        long current = getMultiplier();
        long next = current + shift;
        if (shift > 0 && next < current) {
            next = Long.MAX_VALUE;
        } else if (shift < 0 && next > current) {
            next = 1L;
        }
        next = Math.max(1L, next);
        setMultiplier(next);
        return next;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }
}
