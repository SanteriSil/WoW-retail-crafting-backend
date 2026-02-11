type LogsPanelProps = {
  onArchive: () => Promise<void>;
  onClear: () => Promise<void>;
};

export default function LogsPanel({ onArchive, onClear }: LogsPanelProps) {
  return (
    <div className="card">
      <h3>Logs</h3>
      <div className="row">
        <button className="button secondary" type="button" onClick={onArchive}>
          Archive logs
        </button>
        <button className="button secondary" type="button" onClick={onClear}>
          Clear archives
        </button>
      </div>
      <div className="muted">Actions map to /logs/archive and /logs/clear.</div>
    </div>
  );
}
