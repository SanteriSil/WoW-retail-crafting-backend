type LogsPanelProps = {
  onArchive: () => Promise<void>;
  onClear: () => Promise<void>;
  message?: string | null;
  busy?: boolean;
};

export default function LogsPanel({ onArchive, onClear, message, busy }: LogsPanelProps) {
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

  return (
    <div className="card">
      <h3>Logs</h3>
      <div className="row">
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
