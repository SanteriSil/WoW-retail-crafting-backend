import type { Character, Profession } from "../types";

type Props = {
    characters: Character[];
    professions: Profession[];
    characterId: number | undefined;
    professionId: number | undefined;
    search: string;
    groupByOutput: boolean;
    showBaseMetrics: boolean;
    onCharacterChange: (id: number | undefined) => void;
    onProfessionChange: (id: number | undefined) => void;
    onSearchChange: (s: string) => void;
    onGroupByOutputChange: (value: boolean) => void;
    onShowBaseMetricsChange: (value: boolean) => void;
};

export default function DashboardFilters({
    characters, professions, characterId, professionId, search,
    groupByOutput, showBaseMetrics,
    onCharacterChange, onProfessionChange, onSearchChange, onGroupByOutputChange, onShowBaseMetricsChange,
}: Props) {
    return (
        <div className="dashboard-filters">
            <div className="dashboard-filter-row">
                <div className="dashboard-chips">
                    <button
                        type="button"
                        className={`profession-chip${characterId == null ? " active" : ""}`}
                        onClick={() => onCharacterChange(undefined)}
                    >
                        All Characters
                    </button>
                    {characters.map((c) => (
                        <button
                            key={c.id}
                            type="button"
                            className={`profession-chip${characterId === c.id ? " active" : ""}`}
                            onClick={() => onCharacterChange(c.id)}
                        >
                            {c.name}
                        </button>
                    ))}
                </div>
                <div className="dashboard-chips">
                    <button
                        type="button"
                        className={`profession-chip${professionId == null ? " active" : ""}`}
                        onClick={() => onProfessionChange(undefined)}
                    >
                        All Professions
                    </button>
                    {professions.map((p) => (
                        <button
                            key={p.id}
                            type="button"
                            className={`profession-chip${professionId === p.id ? " active" : ""}`}
                            onClick={() => onProfessionChange(p.id)}
                        >
                            {p.name}
                        </button>
                    ))}
                </div>
            </div>
            <input
                className="input"
                placeholder="Search recipes…"
                value={search}
                onChange={(e) => onSearchChange(e.target.value)}
                style={{ maxWidth: 300 }}
            />
            <div className="dashboard-toggle-row">
                <label style={{ display: "inline-flex", alignItems: "center", gap: 8, fontSize: 13, color: "#334155" }}>
                    <input
                        type="checkbox"
                        checked={groupByOutput}
                        onChange={(e) => onGroupByOutputChange(e.target.checked)}
                    />
                    Group by output
                </label>
                <label style={{ display: "inline-flex", alignItems: "center", gap: 8, fontSize: 13, color: "#334155" }}>
                    <input
                        type="checkbox"
                        checked={showBaseMetrics}
                        onChange={(e) => onShowBaseMetricsChange(e.target.checked)}
                    />
                    Show base cost column
                </label>
            </div>
        </div>
    );
}

