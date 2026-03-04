import { useCallback, useEffect, useState } from "react";
import { approveAccessRequest, denyAccessRequest, getAccessRequests } from "../api";
import type { AccessRequest } from "../types";

export default function AccessRequestPanel() {
    const [requests, setRequests] = useState<AccessRequest[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [busyId, setBusyId] = useState<number | null>(null);

    const refresh = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            setRequests(await getAccessRequests());
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to load access requests.");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { refresh(); }, [refresh]);

    const handleApprove = async (id: number) => {
        setBusyId(id);
        setError(null);
        try {
            await approveAccessRequest(id);
            await refresh();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to approve request.");
        } finally {
            setBusyId(null);
        }
    };

    const handleDeny = async (id: number) => {
        setBusyId(id);
        setError(null);
        try {
            await denyAccessRequest(id);
            await refresh();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to deny request.");
        } finally {
            setBusyId(null);
        }
    };

    return (
        <div className="access-requests">
            <h3>Access Requests</h3>

            {error && <div className="um-error">{error}</div>}

            {loading ? (
                <div className="muted">Loading…</div>
            ) : requests.length === 0 ? (
                <div className="muted">No pending requests.</div>
            ) : (
                <ul className="ar-list">
                    {requests.map((r) => (
                        <li key={r.id} className="ar-row">
                            <div className="ar-info">
                                <span className="ar-name">{r.discordUsername}</span>
                                <span className="ar-id muted">{r.discordId}</span>
                                <span className="ar-date muted">
                                    {new Date(r.createdAt).toLocaleDateString()}
                                </span>
                            </div>
                            <div className="ar-actions">
                                <button
                                    type="button"
                                    className="button primary ar-btn"
                                    disabled={busyId === r.id}
                                    onClick={() => handleApprove(r.id)}
                                    title="Approve request"
                                >
                                    ✅ Approve
                                </button>
                                <button
                                    type="button"
                                    className="button secondary ar-btn ar-deny"
                                    disabled={busyId === r.id}
                                    onClick={() => handleDeny(r.id)}
                                    title="Deny request"
                                >
                                    ❌ Deny
                                </button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
