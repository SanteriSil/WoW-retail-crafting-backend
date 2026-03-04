import { useMemo, useState } from "react";
import type { Item } from "../types";

type SheetEntry = { id: number; name: string; quality?: number | null; multiplier: number; finishingIngredient: boolean; iconUrl?: string | null };

type SheetBuilderProps = {
    items: Item[];
};

function qualityStars(quality?: number | null): string | null {
    if (quality == null) return null;
    if (quality === 1) return "★";
    if (quality === 2) return "★★";
    return "?";
}

export default function SheetBuilder({ items }: SheetBuilderProps) {
    const [expanded, setExpanded] = useState(false);
    const [outputItem, setOutputItem] = useState<{ id: number; name: string; quality?: number | null; iconUrl?: string | null } | null>(null);
    const [searchOutput, setSearchOutput] = useState("");
    const [ingredients, setIngredients] = useState<SheetEntry[]>([]);
    const [searchIngredients, setSearchIngredients] = useState("");
    const [copied, setCopied] = useState(false);

    const filterItems = (query: string, excludeIds: Set<number>) => {
        if (!query.trim()) return [];
        const lowered = query.toLowerCase();
        return items
            .filter((it) => !excludeIds.has(it.id) && (it.name.toLowerCase().includes(lowered) || String(it.id).includes(lowered)))
            .slice(0, 12);
    };

    const allUsedIds = useMemo(() => {
        const ids = new Set(ingredients.map((e) => e.id));
        if (outputItem) ids.add(outputItem.id);
        return ids;
    }, [ingredients, outputItem]);

    const suggestionsOutput = useMemo(() => filterItems(searchOutput, new Set(outputItem ? [outputItem.id] : [])), [searchOutput, items, outputItem]);
    const suggestionsIngredients = useMemo(() => filterItems(searchIngredients, allUsedIds), [searchIngredients, items, allUsedIds]);

    const addTo = (list: SheetEntry[], setList: React.Dispatch<React.SetStateAction<SheetEntry[]>>, item: Item) => {
        if (list.some((e) => e.id === item.id)) return;
        setList([...list, { id: item.id, name: item.name, quality: item.quality, multiplier: 1, finishingIngredient: !!item.finishingIngredient, iconUrl: item.iconUrl }]);
    };

    const removeFrom = (setList: React.Dispatch<React.SetStateAction<SheetEntry[]>>, id: number) => {
        setList((prev) => prev.filter((e) => e.id !== id));
    };

    const setMultiplier = (setList: React.Dispatch<React.SetStateAction<SheetEntry[]>>, id: number, value: number) => {
        setList((prev) => prev.map((e) => (e.id === id ? { ...e, multiplier: value } : e)));
    };

    const outputJson = useMemo(() => {
        const active = ingredients.filter((e) => e.multiplier > 0);
        const withResEntries = active.filter((e) => !e.finishingIngredient);
        const withoutResEntries = active.filter((e) => e.finishingIngredient);
        const payload = {
            outputId: outputItem?.id ?? null,
            withResourcefulness: withResEntries.map((e) => ({ id: e.id, multiplier: e.multiplier })),
            withoutResourcefulness: withoutResEntries.map((e) => ({ id: e.id, multiplier: e.multiplier }))
        };
        return JSON.stringify(payload, null, 2);
    }, [outputItem, ingredients]);

    const hasContent = outputItem != null || ingredients.length > 0;

    const handleCopy = async () => {
        try {
            await navigator.clipboard.writeText(outputJson);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch {
            /* fallback: user can select+copy from the textarea */
        }
    };

    return (
        <div className={`sheet-panel ${expanded ? "expanded" : "collapsed"}`}>
            {!expanded ? (
                <button
                    className="sheet-toggle"
                    type="button"
                    onClick={() => setExpanded(true)}
                    aria-label="Open Sheet Builder"
                >
                    <svg className={`chev ${expanded ? "rotated" : ""}`} width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                        <path d="M9 5l7 7-7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                </button>
            ) : (
                <div className="sheet-card">
                    <div className="sheet-header">
                        <button
                            className="sheet-toggle"
                            type="button"
                            onClick={() => setExpanded(false)}
                            aria-label="Close Sheet Builder"
                        >
                            <svg className={`chev ${expanded ? "rotated" : ""}`} width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                                <path d="M9 5l7 7-7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                            </svg>
                        </button>
                        <div className="sheet-title">Sheet Builder</div>
                    </div>

                    {/* Output Item */}
                    <div className="sheet-section">
                        <div className="sheet-section-label">Output Item</div>
                        {outputItem ? (
                            <div className="sheet-entries">
                                <div className="sheet-entry">
                                    <span className="sheet-entry-name" title={`#${outputItem.id}`}>
                                        {outputItem.iconUrl && (
                                            <img src={outputItem.iconUrl} alt="" width={16} height={16} style={{ borderRadius: 3, verticalAlign: "middle", marginRight: 4 }} />
                                        )}
                                        {outputItem.name}
                                        {(() => {
                                            const stars = qualityStars(outputItem.quality);
                                            return stars ? <span className={`quality-stars q${outputItem.quality}`}> {stars}</span> : null;
                                        })()}
                                    </span>
                                    <button
                                        type="button"
                                        className="sheet-remove-btn"
                                        onClick={() => setOutputItem(null)}
                                        aria-label="Remove output item"
                                    >
                                        ✕
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <div style={{ position: "relative" }}>
                                <input
                                    className="input"
                                    placeholder="Search output item…"
                                    value={searchOutput}
                                    onChange={(e) => setSearchOutput(e.target.value)}
                                    style={{ fontSize: 12, padding: "6px 10px" }}
                                />
                                {suggestionsOutput.length > 0 && (
                                    <div className="sheet-suggestions">
                                        {suggestionsOutput.map((item) => {
                                            const stars = qualityStars(item.quality);
                                            return (
                                                <button
                                                    key={item.id}
                                                    type="button"
                                                    className="sheet-suggestion-item"
                                                    onClick={() => { setOutputItem({ id: item.id, name: item.name, quality: item.quality, iconUrl: item.iconUrl }); setSearchOutput(""); }}
                                                >
                                                    {item.iconUrl && (
                                                        <img src={item.iconUrl} alt="" width={16} height={16} style={{ borderRadius: 3 }} />
                                                    )}
                                                    <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                                        {item.name}
                                                        {stars && <span className={`quality-stars q${item.quality}`}> {stars}</span>}
                                                    </span>
                                                    <span className="muted" style={{ fontSize: 11 }}>#{item.id}</span>
                                                </button>
                                            );
                                        })}
                                    </div>
                                )}
                            </div>
                        )}
                    </div>

                    {/* Ingredients */}
                    <Section
                        label="Ingredients"
                        entries={ingredients}
                        search={searchIngredients}
                        onSearchChange={setSearchIngredients}
                        suggestions={suggestionsIngredients}
                        onAdd={(item) => { addTo(ingredients, setIngredients, item); setSearchIngredients(""); }}
                        onRemove={(id) => removeFrom(setIngredients, id)}
                        onMultiplier={(id, v) => setMultiplier(setIngredients, id, v)}
                    />

                    {/* JSON output */}
                    {hasContent && (
                        <div className="sheet-output">
                            <textarea
                                className="input"
                                readOnly
                                value={outputJson}
                                rows={Math.min(14, outputJson.split("\n").length + 1)}
                                style={{ fontFamily: "monospace", fontSize: 11, resize: "vertical" }}
                                onFocus={(e) => e.target.select()}
                            />
                            <button type="button" className="button primary" style={{ marginTop: 6 }} onClick={handleCopy}>
                                {copied ? "Copied!" : "Copy JSON"}
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

/* ---------- reusable section ---------- */

type SectionProps = {
    label: string;
    entries: SheetEntry[];
    search: string;
    onSearchChange: (v: string) => void;
    suggestions: Item[];
    onAdd: (item: Item) => void;
    onRemove: (id: number) => void;
    onMultiplier: (id: number, value: number) => void;
};

function Section({ label, entries, search, onSearchChange, suggestions, onAdd, onRemove, onMultiplier }: SectionProps) {
    return (
        <div className="sheet-section">
            <div className="sheet-section-label">{label}</div>

            <div style={{ position: "relative" }}>
                <input
                    className="input"
                    placeholder="Search items…"
                    value={search}
                    onChange={(e) => onSearchChange(e.target.value)}
                    style={{ fontSize: 12, padding: "6px 10px" }}
                />
                {suggestions.length > 0 && (
                    <div className="sheet-suggestions">
                        {suggestions.map((item) => {
                            const stars = qualityStars(item.quality);
                            return (
                                <button
                                    key={item.id}
                                    type="button"
                                    className="sheet-suggestion-item"
                                    onClick={() => onAdd(item)}
                                >
                                    {item.iconUrl && (
                                        <img src={item.iconUrl} alt="" width={16} height={16} style={{ borderRadius: 3 }} />
                                    )}
                                    <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                        {item.name}
                                        {stars && <span className={`quality-stars q${item.quality}`}> {stars}</span>}
                                    </span>
                                    <span className="muted" style={{ fontSize: 11 }}>#{item.id}</span>
                                </button>
                            );
                        })}
                    </div>
                )}
            </div>

            {entries.length > 0 && (
                <div className="sheet-entries">
                    {entries.map((entry) => {
                        const stars = qualityStars(entry.quality);
                        return (
                        <div key={entry.id} className={`sheet-entry${entry.multiplier === 0 ? " dimmed" : ""}`}>
                            <span className="sheet-entry-name" title={`#${entry.id}`}>
                                {entry.iconUrl && (
                                    <img src={entry.iconUrl} alt="" width={16} height={16} style={{ borderRadius: 3, verticalAlign: "middle", marginRight: 4 }} />
                                )}
                                {entry.name}
                                {stars && <span className={`quality-stars q${entry.quality}`}> {stars}</span>}
                            </span>
                            <span className="sheet-entry-controls">
                                <span className="muted" style={{ fontSize: 11 }}>×</span>
                                <input
                                    type="number"
                                    className="sheet-multiplier-input"
                                    value={entry.multiplier}
                                    min={0}
                                    onChange={(e) => onMultiplier(entry.id, Math.max(0, Number(e.target.value) || 0))}
                                />
                                <button
                                    type="button"
                                    className="sheet-remove-btn"
                                    onClick={() => onRemove(entry.id)}
                                    aria-label={`Remove ${entry.name}`}
                                >
                                    ✕
                                </button>
                            </span>
                        </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
