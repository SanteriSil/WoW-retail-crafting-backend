import type { RecipeListDetail, RecipeListSummary } from "../types";

type Status = {
    msg: string;
    ok: boolean;
} | null;

type Props = {
    lists: RecipeListSummary[];
    activeListId: number | null;
    activeList: RecipeListDetail | null;
    loading: boolean;
    busy: boolean;
    status: Status;
    onSelectList: (listId: number) => void;
    onCreateList: () => void;
    onRenameList: () => void;
    onDeleteList: () => void;
    onRemoveRecipe: (recipeId: number) => void;
    onCopyItemIds: () => void;
    activeListBlacklistedItemIds: number[];
    expandedRecipeIds: Set<number>;
    recipeComponentMap: Record<number, { itemId: number; itemName: string; blacklisted: boolean }[]>;
    onToggleRecipeExpand: (recipeId: number) => void;
    onToggleItemBlacklist: (itemId: number, blacklisted: boolean) => void;
};

export default function RecipeListManager({
    lists,
    activeListId,
    activeList,
    loading,
    busy,
    status,
    onSelectList,
    onCreateList,
    onRenameList,
    onDeleteList,
    onRemoveRecipe,
    onCopyItemIds,
    activeListBlacklistedItemIds,
    expandedRecipeIds,
    recipeComponentMap,
    onToggleRecipeExpand,
    onToggleItemBlacklist,
}: Props) {
    return (
        <section className="card" style={{ marginBottom: 16 }}>
            <div className="card-header" style={{ justifyContent: "space-between", marginBottom: 12, flexWrap: "wrap" }}>
                <div>
                    <h3 style={{ margin: 0 }}>📋 Recipe Lists</h3>
                    <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>
                        Build focused addon scan lists from selected recipes.
                    </div>
                </div>
                {status && (
                    <span className={`status-inline ${status.ok ? "status-success" : "status-error"}`}>
                        {status.msg}
                    </span>
                )}
            </div>

            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 12 }}>
                <select
                    className="select"
                    value={activeListId ?? ""}
                    onChange={(e) => onSelectList(Number(e.target.value))}
                    disabled={loading || lists.length === 0}
                    style={{ maxWidth: 320 }}
                >
                    {lists.length === 0 ? (
                        <option value="">No recipe lists yet</option>
                    ) : (
                        lists.map((list) => (
                            <option key={list.id} value={list.id}>
                                {list.name} ({list.recipeCount})
                            </option>
                        ))
                    )}
                </select>
                <button type="button" className="button secondary" onClick={onCreateList} disabled={busy}>
                    + New List
                </button>
                <button
                    type="button"
                    className="button secondary"
                    onClick={onRenameList}
                    disabled={busy || activeListId == null}
                >
                    ✏️ Rename
                </button>
                <button
                    type="button"
                    className="button danger"
                    onClick={onDeleteList}
                    disabled={busy || activeListId == null}
                >
                    🗑 Delete
                </button>
            </div>

            {loading ? (
                <div className="muted" style={{ padding: "8px 0" }}>Loading recipe lists…</div>
            ) : activeList ? (
                <>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
                        <div className="muted" style={{ fontSize: 13 }}>
                            Recipes in list ({activeList.recipeCount})
                        </div>
                        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                            {activeListBlacklistedItemIds.length > 0 && (
                                <span className="status-inline status-warning">
                                    ⚠ {activeListBlacklistedItemIds.length} blacklisted item ID{activeListBlacklistedItemIds.length === 1 ? "" : "s"} excluded
                                </span>
                            )}
                            <button type="button" className="button secondary small" onClick={onCopyItemIds} disabled={busy}>
                                📋 Copy Item IDs
                            </button>
                        </div>
                    </div>

                    {activeList.recipes.length === 0 ? (
                        <div className="muted" style={{ padding: "8px 0" }}>
                            No recipes added yet. Use the buttons in the recipe table below.
                        </div>
                    ) : (
                        <div className="list" style={{ maxHeight: 260 }}>
                            {activeList.recipes.map((entry) => (
                                <div key={entry.recipeId} className="list-item" style={{ alignItems: "stretch", gap: 12, flexDirection: "column", cursor: "default" }}>
                                    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                                        <div style={{ display: "flex", flexDirection: "column", gap: 4, minWidth: 0 }}>
                                            <strong style={{ fontSize: 14 }}>{entry.recipeName}</strong>
                                            <span className="muted" style={{ fontSize: 12 }}>
                                                {entry.outputItemName ?? "Unknown output"}
                                            </span>
                                        </div>
                                        <button
                                            type="button"
                                            className="button secondary small"
                                            onClick={() => onToggleRecipeExpand(entry.recipeId)}
                                            disabled={busy}
                                            style={{ marginLeft: "auto" }}
                                        >
                                            {expandedRecipeIds.has(entry.recipeId) ? "▾ Components" : "▸ Components"}
                                        </button>
                                        <button
                                            type="button"
                                            className="button secondary small"
                                            onClick={() => onRemoveRecipe(entry.recipeId)}
                                            disabled={busy}
                                        >
                                            ✕ Remove
                                        </button>
                                    </div>

                                    {expandedRecipeIds.has(entry.recipeId) && (
                                        <div style={{ display: "grid", gap: 6 }}>
                                            {(recipeComponentMap[entry.recipeId] ?? []).map((component) => (
                                                <div
                                                    key={`${entry.recipeId}-${component.itemId}`}
                                                    style={{
                                                        display: "flex",
                                                        alignItems: "center",
                                                        gap: 10,
                                                        padding: "6px 8px",
                                                        borderRadius: 8,
                                                        background: component.blacklisted ? "#1f2937" : "#f8fafc",
                                                        color: component.blacklisted ? "#f9fafb" : "inherit",
                                                        opacity: component.blacklisted ? 0.9 : 1,
                                                    }}
                                                >
                                                    <span style={{ fontSize: 13, fontWeight: 600 }}>{component.itemName}</span>
                                                    <span style={{ fontSize: 12, opacity: 0.8 }}>#{component.itemId}</span>
                                                    <button
                                                        type="button"
                                                        className="button secondary small"
                                                        style={{ marginLeft: "auto" }}
                                                        disabled={busy}
                                                        onClick={() => onToggleItemBlacklist(component.itemId, component.blacklisted)}
                                                    >
                                                        {component.blacklisted ? "Unblacklist" : "Blacklist"}
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                </>
            ) : (
                <div className="muted" style={{ padding: "8px 0" }}>
                    Create a recipe list to start collecting recipe scan groups.
                </div>
            )}
        </section>
    );
}
