import { useState } from "react";

type LogsPanelProps = {
  onArchive: () => Promise<void>;
  onClear: () => Promise<void>;
  message?: string | null;
  busy?: boolean;
};

export default function LogsPanel({ onArchive, onClear, message, busy }: LogsPanelProps) {
  const [collapsed, setCollapsed] = useState(false);

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
      </div>
      <div className="muted">{message ?? "Actions map to /logs/archive and /logs/clear."}</div>
    </div>
  );
}
