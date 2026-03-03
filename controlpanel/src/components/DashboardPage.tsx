import { useCallback, useEffect, useState } from "react";
import { getCharacters, getDashboardCrafts } from "../api";
import type { Character, CraftOverrides, DashboardResponse, Profession } from "../types";
import DashboardFilters from "./DashboardFilters";
import DashboardSummary from "./DashboardSummary";
import CraftTable from "./CraftTable";

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

    const handleOverrideChange = (next: CraftOverrides) => {
        setOverrides(next);
        localStorage.setItem("craft-overrides", JSON.stringify(next));
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
                    />
                </>
            )}
        </div>
    );
}
