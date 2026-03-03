import { useEffect, useRef, useState } from "react";
import type { DashboardCraft } from "../types";

type Props = {
    craft: DashboardCraft;
    currentM: number;
    currentR: number;
    onApply: (m: number, r: number) => void;
    onReset: () => void;
    onClose: () => void;
};

export default function CraftOverridePopover({ craft, currentM, currentR, onApply, onReset, onClose }: Props) {
    const [m, setM] = useState(currentM);
    const [r, setR] = useState(currentR);
    const ref = useRef<HTMLDivElement>(null);

    // Close on outside click
    useEffect(() => {
        const handler = (e: MouseEvent) => {
            if (ref.current && !ref.current.contains(e.target as Node)) {
                onClose();
            }
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, [onClose]);

    // Close on Escape
    useEffect(() => {
        const handler = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
        window.addEventListener("keydown", handler);
        return () => window.removeEventListener("keydown", handler);
    }, [onClose]);

    return (
        <div className="craft-override-popover" ref={ref}>
            <div className="craft-override-header">
                <strong>{craft.recipeName}</strong>
                <span className="muted" style={{ fontSize: 12 }}>{craft.characterName}</span>
            </div>
            <div className="craft-override-fields">
                <label className="craft-override-field">
                    <span>Multicraft Multiplier (M):</span>
                    <input
                        type="number"
                        className="input"
                        style={{ width: 80 }}
                        value={m}
                        min={0}
                        step={0.1}
                        onChange={(e) => setM(parseFloat(e.target.value) || 0)}
                        disabled={!craft.isMulticraftable}
                    />
                </label>
                <label className="craft-override-field">
                    <span>Resourcefulness Factor (R):</span>
                    <input
                        type="number"
                        className="input"
                        style={{ width: 80 }}
                        value={r}
                        min={0}
                        max={1}
                        step={0.05}
                        onChange={(e) => setR(Math.min(1, Math.max(0, parseFloat(e.target.value) || 0)))}
                    />
                </label>
            </div>
            <div className="craft-override-actions">
                <button
                    type="button"
                    className="button small secondary"
                    onClick={onReset}
                >
                    Reset to Defaults
                </button>
                <button
                    type="button"
                    className="button small primary"
                    onClick={() => onApply(m, r)}
                >
                    Apply
                </button>
            </div>
        </div>
    );
}
