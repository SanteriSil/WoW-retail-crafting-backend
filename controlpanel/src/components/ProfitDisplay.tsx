import type { ProfitEstimate } from "../types";
import { formatGold, profitClass } from "../utils";

// Discriminated union: compact needs only the essentials; full needs the complete estimate.
type Props =
    | { variant: "compact"; profit: number | null; calculable: boolean }
    | { variant: "full"; estimate: ProfitEstimate | null };

export default function ProfitDisplay(props: Props) {
    if (props.variant === "compact") {
        return (
            <span className={profitClass(props.calculable, props.profit)}>
                {props.calculable && props.profit != null ? formatGold(props.profit) : "—"}
            </span>
        );
    }

    // ── Full variant ──────────────────────────────────────────────────────────
    const { estimate } = props;
    if (!estimate) {
        return <div className="muted">No profit data available.</div>;
    }

    const ahPct = ((1 - estimate.auctionHouseFee) * 100).toFixed(0);

    return (
        <div>
            <div className="profit-breakdown">
                <div className="profit-breakdown-row subtotal">
                    <span>Revenue</span>
                    <span>
                        <span className="muted">
                            {estimate.calculable ? formatGold(estimate.outputRevenue, false) : "—"}
                        </span>
                        <span style={{ fontSize: 11, marginLeft: 8, color: "#94a3b8" }}>
                            after {ahPct}% AH fee
                        </span>
                    </span>
                </div>
                <div className="profit-breakdown-row subtotal">
                    <span>Ingredient Cost</span>
                    <span className="muted">
                        {estimate.ingredientCost > 0 ? formatGold(estimate.ingredientCost, false) : "0s"}
                    </span>
                </div>
                <div className={`profit-breakdown-row total ${profitClass(estimate.calculable, estimate.profit)}`}>
                    <span>Estimated Profit</span>
                    <span>{estimate.calculable ? formatGold(estimate.profit) : "Incomplete"}</span>
                </div>
            </div>

            {!estimate.calculable && (
                <div className="profit-missing-warning">
                    ⚠️ Incomplete estimate — {estimate.missingPrices.length} ingredient price
                    {estimate.missingPrices.length !== 1 ? "s" : ""} unavailable. Profit is calculated from
                    available prices only.
                </div>
            )}
        </div>
    );
}
