import type { Item } from "../types";

type ItemListProps = {
    items: Item[];
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

export default function ItemList({ items }: ItemListProps) {
    if (items.length === 0) {
        return <div className="muted">No items found.</div>;
    }

    return (
        <div className="list">
            {items.map((item) => {
                const updated = formatUpdatedAt(item.currentPriceRecordedAt);
                const stars = qualityStars(item.quality);

                return (
                    <button
                        key={item.id}
                        type="button"
                        className="list-item"
                        onClick={() => window.open(`https://www.wowhead.com/item=${item.id}`, "_blank", "noopener,noreferrer")}
                        title="Open on Wowhead"
                    >
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 8, minWidth: 0, flex: 1 }}>
                            <span
                                style={{
                                    display: "inline-flex",
                                    flexDirection: "column",
                                    alignItems: "center",
                                    gap: 2,
                                    flexShrink: 0,
                                    minWidth: 48
                                }}
                            >
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
                                <span className="muted" style={{ fontSize: 11, lineHeight: 1.1 }}>
                                    {(() => {
                                        const price = formatPriceFromCopper(item.currentPrice);
                                        if (!price) {
                                            return "-";
                                        }

                                        return (
                                            <>
                                                <span className="price-piece" style={{ display: "inline-flex", alignItems: "center" }}>
                                                    <span className="price-value">{price.gold}</span>
                                                    <span className="coin gold" aria-hidden="true">🪙</span>
                                                </span>
                                                <span className="price-piece" style={{ display: "inline-flex", alignItems: "center", marginLeft: 8 }}>
                                                    <span className="price-value">{price.silver}</span>
                                                    <span className="coin silver" aria-hidden="true">🪙</span>
                                                </span>
                                            </>
                                        );
                                    })()}
                                </span>
                            </span>
                            <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                {item.name}
                                {stars && <span className={`quality-stars q${item.quality}`} aria-label={`Quality ${item.quality}`}> {stars}</span>}
                            </span>
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
                                title={updated.title}
                                style={{ fontSize: 11 }}
                            >
                                {updated.label}
                            </span>
                        </span>
                    </button>
                );
            })}
        </div>
    );
}
