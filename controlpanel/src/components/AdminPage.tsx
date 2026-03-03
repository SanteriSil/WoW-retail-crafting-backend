import type { Item, Profession } from "../types";
import UserManagement from "./UserManagement";
import AuctionSubmitPanel from "./AuctionSubmitPanel";
import LogsPanel from "./LogsPanel";

interface AdminPageProps {
    items: Item[];
    professions: Profession[];
    onAhRefresh: () => void;
    ahBusy: boolean;
    ahMessage: string | null;
    ahError: string | null;
    onArchiveLogs: () => Promise<void>;
    onClearLogs: () => Promise<void>;
    logsMessage: string | null;
    logsBusy: boolean;
}

export default function AdminPage({
    items,
    professions,
    onAhRefresh,
    ahBusy,
    ahMessage,
    ahError,
    onArchiveLogs,
    onClearLogs,
    logsMessage,
    logsBusy,
}: AdminPageProps) {
    return (
        <div style={{ marginTop: 16 }}>
            <h2 style={{ marginBottom: 12 }}>Admin Panel</h2>
            <div className="grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
                {/* ── User Management ── */}
                <div className="card">
                    <UserManagement />
                </div>

                {/* ── AH Refresh ── */}
                <div className="card">
                    <h3>AH Refresh</h3>
                    <p className="muted" style={{ marginBottom: 8 }}>
                        Trigger a full auction house data refresh from the Blizzard API.
                    </p>
                    <button className="button" type="button" onClick={onAhRefresh} disabled={ahBusy}>
                        {ahBusy ? "Refreshing…" : "Refresh Auction Data"}
                    </button>
                    {(ahMessage || ahError) && (
                        <div
                            className={`status-inline ${ahError ? "status-error" : "status-success"}`}
                            style={{ marginTop: 8 }}
                        >
                            {ahError ?? ahMessage}
                        </div>
                    )}
                </div>

                {/* ── Auction Submit ── */}
                <AuctionSubmitPanel items={items} professions={professions} />

                {/* ── Logs ── */}
                <LogsPanel
                    onArchive={onArchiveLogs}
                    onClear={onClearLogs}
                    message={logsMessage}
                    busy={logsBusy}
                />
            </div>
        </div>
    );
}
