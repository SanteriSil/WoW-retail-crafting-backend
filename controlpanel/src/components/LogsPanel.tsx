import { useState } from "react";
import { getCurrentLogs } from "../api";

type LogsPanelProps = {
  onArchive: () => Promise<void>;
  onClear: () => Promise<void>;
  message?: string | null;
  busy?: boolean;
};

export default function LogsPanel({ onArchive, onClear, message, busy }: LogsPanelProps) {
  const [collapsed, setCollapsed] = useState(false);
  const [logText, setLogText] = useState<string | null>(null);
  const [loadingLog, setLoadingLog] = useState(false);
  const [logError, setLogError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<boolean>(false);

  const handleArchive = async () => {
    const confirmed = window.confirm("Archive logs? This will create a snapshot of the current logs.");
    if (!confirmed) return;
    await onArchive();
  };

  const handleClear = async () => {
    const confirmed = window.confirm("Clear archived logs? This will permanently delete archived logs.");
    if (!confirmed) return;
    await onClear();
  };

  const handleFetchCurrent = async () => {
    setLogError(null);
    setLogText(null);
    setLoadingLog(true);
    try {
      const txt = await getCurrentLogs();
      setLogText(txt ?? "");
    } catch (err) {
      setLogError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoadingLog(false);
    }
  };

  if (collapsed) {
    return (
      <div className="card minimized" role="region" aria-label="Logs">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <strong>Logs</strong>
          <button className="button small" type="button" onClick={() => setCollapsed(false)} aria-label="Expand logs">
            +
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="card" role="region" aria-label="Logs">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
        <h3 style={{ margin: 0 }}>Logs</h3>
        <button className="button secondary small" type="button" onClick={() => setCollapsed(true)} aria-label="Minimize logs">
          -
        </button>
      </div>

      <div className="row" style={{ marginTop: 12 }}>
        <button className="button secondary" type="button" onClick={handleArchive} disabled={busy}>
          {busy ? "Working..." : "Archive logs"}
        </button>

        <button className="button secondary" type="button" onClick={handleClear} disabled={busy}>
          {busy ? "Working..." : "Clear archives"}
        </button>

        <button
          className="button secondary"
          type="button"
          onClick={handleFetchCurrent}
          disabled={busy || loadingLog}
          aria-pressed={!!logText}
        >
          {loadingLog ? "Loading..." : "Fetch current log"}
        </button>
      </div>

      <div className="muted" style={{ marginTop: 8 }}>
        {message ?? "Actions map to /logs/archive and /logs/clear. Use 'Fetch current log' to read /logs/current."}
      </div>

      {logError && (
        <div className="error" style={{ marginTop: 12 }}>
          Failed to load log: {logError}
        </div>
      )}

      {logText !== null && (
        <div style={{ marginTop: 12 }}>
          <div style={{ fontSize: 13, marginBottom: 6, color: "#475569", fontWeight: 600 }}>Current log</div>

          {/* viewer wrapper - contains expand control */}
          <div style={{ position: "relative" }}>
            <div style={{ position: "absolute", top: 8, right: 8, zIndex: 3 }}>
              <button
                type="button"
                className="button small"
                onClick={() => setExpanded(e => !e)}
                aria-pressed={expanded}
                title={expanded ? "Shrink log viewer" : "Expand log viewer"}
                onMouseDown={e => e.preventDefault()}
              >
                {expanded ? "⤫" : "⤢"}
              </button>
            </div>

            <div
              className="list"
              role="region"
              aria-label="Current log output"
              style={{
                whiteSpace: "pre-wrap",
                fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, monospace",
                maxHeight: expanded ? "80vh" : 260,
                overflow: "auto",
                padding: 12,
                transition: "max-height 180ms ease",
                boxShadow: expanded ? "0 12px 30px rgba(2,6,23,0.15)" : undefined,
                zIndex: expanded ? 2 : 1
              }}
            >
              <pre style={{ margin: 0, whiteSpace: "pre-wrap" }}>{logText || "(empty)"}</pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
