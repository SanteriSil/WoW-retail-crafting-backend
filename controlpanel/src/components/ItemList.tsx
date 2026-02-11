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
                    <span>{item.name}</span>
                    <span className="muted">#{item.id}</span>
                </button>
            ))}
        </div>
    );
}
