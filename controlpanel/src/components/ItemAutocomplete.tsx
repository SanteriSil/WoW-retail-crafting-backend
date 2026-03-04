import { useEffect, useMemo, useRef, useState } from "react";
import type { Item } from "../types";
import { qualityStars } from "./ItemList";

export type ItemAutocompleteProps = {
    value: string;
    onChange: (newId: string) => void;
    itemMap: Map<number, Item>;
    placeholder?: string;
    inputClassName?: string;
    inputStyle?: React.CSSProperties;
};

export default function ItemAutocomplete({
    value,
    onChange,
    itemMap,
    placeholder = "Item name…",
    inputClassName,
    inputStyle,
}: ItemAutocompleteProps) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");
    const [highlightIdx, setHighlightIdx] = useState(-1);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const listRef = useRef<HTMLUListElement>(null);

    const items = useMemo(() => Array.from(itemMap.values()), [itemMap]);
    const filtered = useMemo(() => {
        const q = query.toLowerCase();
        return items.filter((item) => !q || item.name.toLowerCase().includes(q));
    }, [items, query]);

    // when external value (id) changes, update query to show name
    useEffect(() => {
        if (value) {
            const id = parseInt(value, 10);
            const item = itemMap.get(id);
            if (item) {
                setQuery(item.name);
                return;
            }
        }
        setQuery("");
    }, [value, itemMap]);

    // close on outside click
    useEffect(() => {
        const handler = (e: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, []);

    // scroll highlighted into view
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
                    selectItem(filtered[highlightIdx]);
                }
                break;
            case "Escape":
                setOpen(false);
                break;
        }
    };

    const selectItem = (item: Item) => {
        onChange(item.id.toString());
        setOpen(false);
    };

    return (
        <div className="item-autocomplete" ref={wrapperRef} style={{ position: "relative" }}>
            <input
                className={inputClassName || "input"}
                style={inputStyle}
                value={query}
                placeholder={placeholder}
                onChange={(e) => {
                    setQuery(e.target.value);
                    setOpen(true);
                    setHighlightIdx(0);
                }}
                onFocus={() => {
                    setOpen(true);
                    setHighlightIdx(-1);
                }}
                onBlur={() => {
                    // if the field has been emptied, clear parent value
                    if (!query.trim()) {
                        onChange("");
                        return;
                    }
                    // if user typed an ID directly, commit it
                    const id = parseInt(query, 10);
                    if (id && itemMap.has(id)) {
                        onChange(id.toString());
                    }
                }}
                onKeyDown={handleKeyDown}
                autoComplete="off"
            />

            {open && filtered.length > 0 && query.trim().length > 0 && (
                <ul className="item-dropdown" ref={listRef}>
                    {filtered.slice(0, 50).map((item, idx) => {
                        const stars = qualityStars(item.quality);
                        return (
                            <li
                                key={item.id}
                                className={`item-dropdown-item${idx === highlightIdx ? " highlighted" : ""}`}
                                onMouseDown={() => selectItem(item)}
                                onMouseEnter={() => setHighlightIdx(idx)}
                            >
                                <span>
                                    {item.name}
                                    {stars && (
                                        <span className={`quality-stars q${item.quality}`}> {stars}</span>
                                    )}
                                </span>
                                <span className="muted" style={{ marginLeft: 8 }}>#{item.id}</span>
                            </li>
                        );
                    })}
                    {filtered.length > 50 && (
                        <li className="item-dropdown-item muted" style={{ fontStyle: "italic", cursor: "default" }}>
                            …{filtered.length - 50} more — type to narrow
                        </li>
                    )}
                </ul>
            )}
        </div>
    );
}
