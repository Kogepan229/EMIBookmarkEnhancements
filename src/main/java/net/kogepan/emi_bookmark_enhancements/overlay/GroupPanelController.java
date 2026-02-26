package net.kogepan.emi_bookmark_enhancements.overlay;

import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkEntry;
import net.kogepan.emi_bookmark_enhancements.bookmark.model.EmiBookmarkGroup;
import net.kogepan.emi_bookmark_enhancements.bookmark.service.EmiBookmarkManager;
import net.kogepan.emi_bookmark_enhancements.integration.emi.EmiRuntimeAccess;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GroupPanelController {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final int SLOT_SIZE = 18;

    private static EmiBookmarkManager bookmarkManager;
    private static DragState dragState;
    private static FavoriteGridSnapshot lastGridSnapshot;

    private GroupPanelController() {
    }

    public static void register(EmiBookmarkManager manager) {
        bookmarkManager = manager;
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.addListener(GroupPanelController::onMouseButtonPressedPre);
            MinecraftForge.EVENT_BUS.addListener(GroupPanelController::onMouseDraggedPre);
            MinecraftForge.EVENT_BUS.addListener(GroupPanelController::onMouseButtonReleasedPre);
            MinecraftForge.EVENT_BUS.addListener(GroupPanelController::onRenderPost);
        }
    }

    private static void onMouseButtonPressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (bookmarkManager == null || !LayoutModeController.isVerticalMode() || event.getScreen() == null) {
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }

        FavoriteGridSnapshot grid = captureGridSnapshot();
        if (grid == null || !grid.containsPanel((int) event.getMouseX(), (int) event.getMouseY())) {
            return;
        }
        int row = grid.rowIndexAt((int) event.getMouseY());
        if (row < 0 || grid.getRowEntries(row).isEmpty()) {
            return;
        }

        dragState = new DragState(event.getButton(), row);
        lastGridSnapshot = grid;
        event.setCanceled(true);
    }

    private static void onMouseDraggedPre(ScreenEvent.MouseDragged.Pre event) {
        if (dragState == null || bookmarkManager == null || !LayoutModeController.isVerticalMode()) {
            return;
        }

        FavoriteGridSnapshot grid = captureGridSnapshot();
        if (grid == null) {
            grid = lastGridSnapshot;
        }
        if (grid == null) {
            return;
        }

        dragState.endRow = grid.closestRowIndex((int) event.getMouseY());
        lastGridSnapshot = grid;
        event.setCanceled(true);
    }

    private static void onMouseButtonReleasedPre(ScreenEvent.MouseButtonReleased.Pre event) {
        if (dragState == null || bookmarkManager == null || !LayoutModeController.isVerticalMode()) {
            return;
        }
        if (event.getButton() != dragState.button) {
            return;
        }

        FavoriteGridSnapshot grid = captureGridSnapshot();
        if (grid == null) {
            grid = lastGridSnapshot;
        }
        if (grid == null) {
            clearDragState();
            return;
        }

        dragState.endRow = grid.closestRowIndex((int) event.getMouseY());
        int minRow = Math.min(dragState.startRow, dragState.endRow);
        int maxRow = Math.max(dragState.startRow, dragState.endRow);

        boolean changed;
        if (dragState.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (dragState.startRow <= dragState.endRow) {
                changed = includeRowsInGroup(minRow, maxRow, grid);
            } else {
                changed = separateRowsFromGroup(minRow, maxRow, grid);
            }
        } else {
            if (dragState.startRow == dragState.endRow) {
                changed = toggleCraftingChainAtRow(dragState.startRow, grid);
            } else {
                changed = excludeRowsFromGroup(minRow, maxRow, grid);
            }
        }

        clearDragState();
        if (changed) {
            bookmarkManager.save();
            EmiRuntimeAccess.refreshFavoritesSidebar();
        }
        event.setCanceled(true);
    }

    private static void onRenderPost(ScreenEvent.Render.Post event) {
        if (bookmarkManager == null || !LayoutModeController.isVerticalMode()) {
            return;
        }
        FavoriteGridSnapshot grid = captureGridSnapshot();
        if (grid == null) {
            return;
        }
        lastGridSnapshot = grid;

        List<Integer> rowGroupIds = grid.getPrimaryGroupIds();
        if (dragState != null) {
            applyDragPreview(rowGroupIds, grid);
        }
        GroupBracketRenderer.renderBrackets(
                event.getGuiGraphics(),
                grid.gridLeft(),
                grid.gridTop(),
                grid.rowHeight(),
                rowGroupIds,
                bookmarkManager::getGroup
        );

        if (dragState != null) {
            int minRow = Math.min(dragState.startRow, dragState.endRow);
            int maxRow = Math.max(dragState.startRow, dragState.endRow);
            int color = dragState.button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    ? GroupBracketRenderer.DRAG_INCLUDE_COLOR
                    : GroupBracketRenderer.DRAG_EXCLUDE_COLOR;
            for (int row = minRow; row <= maxRow; row++) {
                GroupBracketRenderer.renderRowHighlight(
                        event.getGuiGraphics(),
                        grid.gridLeft(),
                        grid.gridTop(),
                        grid.rowHeight(),
                        row,
                        color);
            }
            return;
        }

        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        if (!grid.containsPanel(mouseX, mouseY)) {
            return;
        }
        int row = grid.rowIndexAt(mouseY);
        if (row < 0) {
            return;
        }
        GroupBracketRenderer.renderRowHighlight(
                event.getGuiGraphics(),
                grid.gridLeft(),
                grid.gridTop(),
                grid.rowHeight(),
                row,
                GroupBracketRenderer.HOVER_HIGHLIGHT_COLOR);
    }

    private static boolean includeRowsInGroup(int minRow, int maxRow, FavoriteGridSnapshot grid) {
        List<EmiBookmarkEntry> selectedEntries = grid.collectUniqueEntries(minRow, maxRow);
        if (selectedEntries.isEmpty()) {
            return false;
        }

        int targetGroupId = EmiBookmarkManager.DEFAULT_GROUP_ID;
        for (EmiBookmarkEntry entry : selectedEntries) {
            if (entry.getGroupId() != EmiBookmarkManager.DEFAULT_GROUP_ID) {
                targetGroupId = entry.getGroupId();
                break;
            }
        }
        if (targetGroupId == EmiBookmarkManager.DEFAULT_GROUP_ID) {
            targetGroupId = bookmarkManager.createGroup();
        }
        return bookmarkManager.moveEntriesToGroup(selectedEntries, targetGroupId);
    }

    private static boolean excludeRowsFromGroup(int minRow, int maxRow, FavoriteGridSnapshot grid) {
        List<EmiBookmarkEntry> selectedEntries = grid.collectUniqueEntries(minRow, maxRow);
        if (selectedEntries.isEmpty()) {
            return false;
        }
        return bookmarkManager.moveEntriesToGroup(selectedEntries, EmiBookmarkManager.DEFAULT_GROUP_ID);
    }

    private static boolean separateRowsFromGroup(int minRow, int maxRow, FavoriteGridSnapshot grid) {
        List<List<EmiBookmarkEntry>> recipeUnits = grid.collectRecipeUnits(minRow, maxRow);
        if (recipeUnits.size() <= 1) {
            return false;
        }

        boolean changed = false;
        for (int i = 1; i < recipeUnits.size(); i++) {
            int newGroupId = bookmarkManager.createGroup();
            changed |= bookmarkManager.moveEntriesToGroup(recipeUnits.get(i), newGroupId);
        }
        return changed;
    }

    private static boolean toggleCraftingChainAtRow(int row, FavoriteGridSnapshot grid) {
        int groupId = grid.getPrimaryGroupId(row);
        if (groupId == EmiBookmarkManager.DEFAULT_GROUP_ID) {
            return false;
        }
        EmiBookmarkGroup group = bookmarkManager.getGroup(groupId);
        if (group == null) {
            return false;
        }
        return bookmarkManager.setCraftingChainEnabled(groupId, !group.isCraftingChainEnabled());
    }

    private static void applyDragPreview(List<Integer> rowGroupIds, FavoriteGridSnapshot grid) {
        if (dragState == null || rowGroupIds.isEmpty()) {
            return;
        }
        int minRow = Math.max(0, Math.min(dragState.startRow, dragState.endRow));
        int maxRow = Math.min(rowGroupIds.size() - 1, Math.max(dragState.startRow, dragState.endRow));
        if (minRow > maxRow) {
            return;
        }

        if (dragState.button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragState.startRow <= dragState.endRow) {
            int previewGroupId = determineIncludePreviewGroupId(minRow, maxRow, grid);
            for (int row = minRow; row <= maxRow; row++) {
                rowGroupIds.set(row, previewGroupId);
            }
        } else if (dragState.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            for (int row = minRow; row <= maxRow; row++) {
                rowGroupIds.set(row, EmiBookmarkManager.DEFAULT_GROUP_ID);
            }
        }
    }

    private static int determineIncludePreviewGroupId(int minRow, int maxRow, FavoriteGridSnapshot grid) {
        for (EmiBookmarkEntry entry : grid.collectUniqueEntries(minRow, maxRow)) {
            if (entry.getGroupId() != EmiBookmarkManager.DEFAULT_GROUP_ID) {
                return entry.getGroupId();
            }
        }
        return Integer.MIN_VALUE;
    }

    private static FavoriteGridSnapshot captureGridSnapshot() {
        if (bookmarkManager == null) {
            return null;
        }

        List<EmiRuntimeAccess.FavoriteSlot> visibleSlots = EmiRuntimeAccess.getVisibleFavoriteSlots();
        if (visibleSlots.isEmpty()) {
            return null;
        }

        Map<Integer, List<RowCell>> rows = new TreeMap<>();
        int minX = Integer.MAX_VALUE;
        for (EmiRuntimeAccess.FavoriteSlot slot : visibleSlots) {
            EmiBookmarkEntry entry = bookmarkManager.findEntry(slot.handle());
            if (entry == null) {
                continue;
            }
            int cellX = slot.x() - 1;
            int cellY = slot.y() - 1;
            rows.computeIfAbsent(cellY, key -> new ArrayList<>())
                    .add(new RowCell(entry, cellX, cellY));
            if (cellX < minX) {
                minX = cellX;
            }
        }

        if (rows.isEmpty() || minX == Integer.MAX_VALUE) {
            return null;
        }

        List<GridRow> rowList = new ArrayList<>(rows.size());
        for (Map.Entry<Integer, List<RowCell>> row : rows.entrySet()) {
            row.getValue().sort((left, right) -> Integer.compare(left.x(), right.x()));
            rowList.add(new GridRow(row.getKey(), row.getValue()));
        }
        if (rowList.isEmpty()) {
            return null;
        }

        return new FavoriteGridSnapshot(minX, SLOT_SIZE, rowList);
    }

    private static void clearDragState() {
        dragState = null;
    }

    private static final class DragState {
        private final int button;
        private final int startRow;
        private int endRow;

        private DragState(int button, int startRow) {
            this.button = button;
            this.startRow = startRow;
            this.endRow = startRow;
        }
    }

    private record RowCell(EmiBookmarkEntry entry, int x, int y) {
    }

    private record GridRow(int y, List<RowCell> cells) {
    }

    private record FavoriteGridSnapshot(int gridLeft, int rowHeight, List<GridRow> rows) {
        int gridTop() {
            return rows.get(0).y();
        }

        boolean containsPanel(int mouseX, int mouseY) {
            int panelLeft = gridLeft - GroupBracketRenderer.GROUP_PANEL_WIDTH;
            int panelRight = gridLeft;
            int top = gridTop();
            int bottom = rows.get(rows.size() - 1).y() + rowHeight;
            return mouseX >= panelLeft && mouseX < panelRight
                    && mouseY >= top && mouseY < bottom;
        }

        int rowIndexAt(int mouseY) {
            for (int i = 0; i < rows.size(); i++) {
                int rowTop = rows.get(i).y();
                if (mouseY >= rowTop && mouseY < rowTop + rowHeight) {
                    return i;
                }
            }
            return -1;
        }

        int closestRowIndex(int mouseY) {
            if (rows.isEmpty()) {
                return -1;
            }
            int firstTop = rows.get(0).y();
            if (mouseY < firstTop) {
                return 0;
            }
            int lastIndex = rows.size() - 1;
            int lastBottom = rows.get(lastIndex).y() + rowHeight;
            if (mouseY >= lastBottom) {
                return lastIndex;
            }
            int rowIndex = rowIndexAt(mouseY);
            if (rowIndex >= 0) {
                return rowIndex;
            }
            int relative = (mouseY - firstTop) / rowHeight;
            return Math.max(0, Math.min(lastIndex, relative));
        }

        List<EmiBookmarkEntry> getRowEntries(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                return List.of();
            }
            List<EmiBookmarkEntry> entries = new ArrayList<>();
            for (RowCell cell : rows.get(rowIndex).cells()) {
                entries.add(cell.entry());
            }
            return entries;
        }

        int getPrimaryGroupId(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                return EmiBookmarkManager.DEFAULT_GROUP_ID;
            }
            for (RowCell cell : rows.get(rowIndex).cells()) {
                int groupId = cell.entry().getGroupId();
                if (groupId != EmiBookmarkManager.DEFAULT_GROUP_ID) {
                    return groupId;
                }
            }
            if (!rows.get(rowIndex).cells().isEmpty()) {
                return rows.get(rowIndex).cells().get(0).entry().getGroupId();
            }
            return EmiBookmarkManager.DEFAULT_GROUP_ID;
        }

        List<Integer> getPrimaryGroupIds() {
            List<Integer> groupIds = new ArrayList<>(rows.size());
            for (int row = 0; row < rows.size(); row++) {
                groupIds.add(getPrimaryGroupId(row));
            }
            return groupIds;
        }

        List<EmiBookmarkEntry> collectUniqueEntries(int minRow, int maxRow) {
            if (rows.isEmpty()) {
                return List.of();
            }
            int from = Math.max(0, minRow);
            int to = Math.min(rows.size() - 1, maxRow);
            if (from > to) {
                return List.of();
            }

            List<EmiBookmarkEntry> entries = new ArrayList<>();
            java.util.Set<EmiBookmarkEntry> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int row = from; row <= to; row++) {
                for (RowCell cell : rows.get(row).cells()) {
                    if (seen.add(cell.entry())) {
                        entries.add(cell.entry());
                    }
                }
            }
            return entries;
        }

        List<List<EmiBookmarkEntry>> collectRecipeUnits(int minRow, int maxRow) {
            if (rows.isEmpty()) {
                return List.of();
            }
            int from = Math.max(0, minRow);
            int to = Math.min(rows.size() - 1, maxRow);
            if (from > to) {
                return List.of();
            }

            List<List<EmiBookmarkEntry>> units = new ArrayList<>();
            List<EmiBookmarkEntry> current = null;
            java.util.Set<EmiBookmarkEntry> currentSeen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int row = from; row <= to; row++) {
                for (RowCell cell : rows.get(row).cells()) {
                    EmiBookmarkEntry entry = cell.entry();
                    if (entry.isResult()) {
                        if (current != null && !current.isEmpty()) {
                            units.add(current);
                        }
                        current = new ArrayList<>();
                        currentSeen = Collections.newSetFromMap(new IdentityHashMap<>());
                        current.add(entry);
                        currentSeen.add(entry);
                    } else if (entry.isIngredient() && current != null && currentSeen.add(entry)) {
                        current.add(entry);
                    }
                }
            }
            if (current != null && !current.isEmpty()) {
                units.add(current);
            }
            return units;
        }
    }
}
