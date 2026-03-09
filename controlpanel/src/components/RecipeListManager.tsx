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
                        <button type="button" className="button secondary small" onClick={onCopyItemIds} disabled={busy}>
                            📋 Copy Item IDs
                        </button>
                    </div>

                    {activeList.recipes.length === 0 ? (
                        <div className="muted" style={{ padding: "8px 0" }}>
                            No recipes added yet. Use the buttons in the recipe table below.
                        </div>
                    ) : (
                        <div className="list" style={{ maxHeight: 260 }}>
                            {activeList.recipes.map((entry) => (
                                <div key={entry.recipeId} className="list-item" style={{ alignItems: "center", gap: 12 }}>
                                    <div style={{ display: "flex", flexDirection: "column", gap: 4, minWidth: 0 }}>
                                        <strong style={{ fontSize: 14 }}>{entry.recipeName}</strong>
                                        <span className="muted" style={{ fontSize: 12 }}>
                                            {entry.outputItemName ?? "Unknown output"}
                                        </span>
                                    </div>
                                    <button
                                        type="button"
                                        className="button secondary small"
                                        onClick={() => onRemoveRecipe(entry.recipeId)}
                                        disabled={busy}
                                        style={{ marginLeft: "auto" }}
                                    >
                                        ✕ Remove
                                    </button>
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
