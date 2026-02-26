# JEI-Enhancements機能調査ドキュメント（EMI移植用）

最終更新: 2026-02-26  
対象: Minecraft 1.20.1 / Java 17

## 0. 調査範囲

### 移植元（JEI-Enhancements）
- `JEI-Enhancements/src/main/java/com/gali/jei_enhancements/bookmark/*`
- `JEI-Enhancements/src/main/java/com/gali/jei_enhancements/event/*`
- `JEI-Enhancements/src/main/java/com/gali/jei_enhancements/mixin/*`
- `JEI-Enhancements/src/main/java/com/gali/jei_enhancements/recipe/*`
- `JEI-Enhancements/src/main/java/com/gali/jei_enhancements/jei/JEIEnhancementsPlugin.java`

### JEI内部参照（JustEnoughItems）
- `JustEnoughItems/Gui/src/main/java/mezz/jei/gui/bookmarks/BookmarkList.java`
- `JustEnoughItems/Gui/src/main/java/mezz/jei/gui/overlay/bookmarks/BookmarkOverlay.java`
- `JustEnoughItems/Gui/src/main/java/mezz/jei/gui/overlay/IngredientGridWithNavigation.java`
- `JustEnoughItems/Gui/src/main/java/mezz/jei/gui/overlay/IngredientGrid.java`
- `JustEnoughItems/Gui/src/main/java/mezz/jei/gui/overlay/IngredientListRenderer.java`
- `JustEnoughItems/Gui/src/main/java/mezz/jei/gui/recipes/RecipesGui.java`
- `JustEnoughItems/Gui/src/main/java/mezz/jei/gui/recipes/RecipeGuiLayouts.java`
- `JustEnoughItems/Gui/src/main/java/mezz/jei/gui/recipes/RecipeLayoutWithButtons.java`
- `JustEnoughItems/Gui/src/main/java/mezz/jei/gui/PageNavigation.java`

### EMI参照（移植先候補）
- `emi/xplat/src/main/java/dev/emi/emi/runtime/EmiFavorites.java`
- `emi/xplat/src/main/java/dev/emi/emi/runtime/EmiFavorite.java`
- `emi/xplat/src/main/java/dev/emi/emi/runtime/EmiPersistentData.java`
- `emi/xplat/src/main/java/dev/emi/emi/runtime/EmiSidebars.java`
- `emi/xplat/src/main/java/dev/emi/emi/screen/EmiScreenManager.java`
- `emi/xplat/src/main/java/dev/emi/emi/screen/RecipeScreen.java`
- `emi/xplat/src/main/java/dev/emi/emi/screen/MicroTextRenderer.java`
- `emi/xplat/src/main/java/dev/emi/emi/config/EmiConfig.java`
- `emi/xplat/src/main/java/dev/emi/emi/config/SidebarType.java`
- `emi/xplat/src/main/java/dev/emi/emi/jemi/runtime/JemiBookmarkOverlay.java`

## 1. 機能サマリ（JEI-Enhancements）

1. ブックマークに独自データ層を追加（数量、factor、種別、グループ、展開状態、crafting chain状態）。
2. 同一アイテムの重複ブックマークを許可（JEI標準の`Set`ベース重複排除を回避）。
3. `Shift + A` / `Ctrl + Shift + A`でレシピをブックマーク化。
4. `Ctrl + ホイール`で数量調整（`Ctrl + Alt`で64刻み）。
5. ブックマーク描画に数量オーバーレイ・グループ背景・折りたたみ表示を追加。
6. レイアウト切替（横並び/縦並び）と、縦並び時の「グループ単位ページング」。
7. グループパネルでドラッグ操作（結合/分離/除外）と右クリックcrafting chain切替。
8. crafting chain有効時、組内レシピの必要量を再計算。
9. ツールチップ拡張（数量表示、Altで操作ヘルプ）。
10. 削除/移動を「equals」ではなく「インスタンス同一性」で扱う安全化。

## 2. データモデル・保存仕様

## 2.1 BookmarkItem
- クラス: `bookmark/BookmarkItem.java`
- 主フィールド:
  - `groupId`
  - `itemKey`
  - `factor`（1回分の基準数量）
  - `amount`（現在数量）
  - `type`（`ITEM`, `RESULT`, `INGREDIENT`）
  - `linkedBookmark`（JEI `IBookmark`インスタンス）
- 数量は `multiplier = ceil(amount / factor)` で運用。

## 2.2 BookmarkGroup
- クラス: `bookmark/BookmarkGroup.java`
- 主フィールド:
  - `groupId`
  - `linkedGroupId`（保存はされるが現行ロジックでは実質未活用）
  - `expanded`
  - `craftingChainEnabled`

