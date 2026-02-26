# EMIBookmarkEnhancements Codex移植計画

最終更新: 2026-02-26  
基準資料: `docs/JEI_ENHANCEMENTS_FEATURE_AUDIT.md`

## 1. 目的

`JEI-Enhancements` のブックマーク拡張を、EMI向けに段階的に移植する。  
このドキュメントは **Codexが順番に自動実行できる粒度** でタスクを定義する。

## 2. 実行ルール（Codex向け）

1. 編集対象は `EMIBookmarkEnhancements/` のみ。
2. 参照専用: `JEI-Enhancements/`, `JustEnoughItems/`, `emi/`。
3. 1タスク完了ごとに:
   - 仕様確認
   - 実装
   - ビルド/検証
   - ドキュメント更新（必要時）
4. 既定で最小差分。新規機能は機能単位で追加。
5. 失敗時は「失敗原因・次の修正方針」をタスクに追記して継続。

## 3. 全体完了条件（Definition of Done）

1. P0機能（数量管理、レシピ追加、基本表示、基本入力）がEMI上で動作。
2. P1機能（重複、並び替え/削除の整合性）が動作。
3. P2機能（縦グループUI）が動作。
4. P3機能（crafting chain計算）が動作。
5. 永続化ファイルの前方互換を維持（読み込み失敗でクラッシュしない）。

## 4. 既定設計（意思決定済み）

1. 永続化ファイル名: `emi_bookmark_enhancements_bookmarks.json`
2. レイアウト設定ファイル名: `emi_bookmark_enhancements_layout.json`
3. データモデルはJEI名を避け、EMI文脈命名を使う。
4. 初期段階ではEMI公開API＋`EmiScreenManager`参照で実装し、不足時のみMixin追加。
5. `Shift+A` / `Ctrl+Shift+A` は `RecipeScreen` / `EmiScreenManager` 経由で処理する。

## 5. タスク一覧（実行順）

## M0: 基盤整備

### T00 プロジェクト雛形整理
- 目的: Forgeテンプレート要素を撤去し、移植用の最小構成にする。
- 入力参照:
  - `src/main/java/net/kogepan/emi_bookmark_enhancements/EmiBookmarkEnhancements.java`
  - `src/main/java/net/kogepan/emi_bookmark_enhancements/Config.java`
- 変更先:
  - `src/main/java/net/kogepan/emi_bookmark_enhancements/EmiBookmarkEnhancements.java`
  - `src/main/java/net/kogepan/emi_bookmark_enhancements/config/ModConfig.java`（新規）
- 完了条件:
  - 例示ブロック/アイテム登録コードが消えている。
  - クライアント初期化導線のみ残る。

### T01 パッケージ構成作成
- 目的: 後続タスクの配置先を固定する。
- 変更先（新規ディレクトリ）:
  - `.../bookmark/model`
  - `.../bookmark/service`
  - `.../bookmark/persistence`
  - `.../input`
  - `.../overlay`
  - `.../recipe`
  - `.../integration/emi`
- 完了条件:
  - 各ディレクトリに最低1クラス（空でも可）を配置。

## M1: データ層移植（P0）

### T10 モデル定義
- 目的: JEI `BookmarkItem/BookmarkGroup` 同等情報をEMI向けに再定義。
- 参照元:
  - `JEI-Enhancements/.../bookmark/BookmarkItem.java`
  - `JEI-Enhancements/.../bookmark/BookmarkGroup.java`
- 変更先:
  - `.../bookmark/model/EmiBookmarkEntry.java`（新規）
  - `.../bookmark/model/EmiBookmarkGroup.java`（新規）
- 必須要件:
  - `groupId`, `itemKey`, `factor`, `amount`, `type`, `expanded`, `craftingChainEnabled`
  - `multiplier`算出/更新ヘルパー

### T11 マネージャ実装
- 目的: in-memory管理（追加/検索/削除/数量シフト/グループ操作）を提供。
- 参照元:
  - `JEI-Enhancements/.../bookmark/BookmarkManager.java`
- 変更先:
  - `.../bookmark/service/EmiBookmarkManager.java`（新規）
- 完了条件:
  - 単体メソッドで追加・検索・重複許可・削除が動作。
  - identity対応マップ（EMIお気に入り実体との紐付け）を保持。

### T12 永続化実装
- 目的: JSON save/loadの実装。
- 変更先:
  - `.../bookmark/persistence/BookmarkStore.java`（新規）
- 完了条件:
  - load失敗時に空データで継続。
  - `nextGroupId`、`groups`、`items`保存。

## M2: EMI Favorites統合（P0）

### T20 EMIブリッジ実装
- 目的: `EmiFavorites` と内部モデルを同期。
- 参照元:
  - `emi/.../runtime/EmiFavorites.java`
  - `emi/.../runtime/EmiFavorite.java`
- 変更先:
  - `.../integration/emi/EmiFavoritesBridge.java`（新規）
- 要件:
  - 起動時同期
  - お気に入り追加/削除時同期
  - 並び順再構築

### T21 起動/終了フック
- 目的: クライアント開始時load、終了前save。
- 変更先:
  - `.../EmiBookmarkEnhancements.java`
  - `.../integration/emi/ClientLifecycleHooks.java`（新規）
- 完了条件:
  - world未参加状態でもクラッシュしない。

## M3: 入力機能（P0）

### T30 Ctrl+Scroll数量調整
- 目的: お気に入りホバー中に数量変更。
- 参照元:
  - `JEI-Enhancements/.../event/BookmarkScrollHandler.java`
  - `emi/.../screen/EmiScreenManager.java` (`getHoveredStack`, `mouseScrolled`)
