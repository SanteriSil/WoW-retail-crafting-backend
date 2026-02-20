import { useMemo, useState } from "react";
import type { Item } from "../types";

type SheetEntry = { id: number; name: string; multiplier: number };

type SheetBuilderProps = {
    items: Item[];
};

export default function SheetBuilder({ items }: SheetBuilderProps) {
    const [expanded, setExpanded] = useState(false);
    const [withRes, setWithRes] = useState<SheetEntry[]>([]);
    const [withoutRes, setWithoutRes] = useState<SheetEntry[]>([]);
    const [searchWith, setSearchWith] = useState("");
    const [searchWithout, setSearchWithout] = useState("");
    const [copied, setCopied] = useState(false);

    const filterItems = (query: string, exclude: SheetEntry[]) => {
        if (!query.trim()) return [];
        const lowered = query.toLowerCase();
        const excludeIds = new Set(exclude.map((e) => e.id));
        return items
            .filter((it) => !excludeIds.has(it.id) && (it.name.toLowerCase().includes(lowered) || String(it.id).includes(lowered)))
            .slice(0, 12);
    };

    const suggestionsWith = useMemo(() => filterItems(searchWith, withRes), [searchWith, items, withRes]);
    const suggestionsWithout = useMemo(() => filterItems(searchWithout, withoutRes), [searchWithout, items, withoutRes]);

    const addTo = (list: SheetEntry[], setList: React.Dispatch<React.SetStateAction<SheetEntry[]>>, item: Item) => {
        if (list.some((e) => e.id === item.id)) return;
        setList([...list, { id: item.id, name: item.name, multiplier: 1 }]);
    };

    const removeFrom = (setList: React.Dispatch<React.SetStateAction<SheetEntry[]>>, id: number) => {
        setList((prev) => prev.filter((e) => e.id !== id));
    };

    const setMultiplier = (setList: React.Dispatch<React.SetStateAction<SheetEntry[]>>, id: number, value: number) => {
        setList((prev) => prev.map((e) => (e.id === id ? { ...e, multiplier: value } : e)));
    };

    const outputJson = useMemo(() => {
        const payload = {
            withResourcefulness: withRes.map((e) => ({ id: e.id, multiplier: e.multiplier })),
            withoutResourcefulness: withoutRes.map((e) => ({ id: e.id, multiplier: e.multiplier }))
        };
        return JSON.stringify(payload, null, 2);
    }, [withRes, withoutRes]);

    const hasContent = withRes.length > 0 || withoutRes.length > 0;

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

                    {/* With Resourcefulness */}
                    <Section
                        label="With Resourcefulness"
                        entries={withRes}
                        search={searchWith}
                        onSearchChange={setSearchWith}
                        suggestions={suggestionsWith}
                        onAdd={(item) => { addTo(withRes, setWithRes, item); setSearchWith(""); }}
                        onRemove={(id) => removeFrom(setWithRes, id)}
                        onMultiplier={(id, v) => setMultiplier(setWithRes, id, v)}
                    />

                    {/* Without Resourcefulness */}
                    <Section
                        label="Without Resourcefulness"
                        entries={withoutRes}
                        search={searchWithout}
                        onSearchChange={setSearchWithout}
                        suggestions={suggestionsWithout}
                        onAdd={(item) => { addTo(withoutRes, setWithoutRes, item); setSearchWithout(""); }}
                        onRemove={(id) => removeFrom(setWithoutRes, id)}
                        onMultiplier={(id, v) => setMultiplier(setWithoutRes, id, v)}
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
                        {suggestions.map((item) => (
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
                                </span>
                                <span className="muted" style={{ fontSize: 11 }}>#{item.id}</span>
                            </button>
                        ))}
                    </div>
                )}
            </div>

            {entries.length > 0 && (
                <div className="sheet-entries">
                    {entries.map((entry) => (
                        <div key={entry.id} className="sheet-entry">
                            <span className="sheet-entry-name" title={`#${entry.id}`}>{entry.name}</span>
                            <span className="sheet-entry-controls">
                                <span className="muted" style={{ fontSize: 11 }}>×</span>
                                <input
                                    type="number"
                                    className="sheet-multiplier-input"
                                    value={entry.multiplier}
                                    min={1}
                                    onChange={(e) => onMultiplier(entry.id, Math.max(1, Number(e.target.value) || 1))}
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
                    ))}
                </div>
            )}
        </div>
    );
}
