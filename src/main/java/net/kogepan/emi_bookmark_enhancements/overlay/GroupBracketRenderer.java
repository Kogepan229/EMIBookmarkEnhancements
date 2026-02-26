package net.kogepan.emi_bookmark_enhancements.overlay;

import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkGroup;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.IntFunction;

public final class GroupBracketRenderer {
    public static final int GROUP_PANEL_WIDTH = 8;
    public static final int HOVER_HIGHLIGHT_COLOR = 0x80FFFFFF;
    public static final int DRAG_INCLUDE_COLOR = 0x6045DA75;
    public static final int DRAG_EXCLUDE_COLOR = 0x60FF4444;

    private static final int GROUP_CHAIN_COLOR = 0xFF45DA75;
    private static final int GROUP_NONE_COLOR = 0xFF666666;
    private static final int GROUP_LINKED_COLOR = 0xFFAAAAAA;

    private GroupBracketRenderer() {
    }

    public static void renderBrackets(GuiGraphics guiGraphics,
                                      int gridLeft,
                                      int gridTop,
                                      int rowHeight,
                                      List<Integer> rowGroupIds,
                                      IntFunction<EmiBookmarkGroup> groupLookup) {
        if (rowGroupIds == null || rowGroupIds.isEmpty()) {
            return;
        }

        int startRow = -1;
        int currentGroupId = EmiBookmarkManager.DEFAULT_GROUP_ID;
        for (int row = 0; row < rowGroupIds.size(); row++) {
            int groupId = safeGroupId(rowGroupIds.get(row));
            if (groupId != EmiBookmarkManager.DEFAULT_GROUP_ID) {
                if (startRow < 0) {
                    startRow = row;
                    currentGroupId = groupId;
                } else if (groupId != currentGroupId) {
                    drawGroupBracket(guiGraphics, gridLeft, gridTop, rowHeight, startRow, row - 1,
                            colorForGroup(currentGroupId, groupLookup));
                    startRow = row;
                    currentGroupId = groupId;
                }
            } else if (startRow >= 0) {
                drawGroupBracket(guiGraphics, gridLeft, gridTop, rowHeight, startRow, row - 1,
                        colorForGroup(currentGroupId, groupLookup));
                startRow = -1;
                currentGroupId = EmiBookmarkManager.DEFAULT_GROUP_ID;
            }
        }

        if (startRow >= 0) {
            drawGroupBracket(guiGraphics, gridLeft, gridTop, rowHeight, startRow, rowGroupIds.size() - 1,
                    colorForGroup(currentGroupId, groupLookup));
        }
    }

    public static void renderRowHighlight(GuiGraphics guiGraphics,
                                          int gridLeft,
                                          int gridTop,
                                          int rowHeight,
                                          int rowIndex,
                                          int color) {
        int x = gridLeft - GROUP_PANEL_WIDTH;
        int y = gridTop + rowIndex * rowHeight;
        guiGraphics.fill(x, y, x + GROUP_PANEL_WIDTH, y + rowHeight, color);
    }

    private static int colorForGroup(int groupId, IntFunction<EmiBookmarkGroup> groupLookup) {
        if (groupId == Integer.MIN_VALUE) {
            return DRAG_INCLUDE_COLOR;
        }
        EmiBookmarkGroup group = groupLookup == null ? null : groupLookup.apply(groupId);
        if (group != null && group.isCraftingChainEnabled()) {
            return GROUP_CHAIN_COLOR;
        }
        if (group != null && group.hasLink()) {
            return GROUP_LINKED_COLOR;
        }
        return GROUP_NONE_COLOR;
    }

    private static void drawGroupBracket(GuiGraphics guiGraphics,
                                         int gridLeft,
                                         int gridTop,
                                         int rowHeight,
                                         int startRow,
                                         int endRow,
                                         int color) {
        int halfWidth = GROUP_PANEL_WIDTH / 2;
        int heightPadding = Math.max(1, rowHeight / 4);
        int leftPosition = gridLeft - halfWidth - 1;

        int top = gridTop + startRow * rowHeight;
        int bottom = gridTop + (endRow + 1) * rowHeight;

        guiGraphics.fill(leftPosition, top + heightPadding, leftPosition + halfWidth, top + heightPadding + 1, color);
        guiGraphics.fill(leftPosition, top + heightPadding, leftPosition + 1, bottom - heightPadding, color);
        guiGraphics.fill(leftPosition, bottom - heightPadding - 1, leftPosition + halfWidth, bottom - heightPadding, color);
    }

    private static int safeGroupId(Integer groupId) {
        return groupId == null ? EmiBookmarkManager.DEFAULT_GROUP_ID : groupId;
    }
}
