import { useEffect, useMemo, useRef, useState } from "react";
import type { Region, RealmEntry } from "../data/realms";
import { REALMS } from "../data/realms";

type Props = {
    value: string;
    onChange: (value: string) => void;
    region: Region;
    onRegionChange: (region: Region) => void;
};

export default function RealmAutocomplete({ value, onChange, region, onRegionChange }: Props) {
    const [open, setOpen] = useState(false);
    const [highlightIdx, setHighlightIdx] = useState(-1);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const listRef = useRef<HTMLUListElement>(null);

    const filtered = useMemo(() => {
        const q = value.toLowerCase();
        return REALMS.filter(
            (r: RealmEntry) => r.region === region && (!q || r.name.toLowerCase().includes(q))
        );
    }, [value, region]);

    // Close on outside click
    useEffect(() => {
        const handler = (e: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, []);

    // Scroll highlighted item into view
    useEffect(() => {
        if (highlightIdx >= 0 && listRef.current) {
            const el = listRef.current.children[highlightIdx] as HTMLElement | undefined;
            el?.scrollIntoView({ block: "nearest" });
        }
    }, [highlightIdx]);

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (!open && (e.key === "ArrowDown" || e.key === "ArrowUp")) {
            setOpen(true);
            setHighlightIdx(0);
            e.preventDefault();
            return;
        }
        if (!open) return;

        switch (e.key) {
            case "ArrowDown":
                e.preventDefault();
                setHighlightIdx((prev) => Math.min(prev + 1, filtered.length - 1));
                break;
            case "ArrowUp":
                e.preventDefault();
                setHighlightIdx((prev) => Math.max(prev - 1, 0));
                break;
            case "Enter":
                e.preventDefault();
                if (highlightIdx >= 0 && highlightIdx < filtered.length) {
                    onChange(filtered[highlightIdx].name);
                    setOpen(false);
                }
                break;
            case "Escape":
                setOpen(false);
                break;
        }
    };

    const select = (realm: RealmEntry) => {
        onChange(realm.name);
        setOpen(false);
    };

    return (
        <div className="realm-autocomplete" ref={wrapperRef}>
            {/* Region toggle */}
            <div className="realm-region-toggle">
                <span className="label" style={{ fontSize: 12, marginRight: 6 }}>Region:</span>
                {(["EU", "NA"] as Region[]).map((r) => (
                    <button
                        key={r}
                        type="button"
                        className={`realm-region-btn${region === r ? " active" : ""}`}
                        onClick={() => onRegionChange(r)}
                    >
                        {r}
                    </button>
                ))}
            </div>

            {/* Input */}
            <input
                className="input"
                value={value}
                onChange={(e) => {
                    onChange(e.target.value);
                    setOpen(true);
                    setHighlightIdx(0);
                }}
                onFocus={() => { setOpen(true); setHighlightIdx(-1); }}
                onKeyDown={handleKeyDown}
                placeholder="Type to search realms…"
                autoComplete="off"
            />

            {/* Dropdown */}
            {open && filtered.length > 0 && (
                <ul className="realm-dropdown" ref={listRef}>
                    {filtered.slice(0, 50).map((realm, idx) => (
                        <li
                            key={realm.slug}
                            className={`realm-dropdown-item${idx === highlightIdx ? " highlighted" : ""}`}
                            onMouseDown={() => select(realm)}
                            onMouseEnter={() => setHighlightIdx(idx)}
                        >
                            {realm.name}
                        </li>
                    ))}
                    {filtered.length > 50 && (
                        <li className="realm-dropdown-item muted" style={{ fontStyle: "italic", cursor: "default" }}>
                            …{filtered.length - 50} more — type to narrow
                        </li>
                    )}
                </ul>
            )}
        </div>
    );
}
