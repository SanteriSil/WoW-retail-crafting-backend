type LogsPanelProps = {
  onArchive: () => Promise<void>;
  onClear: () => Promise<void>;
  message?: string | null;
  busy?: boolean;
};

export default function LogsPanel({ onArchive, onClear, message, busy }: LogsPanelProps) {
  return (
    <div className="card">
      <h3>Logs</h3>
      <div className="row">
        <button className="button secondary" type="button" onClick={onArchive} disabled={busy}>
          {busy ? "Working..." : "Archive logs"}
        </button>
        <button className="button secondary" type="button" onClick={onClear} disabled={busy}>
          {busy ? "Working..." : "Clear archives"}
        </button>
      </div>
      <div className="muted">{message ?? ""}</div>
    </div>
  );
}
