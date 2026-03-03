import { useCallback, useEffect, useRef, useState } from "react";
import { getCharacters, getDashboardCrafts, getRecipe } from "../api";
import type { CalculatorEntry, Character, CraftOverrides, DashboardCraft, DashboardResponse, Profession, RecipeDetail } from "../types";
import DashboardFilters from "./DashboardFilters";
import DashboardSummary from "./DashboardSummary";
import CraftTable from "./CraftTable";
import CraftingCalculator from "./CraftingCalculator";

type Props = {
    professions: Profession[];
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

export default function DashboardPage({ professions }: Props) {
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

    // U8: Override state
    const [overrides, setOverrides] = useState<CraftOverrides>(loadOverrides);

    // U10: Calculator state
    const [calcEntries, setCalcEntries] = useState<CalculatorEntry[]>(loadCalcEntries);
    const [recipeCache, setRecipeCache] = useState<Map<number, RecipeDetail>>(new Map());
    const fetchingRef = useRef<Set<number>>(new Set());

    const handleOverrideChange = (next: CraftOverrides) => {
        setOverrides(next);
        localStorage.setItem("craft-overrides", JSON.stringify(next));
    };

    const persistCalc = (entries: CalculatorEntry[]) => {
        setCalcEntries(entries);
        localStorage.setItem("craft-calculator", JSON.stringify(entries));
    };

    // Fetch recipe details for calculator entries that aren't cached
    useEffect(() => {
        const missing = calcEntries
            .map((e) => e.recipeId)
            .filter((id) => !recipeCache.has(id) && !fetchingRef.current.has(id));

        const unique = [...new Set(missing)];
        if (unique.length === 0) return;

        for (const id of unique) fetchingRef.current.add(id);

        Promise.all(
            unique.map((id) =>
                getRecipe(id)
                    .then((detail) => ({ id, detail }))
                    .catch(() => ({ id, detail: null }))
            )
        ).then((results) => {
            setRecipeCache((prev) => {
                const next = new Map(prev);
                for (const { id, detail } of results) {
                    if (detail) next.set(id, detail);
                    fetchingRef.current.delete(id);
                }
                return next;
            });
        });
    }, [calcEntries, recipeCache]);

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
                onCharacterChange={setCharacterId}
                onProfessionChange={setProfessionId}
                onSearchChange={setSearch}
            />

            {error && <div className="error">{error}</div>}

            {dashboard && (
                <>
                    <DashboardSummary
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
                        overrides={overrides}
                        onOverrideChange={handleOverrideChange}
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
