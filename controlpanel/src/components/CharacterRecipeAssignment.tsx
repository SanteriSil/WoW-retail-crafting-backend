import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { assignRecipes, getCharacterRecipes, getRecipes, unassignRecipe } from "../api";
import type { Page, Profession, RecipeFilterParams, RecipeSummary } from "../types";

function qualityStars(quality?: number | null): string | null {
    if (quality == null) return null;
    if (quality === 1) return "★";
    if (quality === 2) return "★★";
    return null;
}

type Props = {
    characterId: number;
    professions: Profession[];
    characterProfessionIds: number[];
    onAssignmentChange: () => void;
};

export default function CharacterRecipeAssignment({ characterId, professions, characterProfessionIds, onAssignmentChange }: Props) {
    // ── Assigned recipes ──
    const [assigned, setAssigned] = useState<RecipeSummary[]>([]);
    const [assignedLoading, setAssignedLoading] = useState(false);

    // Professions available in the dropdown: character's professions (if any) or all
    const charProfs = professions.filter((p) => characterProfessionIds.includes(p.id));
    const hasCharProfs = charProfs.length > 0;

    // ── Browser state ──
    const [search, setSearch] = useState("");
    const [outputSearch, setOutputSearch] = useState("");
    const [profFilter, setProfFilter] = useState<number | "" | "char">(
        hasCharProfs ? (charProfs.length === 1 ? charProfs[0].id : "char") : ""
    );
    const [browserPage, setBrowserPage] = useState<Page<RecipeSummary> | null>(null);
    const [browserLoading, setBrowserLoading] = useState(false);
    const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
    const [assigning, setAssigning] = useState(false);
    const [status, setStatus] = useState<{ msg: string; ok: boolean } | null>(null);
    const debounceRef = useRef<ReturnType<typeof setTimeout>>();

    // ── Fetch assigned recipes ──
    const loadAssigned = useCallback(async () => {
        setAssignedLoading(true);
        try {
            const data = await getCharacterRecipes(characterId);
            setAssigned(data);
        } catch {
            // non-critical
        } finally {
            setAssignedLoading(false);
        }
    }, [characterId]);

    useEffect(() => {
        void loadAssigned();
    }, [loadAssigned]);

    // ── Fetch browseable recipes (debounced search) ──
    const fetchBrowser = useCallback(async (s: string, prof: number | "" | "char") => {
        setBrowserLoading(true);
        try {
            if (prof === "char" && characterProfessionIds.length > 0) {
                // Fetch for each of the character's professions and merge
                const pages = await Promise.all(
                    characterProfessionIds.map((pid) => {
                        const params: RecipeFilterParams = { size: 10, sort: "name,asc", professionId: pid };
                        if (s.trim()) params.search = s.trim();
                        return getRecipes(params);
                    })
                );
                const seen = new Set<number>();
                const merged = pages.flatMap((p) => p.content).filter((r) => {
                    if (seen.has(r.id)) return false;
                    seen.add(r.id);
                    return true;
                }).sort((a, b) => a.name.localeCompare(b.name)).slice(0, 20);
                setBrowserPage({ content: merged, totalElements: merged.length, totalPages: 1, number: 0, size: 20 });
            } else {
                const params: RecipeFilterParams = { size: 10, sort: "name,asc" };
                if (s.trim()) params.search = s.trim();
                if (typeof prof === "number" && prof) params.professionId = prof;
                const page = await getRecipes(params);
                setBrowserPage(page);
            }
        } catch {
            // ignore
        } finally {
            setBrowserLoading(false);
        }
    }, [characterProfessionIds]);

    // Debounce search input
    useEffect(() => {
        clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => {
            void fetchBrowser(search, profFilter);
        }, 300);
        return () => clearTimeout(debounceRef.current);
    }, [search, profFilter, fetchBrowser]);

    const assignedIdSet = new Set(assigned.map((r) => r.id));
    const filteredBrowserRecipes = useMemo(() => {
        const outputNeedle = outputSearch.trim().toLowerCase();
        if (!browserPage) return [];
        if (!outputNeedle) return browserPage.content;

        return browserPage.content.filter((recipe) =>
            recipe.outputItemName.toLowerCase().includes(outputNeedle)
        );
    }, [browserPage, outputSearch]);

    // ── Unassign ──
    const handleUnassign = async (recipeId: number) => {
        try {
            await unassignRecipe(characterId, recipeId);
            setStatus({ msg: "Recipe unassigned.", ok: true });
            await loadAssigned();
            onAssignmentChange();
        } catch (err) {
            setStatus({ msg: err instanceof Error ? err.message : "Unassign failed.", ok: false });
        } finally {
            setTimeout(() => setStatus(null), 3000);
        }
    };

    // ── Assign selected ──
    const handleAssign = async () => {
        if (selectedIds.size === 0) return;
        setAssigning(true);
        try {
            await assignRecipes(characterId, Array.from(selectedIds));
            setStatus({ msg: `${selectedIds.size} recipe(s) assigned.`, ok: true });
            setSelectedIds(new Set());
            await loadAssigned();
            onAssignmentChange();
        } catch (err) {
            setStatus({ msg: err instanceof Error ? err.message : "Assign failed.", ok: false });
        } finally {
            setAssigning(false);
            setTimeout(() => setStatus(null), 3000);
        }
    };

    const toggleSelected = (id: number) => {
        setSelectedIds((prev) => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    return (
        <div className="assignment-panel card">
            <h3 style={{ margin: "0 0 12px" }}>
                Recipe Assignments
                {status && (
                    <span className={`status-inline ${status.ok ? "status-success" : "status-error"}`} style={{ marginLeft: 12 }}>
                        {status.msg}
                    </span>
                )}
            </h3>

            {/* ── Assigned list ── */}
            <div className="assignment-section">
                <div className="recipe-section-label">Assigned Recipes ({assigned.length})</div>
                {assignedLoading && <div className="muted" style={{ fontSize: 12 }}>Loading…</div>}
                {!assignedLoading && assigned.length === 0 && (
                    <div className="muted" style={{ fontSize: 13 }}>No recipes assigned yet.</div>
                )}
                {assigned.length > 0 && (
                    <div className="assigned-recipe-list">
                        {assigned.map((r) => (
                            <div key={r.id} className="assigned-recipe-row">
                                <div>
                                    <span style={{ fontWeight: 500 }}>{r.name}</span>
                                    {r.hasNotes && <span className="notes-indicator" title="This recipe has notes">📝</span>}
                                    {(() => {
                                        const stars = qualityStars(r.outputItemQuality);
                                        return stars ? <span className={`quality-stars q${r.outputItemQuality}`}> {stars}</span> : null;
                                    })()}
                                    <span className="muted" style={{ marginLeft: 8, fontSize: 12 }}>
                                        {r.professionName ?? ""} · {r.expansionName}
                                    </span>
                                </div>
                                <button
                                    type="button"
                                    className="button small danger"
                                    onClick={() => void handleUnassign(r.id)}
                                    title="Unassign"
                                >✕</button>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* ── Recipe browser ── */}
            <div className="assignment-section" style={{ marginTop: 16 }}>
                <div className="recipe-section-label">Assign More</div>
                <div className="assignment-browser-controls">
                    <input
                        className="input"
                        placeholder="Search recipes…"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        style={{ flex: 1 }}
                    />
                    <select
                        className="input"
                        value={profFilter}
                        onChange={(e) => {
                            const v = e.target.value;
                            setProfFilter(v === "char" ? "char" : v ? Number(v) : "");
                        }}
                        style={{ width: 200 }}
                    >
                        {hasCharProfs && (
                            <option value="char">
                                Character&apos;s Professions ({charProfs.map((p) => p.name).join(", ")})
                            </option>
                        )}
                        {hasCharProfs && charProfs.map((p) => (
                            <option key={p.id} value={p.id}>{p.name}</option>
                        ))}
                        <option value="">All Professions</option>
                        {!hasCharProfs && professions.map((p) => (
                            <option key={p.id} value={p.id}>{p.name}</option>
                        ))}
                    </select>
                </div>
                <input
                    className="input"
                    placeholder="Filter by output item…"
                    value={outputSearch}
                    onChange={(e) => setOutputSearch(e.target.value)}
                    style={{ maxWidth: 320, marginBottom: 8 }}
                />

                {browserLoading && <div className="muted" style={{ fontSize: 12, marginTop: 6 }}>Searching…</div>}

                {browserPage && filteredBrowserRecipes.length > 0 && (
                    <div className="browser-recipe-list">
                        {filteredBrowserRecipes.map((r) => {
                            const alreadyAssigned = assignedIdSet.has(r.id);
                            const checked = alreadyAssigned || selectedIds.has(r.id);
                            return (
                                <label
                                    key={r.id}
                                    className={`browser-recipe-row${alreadyAssigned ? " assigned" : ""}`}
                                >
                                    <input
                                        type="checkbox"
                                        checked={checked}
                                        disabled={alreadyAssigned}
                                        onChange={() => toggleSelected(r.id)}
                                    />
                                    <span style={{ fontWeight: 500 }}>
                                        {r.name}
                                        {r.hasNotes && <span className="notes-indicator" title="This recipe has notes">📝</span>}
                                    </span>
                                    {(() => {
                                        const stars = qualityStars(r.outputItemQuality);
                                        return stars ? <span className={`quality-stars q${r.outputItemQuality}`}> {stars}</span> : null;
                                    })()}
                                    <span className="muted" style={{ fontSize: 12 }}>
                                        {r.professionName ?? ""} · {r.expansionName}
                                    </span>
                                </label>
                            );
                        })}
                    </div>
                )}

                {browserPage && filteredBrowserRecipes.length === 0 && !browserLoading && (
                    <div className="muted" style={{ fontSize: 13, marginTop: 6 }}>No recipes found.</div>
                )}

                {selectedIds.size > 0 && (
                    <button
                        type="button"
                        className="button primary"
                        style={{ marginTop: 8 }}
                        disabled={assigning}
                        onClick={() => void handleAssign()}
                    >
                        {assigning ? "Assigning…" : `Assign Selected (${selectedIds.size})`}
                    </button>
                )}
            </div>
        </div>
    );
}