## 2.3 BookmarkManager永続化
- クラス: `bookmark/BookmarkManager.java`
- 保存先: `<mcDir>/config/jei_enhancements_bookmarks.json`
- 保存内容:
  - `nextGroupId`
  - `groups`（`expanded`, `craftingChain`, `linkedGroupId`）
  - `items`（`groupId`, `itemKey`, `factor`, `amount`, `type`）
- 補助仕様:
  - `IdentityHashMap<IBookmark, BookmarkItem>`でJEI側実体に紐付け。
  - `ensureLoaded()`による遅延ロード。

## 2.4 レイアウト設定
- クラス: `bookmark/BookmarkLayoutManager.java`
- 保存先: `<mcDir>/config/jei_enhancements_layout.json`
- 値: `layoutMode = HORIZONTAL | VERTICAL`

## 3. 操作機能詳細

## 3.1 レシピ一括追加（Shift+A / Ctrl+Shift+A）
- 実装:
  - `mixin/RecipesGuiMixin.java`
  - `recipe/RecipeBookmarkHelper.java`
- 挙動:
  - `Shift + A`: 主出力1件 + 全入力を追加（`groupId = 0`）。
  - `Ctrl + Shift + A`: 主出力1件 + 全入力を新規グループとして追加（数量保存）。
  - 副産物（2つ目以降の出力）は追加しない。
  - 同一入力は`itemKey`単位で集約して数量を合算。

## 3.2 数量調整（Ctrl+ホイール）
- 実装: `event/BookmarkScrollHandler.java`
- 挙動:
  - `Ctrl + Scroll`: ±1 multiplier
  - `Ctrl + Alt + Scroll`: ±64 multiplier
  - ブックマーク上でのみ動作し、JEI標準スクロールをキャンセル。
- 備考:
  - 言語キーに`ctrl_shift_scroll`があるが、コード上は未実装。

## 3.3 ツールチップ拡張
- 実装: `mixin/IngredientGridMixin.java`
- 挙動:
  - `Shift`押下時: 現在数量 + 分解表示（例: `130 = 2 x 64 + 2`）を追加。
  - `Alt`押下時: 操作ヘルプ一覧を追加。
  - 通常時: `Hold Alt for more actions`を追加。

## 3.4 数量・背景描画
- 実装:
  - `mixin/BookmarkOverlayMixin.java`
  - `bookmark/BookmarkQuantityRenderer.java`
- 挙動:
  - 非デフォルトグループを半透明背景で囲う。
  - `Ctrl`押下時に`RESULT`/`INGREDIENT`を色分け強調。
  - 右下（または流体は左下）に縮小フォントで数量を描画。
  - 折りたたみ時、グループ先頭に`+N`表示。

## 3.5 レイアウト切替（横/縦）
- 実装: `event/BookmarkLayoutClickHandler.java`
- 操作:
  - ページ送りボタン間のページ番号領域をクリックすると切替。
- 挙動:
  - `BookmarkLayoutManager`を更新して保存。
  - 反射で`IngredientListRenderer`キャッシュを消して`updateLayout`を強制。

## 3.6 縦レイアウト時のページング再定義
- 実装:
  - `mixin/IngredientListRendererMixin.java`
  - `mixin/IngredientGridWithNavigationMixin.java`
  - `mixin/IngredientGridPagedMixin.java`
  - `bookmark/IVerticalPagingAccessor.java`
- 挙動:
  - 1ページの単位を「要素数」ではなく「グループ行数」に変更。
  - `RESULT`または`ITEM`を行頭として扱い、折りたたみ状態にも対応。
  - `nextPage` / `previousPage` はグループ起点インデックスにジャンプ。

## 3.7 グループパネル（縦レイアウト専用）
- 実装:
  - `event/BookmarkLayoutClickHandler.java`
  - `bookmark/GroupingDragHandler.java`
- 操作:
  - 左ドラッグ下方向: 行を同一グループへ統合
  - 左ドラッグ上方向: 選択範囲の配方を分離（新規グループ化）
  - 右ドラッグ: 選択行をデフォルトグループへ除外
  - 右クリック: crafting chain ON/OFF
  - `Alt + 左クリック`(スロット上): グループ展開/折りたたみ
- 描画:
  - `[`型ブラケット表示、ドラッグ中プレビュー表示
  - crafting chain有効時はブラケットを緑色化

## 3.8 crafting chain再計算
- 実装: `bookmark/BookmarkManager.java` (`recalculateCraftingChainInGroup`)
- 概要:
  - 組内の`INGREDIENT -> RESULT`対応（preferred map）を作成。
  - 先頭`RESULT`から再帰的に必要量を累積。
  - 不足分のみ`shift = ceil((required-current)/factor)`で増産。
  - 最後に各`RESULT`のmultiplierを確定し、対応`INGREDIENT`へ反映。

