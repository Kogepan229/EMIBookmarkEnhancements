package net.kogepan.emi_bookmark_enhancements.bookmark.model;

public final class EmiBookmarkGroup {
    private final int groupId;
    private int linkedGroupId = -1;
    private boolean expanded = true;
    private boolean craftingChainEnabled;

    public EmiBookmarkGroup(int groupId) {
        this.groupId = groupId;
    }

    public EmiBookmarkGroup copy() {
        EmiBookmarkGroup copy = new EmiBookmarkGroup(groupId);
        copy.setLinkedGroupId(linkedGroupId);
        copy.setExpanded(expanded);
        copy.setCraftingChainEnabled(craftingChainEnabled);
        return copy;
    }

    public int getGroupId() {
        return groupId;
    }

    public int getLinkedGroupId() {
        return linkedGroupId;
    }

    public void setLinkedGroupId(int linkedGroupId) {
        this.linkedGroupId = linkedGroupId;
    }

    public boolean hasLink() {
        return linkedGroupId >= 0;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void toggleExpanded() {
        expanded = !expanded;
    }

    public boolean isCraftingChainEnabled() {
        return craftingChainEnabled;
    }

    public void setCraftingChainEnabled(boolean craftingChainEnabled) {
        this.craftingChainEnabled = craftingChainEnabled;
    }

    public void toggleCraftingChain() {
        craftingChainEnabled = !craftingChainEnabled;
    }
}