- 変更先:
  - `.../input/FavoriteScrollHandler.java`（新規）
- 要件:
  - `Ctrl`: ±1
  - `Ctrl+Alt`: ±64

### T31 Shift+A / Ctrl+Shift+A
- 目的: レシピをお気に入りグループとして追加。
- 参照元:
  - `JEI-Enhancements/.../mixin/RecipesGuiMixin.java`
  - `JEI-Enhancements/.../recipe/RecipeBookmarkHelper.java`
  - `emi/.../screen/RecipeScreen.java`
- 変更先:
  - `.../recipe/RecipeFavoriteHelper.java`（新規）
  - `.../input/RecipeShortcutHandler.java`（新規）
- 要件:
  - 主出力1件 + 入力全件
  - `Shift+A`: デフォルトグループ
  - `Ctrl+Shift+A`: 新規グループ（数量保持）

## M4: 描画機能（P0）

### T40 数量オーバーレイ描画
- 目的: お気に入りスロット上に数量文字列を描画。
- 参照元:
  - `JEI-Enhancements/.../bookmark/BookmarkQuantityRenderer.java`
  - `emi/.../screen/MicroTextRenderer.java`
- 変更先:
  - `.../overlay/FavoriteQuantityOverlay.java`（新規）
- 要件:
  - 通常短縮表記、Shiftでフル桁表示
  - 流体系単位対応（EMI設定に追従）

### T41 ツールチップ拡張
- 目的: Shift/Alt操作ヘルプと数量情報の表示。
- 変更先:
  - `.../overlay/FavoriteTooltipAugmenter.java`（新規）
  - `src/main/resources/assets/emi_bookmark_enhancements/lang/en_us.json`（新規）
  - `src/main/resources/assets/emi_bookmark_enhancements/lang/ja_jp.json`（新規）
- 完了条件:
  - ホバー時に追加行が表示される。

## M5: 重複・順序・削除の整合（P1）

### T50 重複許可運用
- 目的: 同一アイテム複数登録を安定動作させる。
- 参照元:
  - `JEI-Enhancements/.../mixin/BookmarkListMixin.java`
  - `emi/.../runtime/EmiFavorites.java` (`addFavoriteAt`)
- 変更先:
  - `.../integration/emi/EmiFavoritesBridge.java`
  - `.../bookmark/service/EmiBookmarkManager.java`
- 要件:
  - `strictEquals`一致でも別エントリ保持可能。

### T51 削除仕様
- 目的: RESULT削除時に「該当レシピ単位」で削除。
- 変更先:
  - `.../bookmark/service/EmiBookmarkManager.java`
  - `.../integration/emi/EmiFavoritesBridge.java`
- 完了条件:
  - 他レシピを巻き込まない。

## M6: グループUI（P2）

### T60 レイアウトモード管理
- 目的: HORIZONTAL/VERTICALを保存・切替。
- 参照元:
  - `JEI-Enhancements/.../bookmark/BookmarkLayoutManager.java`
- 変更先:
  - `.../bookmark/persistence/LayoutStore.java`（新規）
  - `.../overlay/LayoutModeController.java`（新規）

### T61 グループパネルとドラッグ操作
- 目的: 縦モードで結合/分離/除外/右クリックchain切替。
- 参照元:
  - `JEI-Enhancements/.../bookmark/GroupingDragHandler.java`
  - `emi/.../screen/EmiScreenManager.java` (`ScreenSpace`, drag/drop paths)
- 変更先:
  - `.../overlay/GroupPanelController.java`（新規）
  - `.../overlay/GroupBracketRenderer.java`（新規）
- 要件:
  - 左下ドラッグ: 結合
  - 左上ドラッグ: 分離
  - 右ドラッグ: デフォルトへ除外
  - 右クリック: `craftingChainEnabled`切替

## M7: Crafting Chain（P3）

### T70 計算エンジン移植
- 目的: 組内レシピ必要量の再計算。
- 参照元:
  - `JEI-Enhancements/.../bookmark/BookmarkManager.java` (`recalculateCraftingChainInGroup`一式)
- 変更先:
  - `.../bookmark/service/CraftingChainCalculator.java`（新規）
  - `.../bookmark/service/EmiBookmarkManager.java`
- 完了条件:
  - chain ON時に下流レシピのmultiplierが再計算される。

## M8: 仕上げ

### T80 エラーハンドリングと互換性
- 目的: 壊れた保存データ/未知型でクラッシュしない。
- 変更先:
  - persistence/service全般

### T81 ドキュメント更新
- 目的: 実装との差分を文書化。
- 変更先:
  - `docs/JEI_ENHANCEMENTS_FEATURE_AUDIT.md`（必要箇所に実装済み印）
  - `docs/CODEX_PORTING_PLAN.md`（完了チェック反映）

## 6. タスク実行テンプレート（Codex用）

各タスクで以下を実施:

1. 対象タスクIDの仕様を再確認。
2. 参照元ファイルを読み、必要な最小機能のみ移植。
3. `EMIBookmarkEnhancements` に実装。
4. 検証:
   - `cd EMIBookmarkEnhancements`
   - `gradle build`（利用不可なら理由を記録）
5. 作業報告フォーマット:
   - 何を移植したか
   - 参照元
   - 変更先
   - ビルド/未解決

## 7. 初回自動実行バッチ（推奨）

Codexはまず以下を連続実行する:

1. `T00`  
2. `T01`  
3. `T10`  
4. `T11`  
5. `T12`  
6. `T20`  
7. `T21`  
8. `T30`

理由: ここまでで「データ保持＋基本入力」の土台が成立し、以降の描画/高度機能の実装リスクを下げられるため。

