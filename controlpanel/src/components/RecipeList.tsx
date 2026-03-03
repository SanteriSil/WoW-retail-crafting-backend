import type { Page, RecipeSummary } from "../types";

type SortableField = "name";

type Props = {
    page: Page<RecipeSummary> | null;
    loading: boolean;
    sort: string;
    onPageChange: (page: number) => void;
    onSortChange: (sort: string) => void;
    onSelectRecipe: (recipe: RecipeSummary) => void;
};

export default function RecipeList({ page, loading, sort, onPageChange, onSortChange, onSelectRecipe }: Props) {
    const [sortField, sortDir] = sort.split(",");

    const handleSortClick = (field: SortableField) => {
        if (sortField === field) {
            onSortChange(`${field},${sortDir === "asc" ? "desc" : "asc"}`);
        } else {
            onSortChange(`${field},asc`);
        }
    };

    const SortIcon = ({ field }: { field: SortableField }) => {
        const active = sortField === field;
        const icon = active ? (sortDir === "asc" ? "↑" : "↓") : "↕";
        return <span className={`sort-indicator${active ? " active" : ""}`}>{icon}</span>;
    };

    if (loading && (!page || page.content.length === 0)) {
        return <div className="muted" style={{ padding: "16px 0" }}>Loading recipes…</div>;
    }

    if (!page || page.content.length === 0) {
        return (
            <div className="muted" style={{ padding: "16px 0" }}>
                No recipes match the current filters.
            </div>
        );
    }

    return (
        <div>
            <div className="recipe-table-wrapper">
                <table className="recipe-table">
                    <thead>
                        <tr>
                            <th className="sortable" onClick={() => handleSortClick("name")}>
                                Name <SortIcon field="name" />
                            </th>
                            <th>Profession</th>
                            <th>Expansion</th>
                            <th>Source</th>
                            <th>Output Item</th>
                        </tr>
                    </thead>
                    <tbody>
                        {page.content.map((recipe) => (
                            <tr
                                key={recipe.id}
                                onClick={() => onSelectRecipe(recipe)}
                                role="button"
                                tabIndex={0}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter" || e.key === " ") onSelectRecipe(recipe);
                                }}
                            >
                                <td style={{ fontWeight: 600 }}>{recipe.name}</td>
                                <td className="muted">{recipe.professionName ?? "—"}</td>
                                <td className="muted">{recipe.expansionName}</td>
                                <td>
                                    <span className={`source-badge ${recipe.source.toLowerCase()}`}>
                                        {recipe.source}
                                    </span>
                                </td>
                                <td className="muted">{recipe.outputItemName}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="recipe-pagination">
                <button
                    type="button"
                    className="button secondary small"
                    disabled={page.number === 0 || loading}
                    onClick={() => onPageChange(page.number - 1)}
                >
                    ← Prev
                </button>
                <span>
                    Page {page.number + 1} of {page.totalPages}
                    <span className="muted" style={{ marginLeft: 8 }}>
                        ({page.totalElements} recipe{page.totalElements !== 1 ? "s" : ""})
                    </span>
                </span>
                <button
                    type="button"
                    className="button secondary small"
                    disabled={page.number >= page.totalPages - 1 || loading}
                    onClick={() => onPageChange(page.number + 1)}
                >
                    Next →
                </button>
            </div>
        </div>
    );
}