## 3.9 削除/移動仕様の変更（Identity重視）
- 実装: `mixin/BookmarkListMixin.java`
- 挙動:
  - `contains`を条件付き上書きし重複追加を許可。
  - `moveBookmark`で`==`による位置特定を使用（同値別インスタンス対応）。
  - `RESULT`の削除は「グループ全体」ではなく「そのRESULT + 直後INGREDIENT群」単位。
  - グループ跨ぎ移動を禁止。

## 4. JEI依存ポイント（移植時に置換が必要）

1. `BookmarkList`のprivate内部構造（`bookmarksList`, `bookmarksSet`, `listeners`）へのMixin依存。
2. `BookmarkOverlay.contents`への反射アクセス（`BookmarkLayoutClickHandler`, `BookmarkOverlayMixin`）。
3. `IngredientGridWithNavigation$IngredientGridPaged`内部クラスへのMixin。
4. `RecipeGuiLayouts.recipeLayoutsWithButtons`への反射アクセス。
5. `RecipeLayoutWithButtons`のアクセサ命名差(`recipeLayout`/`getRecipeLayout`)への反射互換。

JEIバージョン更新で壊れやすいのは上記5点。

## 5. EMIへの対応候補マッピング

| JEI-Enhancements機能 | 主要参照元（JEI-E） | EMI側の対応候補 |
|---|---|---|
| お気に入りデータ管理 | `BookmarkManager`, `BookmarkItem`, `BookmarkGroup` | `EmiFavorites`, `EmiFavorite`, `EmiPersistentData` |
| お気に入り一覧表示/ページ | `BookmarkOverlay`, `IngredientGridWithNavigation` | `EmiScreenManager.SidebarPanel`, `ScreenSpace` |
| ドラッグ並び替え | `BookmarkListMixin.moveBookmark`, `GroupingDragHandler` | `EmiScreenManager.mouseDragged/mouseReleased`, `EmiFavorites.addFavoriteAt` |
| レシピ文脈付きお気に入り | `RecipeBookmarkHelper`, `BookmarkItemType.RESULT` | `EmiFavorite(stack, recipe)`, `EmiApi.getRecipeContext` |
| ホバー取得 | `BookmarkOverlay.getIngredientUnderMouse` | `EmiScreenManager.getHoveredStack` / `JemiBookmarkOverlay` |
| 小さい数量描画 | `BookmarkQuantityRenderer` | `MicroTextRenderer.render` |
| 入力処理 | `BookmarkScrollHandler`, `BookmarkLayoutClickHandler`, `RecipesGuiMixin` | `EmiScreenManager.stackInteraction/genericInteraction`, `RecipeScreen.*input*` |

## 6. 既知の仕様差分・注意点

1. `linkedGroupId`は保存/読込されるが、設定ロジックが見当たらず実質未使用。
2. `currentAddingGroupId`はsetter/getterのみで実質未使用。
3. `shiftSubGroupAmount`、`cleanupEmptyGroup`、`removeEntireGroup`は未使用。
4. `Jei_enhancementsClient.java`は空ファイル。
5. 言語キー`jei_enhancements.tooltip.ctrl_shift_scroll`と`shift_a`は実コードで未参照。
6. `Shift + A`で作られた項目は`groupId=0`（デフォルト）で、グループ背景やグループ単位数量調整対象外。

## 7. EMI移植時の実装優先順位（提案）

1. P0: レシピ一括追加（Shift+A/Ctrl+Shift+A相当）、数量表示、Ctrl+Scroll数量調整。
2. P1: 重複許可 + identityベース並び替え/削除。
3. P2: 縦レイアウト + グループパネル（結合/分離/除外/折りたたみ）。
4. P3: crafting chain自動再計算。

## 8. 参照ファイル対応（移植元 -> 主要移植先候補）

- `JEI-Enhancements/.../bookmark/BookmarkManager.java` -> `EMIBookmarkEnhancements` 新規 `bookmark/EmiBookmarkManager`（想定）
- `JEI-Enhancements/.../bookmark/GroupingDragHandler.java` -> `EMIBookmarkEnhancements` 新規 `ui/SidebarGroupingController`（想定）
- `JEI-Enhancements/.../recipe/RecipeBookmarkHelper.java` -> `EMIBookmarkEnhancements` 新規 `recipe/RecipeFavoriteHelper`（想定）
- `JEI-Enhancements/.../mixin/RecipesGuiMixin.java` -> `EMIBookmarkEnhancements` 新規入力フック（`RecipeScreen`/`EmiScreenManager`連携）
- `JEI-Enhancements/.../mixin/IngredientGridMixin.java` + `BookmarkQuantityRenderer.java` -> `EMIBookmarkEnhancements` 新規サイドバー描画拡張

上記「想定」は本ドキュメント時点の設計案であり、EMI実装制約に応じて命名変更可。

