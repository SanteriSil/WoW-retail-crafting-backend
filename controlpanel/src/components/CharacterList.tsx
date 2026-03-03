import type { Character } from "../types";

type Props = {
    characters: Character[];
    selected: Character | null;
    onSelect: (c: Character) => void;
    onDelete: (id: number) => void;
    onRefreshIcon: (id: number) => void;
};

export default function CharacterList({ characters, selected, onSelect, onDelete, onRefreshIcon }: Props) {
    if (characters.length === 0) {
        return <div className="muted" style={{ padding: 12 }}>No characters yet. Create one →</div>;
    }

    return (
        <div className="character-list">
            {characters.map((c) => (
                <button
                    key={c.id}
                    type="button"
                    className={`character-card${selected?.id === c.id ? " selected" : ""}`}
                    onClick={() => onSelect(c)}
                >
                    <div className="character-card-left">
                        {c.iconUrl ? (
                            <img src={c.iconUrl} alt="" className="character-icon" />
                        ) : (
                            <div className="character-icon-placeholder">👤</div>
                        )}
                        <div>
                            <div className="character-name">{c.name}</div>
                            <div className="muted" style={{ fontSize: 12 }}>{c.realm}</div>
                            <div className="character-badges">
                                {c.professions.map((p) => (
                                    <span key={p.id} className="profession-chip active" style={{ fontSize: 10, padding: "1px 6px" }}>
                                        {p.professionName}
                                    </span>
                                ))}
                            </div>
                        </div>
                    </div>
                    <div className="character-card-right">
                        <span className="muted" style={{ fontSize: 11 }}>
                            {c.assignedRecipeCount} recipe{c.assignedRecipeCount !== 1 ? "s" : ""}
                        </span>
                        <div style={{ display: "flex", gap: 4 }}>
                            <button
                                type="button"
                                className="button small secondary"
                                onClick={(e) => { e.stopPropagation(); onRefreshIcon(c.id); }}
                                title="Refresh icon"
                            >
                                🔄
                            </button>
                            <button
                                type="button"
                                className="button small danger"
                                onClick={(e) => { e.stopPropagation(); onDelete(c.id); }}
                                title="Delete character"
                            >
                                🗑
                            </button>
                        </div>
                    </div>
                </button>
            ))}
        </div>
    );
}
