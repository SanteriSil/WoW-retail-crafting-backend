import { useCallback, useEffect, useRef, useState } from "react";
import type { Expansion, Profession, RecipeFilterParams } from "../types";

type Props = {
    professions: Profession[];
    expansions: Expansion[];
    filters: RecipeFilterParams;
    onChange: (next: RecipeFilterParams) => void;
    onClear: () => void;
};

export default function RecipeFilters({ professions, expansions, filters, onChange, onClear }: Props) {
    const [localSearch, setLocalSearch] = useState(filters.search ?? "");
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Sync search input if filters are cleared externally (e.g. "✕ Clear" button)
    useEffect(() => {
        setLocalSearch(filters.search ?? "");
    }, [filters.search]);

    const handleSearch = useCallback(
        (value: string) => {
            setLocalSearch(value);
            if (debounceRef.current) clearTimeout(debounceRef.current);
            debounceRef.current = setTimeout(() => {
                onChange({ ...filters, search: value.trim() || undefined, page: 0 });
            }, 300);
        },
        [filters, onChange],
    );

    const toggleProfession = useCallback(
        (id: number) => {
            onChange({ ...filters, professionId: filters.professionId === id ? undefined : id, page: 0 });
        },
        [filters, onChange],
    );

    const toggleExpansion = useCallback(
        (id: number) => {
            onChange({ ...filters, expansionId: filters.expansionId === id ? undefined : id, page: 0 });
        },
        [filters, onChange],
    );

    const hasFilter = filters.professionId != null || filters.expansionId != null || !!filters.search;

    return (
        <div className="recipe-filters">
            <input
                className="input"
                placeholder="Search by recipe name…"
                value={localSearch}
                onChange={(e) => handleSearch(e.target.value)}
            />

            <div className="profession-filter">
                {professions.map((p) => (
                    <button
                        key={p.id}
                        type="button"
                        className={`profession-chip${filters.professionId === p.id ? " active" : ""}`}
                        onClick={() => toggleProfession(p.id)}
                    >
                        {p.name}
                    </button>
                ))}
            </div>

            <div className="profession-filter">
                {expansions.map((e) => (
                    <button
                        key={e.id}
                        type="button"
                        className={`profession-chip${filters.expansionId === e.id ? " active" : ""}`}
                        onClick={() => toggleExpansion(e.id)}
                    >
                        {e.name}
                    </button>
                ))}
                {hasFilter && (
                    <button type="button" className="profession-chip clear" onClick={onClear}>
                        ✕ Clear
                    </button>
                )}
            </div>
        </div>
    );
}
