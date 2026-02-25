import type { Item } from "../types";

type ItemListProps = {
    items: Item[];
    selectedItem?: Item | null;
    onSelect?: (item: Item) => void;
};

function formatPriceFromCopper(value?: number | null): { gold: number; silver: number } | null {
    if (value == null || value < 0) {
        return null;
    }

    const totalSilver = Math.floor(value / 100);
    const gold = Math.floor(totalSilver / 100);
    const silver = totalSilver % 100;

    return { gold, silver };
}

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

function qualityStars(quality?: number | null): string | null {
    if (quality == null) return null;
    if (quality === 1) return "★";
    if (quality === 2) return "★★";
    return "?";
}

export default function ItemList({ items, selectedItem, onSelect }: ItemListProps) {
    if (items.length === 0) {
        return <div className="muted">No items found.</div>;
    }

    return (
        <div className="list">
            {items.map((item) => {
                const updated = formatUpdatedAt(item.currentPriceRecordedAt);
                const stars = qualityStars(item.quality);

                return (
                    <div
                        key={item.id}
                        className={`list-item${selectedItem?.id === item.id ? " selected" : ""}`}
                        onClick={() => onSelect?.(item)}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") onSelect?.(item); }}
                    >
                                <div className="list-item-left">
                                    <div className="list-item-icon">
                                        {item.iconUrl ? (
                                            <img src={item.iconUrl} alt={item.name} width={28} height={28} style={{ borderRadius: 4, objectFit: "cover" }} loading="lazy" />
                                        ) : (
                                            <span aria-hidden="true" style={{ width: 28, height: 28, borderRadius: 4, border: "1px solid #cbd5e1", background: "#f8fafc", display: "inline-block" }} />
                                        )}
                                    </div>
                                    <div className="item-cost muted">
                                        {(() => {
                                            const price = formatPriceFromCopper(item.currentPrice);
                                            if (!price) return "-";
                                            return (
                                                <>
                                                    <span className="price-piece"><span className="price-value">{price.gold}</span><span className="coin gold">🪙</span></span>
                                                    <span className="price-piece" style={{ marginLeft: 8 }}><span className="price-value">{price.silver}</span><span className="coin silver">🪙</span></span>
                                                </>
                                            );
                                        })()}
                                    </div>
                                    <div className="list-item-main">
                                        <div className="item-name">
                                            <span className="item-name-text">{item.name}</span>
                                            {stars && <span className={`quality-stars q${item.quality}`} aria-label={`Quality ${item.quality}`}> {stars}</span>}
                                        </div>
                                    </div>
                                </div>
                                <div className="list-item-right">
                                    <div className="muted">#{item.id}</div>
                                    {item.quantity != null && (
                                        <div className="muted" style={{ fontSize: 11 }}>Qty: {item.quantity}</div>
                                    )}
                                    <div className="muted" title={updated.title} style={{ fontSize: 11 }}>{updated.label}</div>
                                    <a
                                        className="wowhead-link"
                                        href={`https://www.wowhead.com/item=${item.id}`}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        onClick={(e) => e.stopPropagation()}
                                        title="Open on Wowhead"
                                    >
                                        🔗 Link
                                    </a>
                                </div>
                    </div>
                );
            })}
        </div>
    );
}
