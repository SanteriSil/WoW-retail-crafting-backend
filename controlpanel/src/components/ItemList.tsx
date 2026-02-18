import type { Item } from "../types";

type ItemListProps = {
    items: Item[];
};

function formatUpdatedAt(value?: string | null): { label: string; title?: string } {
    if (!value) {
        return { label: "Updated: never" };
    }

    const dt = new Date(value);
    if (Number.isNaN(dt.getTime())) {
        return { label: "Updated: unknown" };
    }

    const diffMs = Date.now() - dt.getTime();
    const minutes = Math.floor(diffMs / 60000);

    let relative: string;
    if (minutes < 1) relative = "just now";
    else if (minutes < 60) relative = `${minutes}m ago`;
    else {
        const hours = Math.floor(minutes / 60);
        if (hours < 24) relative = `${hours}h ago`;
        else {
            const days = Math.floor(hours / 24);
            relative = `${days}d ago`;
        }
    }

    return {
        label: `Updated: ${relative}`,
        title: dt.toLocaleString()
    };
}

export default function ItemList({ items }: ItemListProps) {
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
                    onClick={() => window.open(`https://www.wowhead.com/item=${item.id}`, "_blank", "noopener,noreferrer")}
                    title="Open on Wowhead"
                >
                    <span style={{ display: "inline-flex", alignItems: "center", gap: 8, minWidth: 0, flex: 1 }}>
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
                    <span
                        style={{
                            display: "inline-flex",
                            flexDirection: "column",
                            alignItems: "flex-end",
                            gap: 2,
                            marginLeft: 8,
                            flexShrink: 0
                        }}
                    >
                        <span className="muted">#{item.id}</span>
                        <span
                            className="muted"
                            title={formatUpdatedAt(item.currentPriceRecordedAt).title}
                            style={{ fontSize: 11 }}
                        >
                            {formatUpdatedAt(item.currentPriceRecordedAt).label}
                        </span>
                    </span>
                </button>
            ))}
        </div>
    );
}
