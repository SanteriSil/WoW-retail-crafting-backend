import { formatGold } from "../utils";

type Props = {
    totalBaseCost: number;
    totalBaseProfit: number;
    totalAdjustedProfit: number;
    totalCrafts: number;
};

export default function DashboardSummary({ totalBaseCost, totalBaseProfit, totalAdjustedProfit, totalCrafts }: Props) {
    return (
        <div className="dashboard-summary">
            <div className="dashboard-summary-card">
                <div className="dashboard-summary-label">Total Crafts</div>
                <div className="dashboard-summary-value">{totalCrafts}</div>
            </div>
            <div className="dashboard-summary-card">
                <div className="dashboard-summary-label">Base Cost</div>
                <div className="dashboard-summary-value">{formatGold(totalBaseCost, false)}</div>
            </div>
            <div className="dashboard-summary-card">
                <div className="dashboard-summary-label">Base Profit</div>
                <div className={`dashboard-summary-value ${totalBaseProfit >= 0 ? "profit-positive" : "profit-negative"}`}>
                    {formatGold(totalBaseProfit)}
                </div>
            </div>
            <div className="dashboard-summary-card">
                <div className="dashboard-summary-label">Adjusted Profit</div>
                <div className={`dashboard-summary-value ${totalAdjustedProfit >= 0 ? "profit-positive" : "profit-negative"}`}>
                    {formatGold(totalAdjustedProfit)}
                </div>
            </div>
        </div>
    );
}
