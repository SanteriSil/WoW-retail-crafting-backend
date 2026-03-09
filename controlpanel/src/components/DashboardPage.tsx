import { useCallback, useEffect, useRef, useState } from "react";
import { getCharacters, getDashboardCrafts, getRecipe } from "../api";
import type { CalculatorEntry, Character, CraftOverrides, DashboardCraft, DashboardResponse, Profession, RecipeDetail } from "../types";
import DashboardFilters from "./DashboardFilters";
import DashboardSummary from "./DashboardSummary";
import CraftTable from "./CraftTable";
import CraftingCalculator from "./CraftingCalculator";

type Props = {
    professions: Profession[];
    role: string | null;
};

function loadOverrides(): CraftOverrides {
    try {
        return JSON.parse(localStorage.getItem("craft-overrides") || "{}");
    } catch {
        return {};
    }
}

function loadCalcEntries(): CalculatorEntry[] {
    try {
        return JSON.parse(localStorage.getItem("craft-calculator") || "[]");
    } catch {
        return [];
    }
}

export default function DashboardPage({ professions, role }: Props) {
    void role;
    const [characters, setCharacters] = useState<Character[]>([]);
    const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Filter state
    const [characterId, setCharacterId] = useState<number | undefined>();
    const [professionId, setProfessionId] = useState<number | undefined>();
    const [search, setSearch] = useState("");
    const [sort, setSort] = useState("adjustedProfit");
    const [direction, setDirection] = useState("desc");
    const [groupByOutput, setGroupByOutput] = useState(true);
    const [showBaseMetrics, setShowBaseMetrics] = useState(true);

    // U8: Override state
    const [overrides, setOverrides] = useState<CraftOverrides>(loadOverrides);

    // U10: Calculator state
    const [calcEntries, setCalcEntries] = useState<CalculatorEntry[]>(loadCalcEntries);
    const [recipeCache, setRecipeCache] = useState<Map<number, RecipeDetail>>(new Map());
    const [recipeLoadingIds, setRecipeLoadingIds] = useState<Set<number>>(new Set());
    const fetchingRef = useRef<Set<number>>(new Set());
    const recipeCacheRef = useRef<Map<number, RecipeDetail>>(new Map());

    useEffect(() => {
        recipeCacheRef.current = recipeCache;
    }, [recipeCache]);

    const handleOverrideChange = (next: CraftOverrides) => {
        setOverrides(next);
        localStorage.setItem("craft-overrides", JSON.stringify(next));
    };

    const persistCalc = (entries: CalculatorEntry[]) => {
        setCalcEntries(entries);
        localStorage.setItem("craft-calculator", JSON.stringify(entries));
    };

    const fetchRecipeDetail = useCallback(async (id: number, force = false) => {
        if (!force && (recipeCacheRef.current.has(id) || fetchingRef.current.has(id))) return;

        fetchingRef.current.add(id);
        setRecipeLoadingIds((prev) => {
            const next = new Set(prev);
            next.add(id);
            return next;
        });

        try {
            const detail = await getRecipe(id);
            setRecipeCache((prev) => {
                const next = new Map(prev);
                next.set(id, detail);
                return next;
            });
        } catch {
            // Ignore here; consumers show a fallback message.
        } finally {
            fetchingRef.current.delete(id);
            setRecipeLoadingIds((prev) => {
                const next = new Set(prev);
                next.delete(id);
                return next;
            });
        }
    }, []);

    // Fetch recipe details for calculator entries that aren't cached
    useEffect(() => {
        const missing = [...new Set(
            calcEntries
                .map((e) => e.recipeId)
                .filter((id) => !recipeCache.has(id) && !fetchingRef.current.has(id))
        )];

        missing.forEach((id) => {
            void fetchRecipeDetail(id);
        });
    }, [calcEntries, recipeCache, fetchRecipeDetail]);

    const handleAddToCalculator = (craft: DashboardCraft) => {
        setCalcEntries((prev) => {
            const idx = prev.findIndex(
                (e) => e.characterId === craft.characterId && e.recipeId === craft.recipeId
            );
            let next: CalculatorEntry[];
            if (idx >= 0) {
                next = [...prev];
                next[idx] = { ...next[idx], quantity: next[idx].quantity + 1 };
            } else {
                next = [...prev, { characterId: craft.characterId, recipeId: craft.recipeId, quantity: 1 }];
            }
            localStorage.setItem("craft-calculator", JSON.stringify(next));
            return next;
        });
    };

    const handleCalcUpdateQty = (characterId: number, recipeId: number, quantity: number) => {
        const next = calcEntries.map((e) =>
            e.characterId === characterId && e.recipeId === recipeId ? { ...e, quantity } : e
        );
        persistCalc(next);
    };

    const handleCalcRemove = (characterId: number, recipeId: number) => {
        const next = calcEntries.filter(
            (e) => !(e.characterId === characterId && e.recipeId === recipeId)
        );
        persistCalc(next);
    };

    const handleCalcClear = () => {
        persistCalc([]);
    };

    useEffect(() => {
        getCharacters().then(setCharacters).catch(() => setCharacters([]));
    }, []);

    const fetchDashboard = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await getDashboardCrafts({
                characterId,
                professionId,
                search: search || undefined,
                sort,
                direction,
            });
            setDashboard(data);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to load dashboard.");
        } finally {
            setLoading(false);
        }
    }, [characterId, professionId, search, sort, direction]);

    useEffect(() => {
        void fetchDashboard();
    }, [fetchDashboard]);

    const handleSortChange = (field: string) => {
        if (field === sort) {
            setDirection((prev) => (prev === "asc" ? "desc" : "asc"));
        } else {
            setSort(field);
            setDirection("desc");
        }
    };

    return (
        <div className="dashboard-page">
            <div className="recipes-page-header">
                <h2 style={{ margin: 0 }}>Craft Dashboard</h2>
            </div>

            {characters.length === 0 && !loading && (
                <div className="muted" style={{ padding: 16, textAlign: "center" }}>
                    Create a character to start tracking crafts.
                </div>
            )}

            <DashboardFilters
                characters={characters}
                professions={professions}
                characterId={characterId}
                professionId={professionId}
                search={search}
                groupByOutput={groupByOutput}
                showBaseMetrics={showBaseMetrics}
                onCharacterChange={setCharacterId}
                onProfessionChange={setProfessionId}
                onSearchChange={setSearch}
                onGroupByOutputChange={setGroupByOutput}
                onShowBaseMetricsChange={setShowBaseMetrics}
            />

            {error && <div className="error">{error}</div>}

            {dashboard && (
                <>
                    <DashboardSummary
                        totalBaseCost={dashboard.totalBaseCost}
                        totalBaseProfit={dashboard.totalBaseProfit}
                        totalAdjustedProfit={dashboard.totalAdjustedProfit}
                        totalCrafts={dashboard.totalCrafts}
                    />
                    <CraftTable
                        crafts={dashboard.crafts}
                        sort={sort}
                        direction={direction}
                        onSortChange={handleSortChange}
                        loading={loading}
                        groupByOutput={groupByOutput}
                        showBaseMetrics={showBaseMetrics}
                        overrides={overrides}
                        onOverrideChange={handleOverrideChange}
                        recipeCache={recipeCache}
                        recipeLoadingIds={recipeLoadingIds}
                        onFetchRecipe={(recipeId) => {
                            void fetchRecipeDetail(recipeId);
                        }}
                        onAddToCalculator={handleAddToCalculator}
                    />

                    <CraftingCalculator
                        entries={calcEntries}
                        crafts={dashboard.crafts}
                        overrides={overrides}
                        recipeCache={recipeCache}
                        onUpdateQuantity={handleCalcUpdateQty}
                        onRemove={handleCalcRemove}
                        onClear={handleCalcClear}
                    />
                </>
            )}
        </div>
    );
}
