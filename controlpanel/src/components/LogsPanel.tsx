import { useEffect, useMemo, useState } from "react";
import { getCurrentLogs, getLogFiles } from "../api";
import type { LogEntry, LogFileInfo, LogViewResponse } from "../types";

type LogsPanelProps = {
  onArchive: () => Promise<void>;
  onClear: () => Promise<void>;
  message?: string | null;
  busy?: boolean;
};

type LogLevel = "ALL" | "DEBUG" | "INFO" | "WARN" | "ERROR";

const LOG_LEVELS: Array<{ key: LogLevel; label: string }> = [
  { key: "ALL", label: "All levels" },
  { key: "DEBUG", label: "Debug" },
  { key: "INFO", label: "Info" },
  { key: "WARN", label: "Warn" },
  { key: "ERROR", label: "Error" }
];

const LINE_LIMITS = [200, 400, 800, 1500, 3000];

function formatBytes(size: number): string {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function levelBadgeClass(level: string | null): string {
  switch ((level ?? "").toUpperCase()) {
    case "ERROR": return "status-error";
    case "WARN": return "status-warning";
    case "INFO": return "status-success";
    default: return "status-inline";
  }
}

function entrySummary(entry: LogEntry): string {
  const parts = [entry.timestamp, entry.thread ? `[${entry.thread}]` : null, entry.logger, entry.message]
    .filter(Boolean);
  return parts.join(" • ");
}

export default function LogsPanel({ onArchive, onClear, message, busy }: LogsPanelProps) {
  const [collapsed, setCollapsed] = useState(false);
  const [logFiles, setLogFiles] = useState<LogFileInfo[]>([]);
  const [selectedFile, setSelectedFile] = useState("current");
  const [selectedLevel, setSelectedLevel] = useState<LogLevel>("ALL");
  const [search, setSearch] = useState("");
  const [lineLimit, setLineLimit] = useState(400);
  const [logView, setLogView] = useState<LogViewResponse | null>(null);
  const [loadingLog, setLoadingLog] = useState(false);
  const [loadingFiles, setLoadingFiles] = useState(false);
  const [logError, setLogError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<boolean>(false);

  const selectedFileInfo = useMemo(
    () => logFiles.find((file) => file.key === selectedFile) ?? null,
    [logFiles, selectedFile]
  );

  const loadFiles = async (preferredFile?: string) => {
    setLoadingFiles(true);
    try {
      const files = await getLogFiles();
      setLogFiles(files);
      const nextFile = preferredFile && files.some((file) => file.key === preferredFile)
        ? preferredFile
        : (files[0]?.key ?? "current");
      setSelectedFile(nextFile);
      return nextFile;
    } catch (err) {
      setLogError(err instanceof Error ? err.message : String(err));
      return preferredFile ?? selectedFile;
    } finally {
      setLoadingFiles(false);
    }
  };

  const loadLogView = async (fileKey?: string) => {
    setLogError(null);
    setLoadingLog(true);
    try {
      const view = await getCurrentLogs({
        file: fileKey ?? selectedFile,
        lines: lineLimit,
        level: selectedLevel === "ALL" ? undefined : selectedLevel,
        search: search.trim() || undefined,
      });
      setLogView(view);
    } catch (err) {
      setLogView(null);
      setLogError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoadingLog(false);
    }
  };

  useEffect(() => {
    void (async () => {
      const initialFile = await loadFiles("current");
      await loadLogView(initialFile);
    })();
  }, []);

  const handleArchive = async () => {
    const confirmed = window.confirm("Archive logs? This will create a snapshot of the current logs.");
    if (!confirmed) return;
    await onArchive();
    const nextFile = await loadFiles(selectedFile === "current" ? undefined : selectedFile);
    await loadLogView(nextFile);
  };

  const handleClear = async () => {
    const confirmed = window.confirm("Clear archived logs? This will permanently delete archived logs.");
    if (!confirmed) return;
    await onClear();
    const nextFile = await loadFiles(selectedFile === "current" ? "current" : undefined);
    await loadLogView(nextFile);
  };

  const handleFetchCurrent = async () => {
    await loadLogView();
  };

  const handleCopyVisible = async () => {
    if (!logView) return;
    await navigator.clipboard.writeText(logView.entries.map((entry) => entry.raw).join("\n"));
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
          disabled={busy || loadingLog || loadingFiles}
          aria-pressed={!!logView}
        >
          {loadingLog ? "Loading..." : "Refresh log view"}
        </button>

        <button
          className="button secondary"
          type="button"
          onClick={() => void handleCopyVisible()}
          disabled={!logView || logView.entries.length === 0}
        >
          Copy visible lines
        </button>
      </div>

      <div className="muted" style={{ marginTop: 8 }}>
        {message ?? "Browse current and archived logs, filter on the server, and inspect recent matching entries without pulling the whole file into the browser."}
      </div>

      <div className="grid" style={{ gridTemplateColumns: "1.1fr 0.9fr 0.8fr 0.8fr", marginTop: 12 }}>
        <label className="field" style={{ marginBottom: 0 }}>
          <span className="label">Log file</span>
          <select
            className="select"
            value={selectedFile}
            onChange={(e) => setSelectedFile(e.target.value)}
            disabled={loadingFiles}
          >
            {logFiles.map((file) => (
              <option key={file.key} value={file.key}>
                {file.current ? "Current log" : file.fileName}
              </option>
            ))}
          </select>
        </label>

        <label className="field" style={{ marginBottom: 0 }}>
          <span className="label">Level</span>
          <select className="select" value={selectedLevel} onChange={(e) => setSelectedLevel(e.target.value as LogLevel)}>
            {LOG_LEVELS.map((level) => (
              <option key={level.key} value={level.key}>{level.label}</option>
            ))}
          </select>
        </label>

        <label className="field" style={{ marginBottom: 0 }}>
          <span className="label">Visible lines</span>
          <select className="select" value={lineLimit} onChange={(e) => setLineLimit(Number(e.target.value))}>
            {LINE_LIMITS.map((count) => (
              <option key={count} value={count}>{count}</option>
            ))}
          </select>
        </label>

        <label className="field" style={{ marginBottom: 0 }}>
          <span className="label">Search</span>
          <input
            className="input"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="error, recipe id, logger..."
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                void handleFetchCurrent();
              }
            }}
          />
        </label>
      </div>

      <div className="row" style={{ marginTop: 10, justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap" }}>
        <div className="muted" style={{ fontSize: 12 }}>
          {selectedFileInfo && (
            <>
              <div>{selectedFileInfo.current ? "Current log file" : selectedFileInfo.fileName}</div>
              <div>Updated {new Date(selectedFileInfo.lastModified).toLocaleString()} • {formatBytes(selectedFileInfo.sizeBytes)}</div>
            </>
          )}
        </div>
        {logView && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "flex-end" }}>
            {Object.entries(logView.levelCounts).map(([level, count]) => (
              <span key={level} className={`status-inline ${levelBadgeClass(level)}`}>
                {level}: {count}
              </span>
            ))}
          </div>
        )}
      </div>

      {logError && (
        <div className="error" style={{ marginTop: 12 }}>
          Failed to load log: {logError}
        </div>
      )}

      {logView && (
        <div style={{ marginTop: 12 }}>
          <div
            style={{
              fontSize: 13,
              marginBottom: 6,
              color: "#475569",
              fontWeight: 600,
              display: "flex",
              alignItems: "center",
              gap: 10,
              flexWrap: "wrap"
            }}
          >
            <span>{logView.fileName}</span>
            <span className="muted" style={{ fontWeight: 500 }}>
              Showing {logView.returnedLines} of {logView.matchedLines} matching lines • {logView.totalLines} total
            </span>
            {logView.truncated && <span className="status-inline status-warning">Showing latest matches only</span>}
          </div>

          {/* viewer wrapper - contains expand control */}
          <div style={{ position: "relative", width: "100%", maxWidth: "100%", minWidth: 0 }}>
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
                width: "100%",
                maxWidth: "100%",
                minWidth: 0,
                boxSizing: "border-box",
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
              {logView.entries.length === 0 ? (
                <div className="muted">No log lines matched the current filters.</div>
              ) : (
                <div style={{ display: "grid", gap: 8 }}>
                  {logView.entries.map((entry) => (
                    <div key={`${entry.lineNumber}-${entry.raw}`} style={{ borderBottom: "1px solid #e2e8f0", paddingBottom: 8 }}>
                      <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
                        <div className="row" style={{ flexWrap: "wrap" }}>
                          {entry.level && (
                            <span className={`status-inline ${levelBadgeClass(entry.level)}`}>{entry.level}</span>
                          )}
                          <span className="muted" style={{ fontSize: 12 }}>#{entry.lineNumber}</span>
                          {entry.timestamp && <span className="muted" style={{ fontSize: 12 }}>{entry.timestamp}</span>}
                          {entry.thread && <span className="muted" style={{ fontSize: 12 }}>[{entry.thread}]</span>}
                        </div>
                        {entry.logger && <span className="muted" style={{ fontSize: 12 }}>{entry.logger}</span>}
                      </div>
                      <pre style={{ margin: "6px 0 0", whiteSpace: "pre-wrap", overflowWrap: "anywhere", wordBreak: "break-word", maxWidth: "100%" }}>
                        {entrySummary(entry)}
                      </pre>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
