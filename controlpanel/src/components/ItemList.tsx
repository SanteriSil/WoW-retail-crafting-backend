import type { Item } from "../types";

type ItemListProps = {
    items: Item[];
    onSelect: (item: Item) => void;
};

export default function ItemList({ items, onSelect }: ItemListProps) {
    if (items.length === 0) {
        return <div className="muted">No items found.</div>;
    }

    return (
        <div className="list">
            {items.map((item) => (
                <button
                    key={item.id}
                    type="button"
                    className="list-item"
                    onClick={() => onSelect(item)}
                >
                    <span style={{ display: "inline-flex", alignItems: "center", gap: 8, minWidth: 0 }}>
                        {item.iconUrl ? (
                            <img
                                src={item.iconUrl}
                                alt={item.name}
                                width={20}
                                height={20}
                                style={{ borderRadius: 4, objectFit: "cover", flexShrink: 0 }}
                                loading="lazy"
                            />
                        ) : (
                            <span
                                aria-hidden="true"
                                style={{
                                    width: 20,
                                    height: 20,
                                    borderRadius: 4,
                                    border: "1px solid #cbd5e1",
                                    background: "#f8fafc",
                                    display: "inline-block",
                                    flexShrink: 0
                                }}
                            />
                        )}
                        <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{item.name}</span>
                    </span>
                    <span className="muted">#{item.id}</span>
                </button>
            ))}
        </div>
    );
}
